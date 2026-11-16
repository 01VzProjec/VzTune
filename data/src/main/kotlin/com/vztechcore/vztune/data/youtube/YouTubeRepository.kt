package com.vztechcore.vztune.data.youtube

import android.net.Uri
import android.util.Log
import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The metadata a YouTube search match returns.
 * Used by YTMusicEnricher as a last-resort artist / duration / thumb resolver.
 */
data class VideoMetadata(
    val videoId: String,
    val channelName: String,
    val title: String,
    val durationSeconds: Long,
    val thumbnailUrl: String?,
)

/** Canal real do YouTube que postou um vídeo (via NewPipe, não via busca por nome). */
data class YouTubeChannelInfo(
    val channelId: String,
    val channelName: String,
    val avatarUrl: String?,
    val channelUrl: String?,
)

/** Info técnica do stream de áudio resolvido pelo NewPipe (usada na tela TrackInfo). */
data class AudioStreamInfo(
    val itag: String?,
    val mimeType: String?,
    val quality: String?,
)

@Singleton
class YouTubeRepository @Inject constructor(
    private val downloader: NewPipeDownloader,
) {
    init {
        NewPipeInitializer.ensureInitialized(downloader)
    }

    /**
     * FIX 6: uma sessão de busca por (query + aba), não uma única sessão global.
     * Antes, ALL e VIDEOS compartilhavam a mesma sessão: quando o prefetch de abas
     * carregava VIDEOS depois de ALL, o "carregar mais" de ALL passava a paginar
     * a busca de VIDEOS. Com uma sessão por searchId, cada aba pagina a sua própria
     * busca, mesmo com o prefetch rodando em segundo plano.
     * searchId vem do MusicRepository ("$query|ALL", "$query|VIDEOS", ...).
     * buildVideoQueue() usa searchId = query (padrão), então não interfere nas abas.
     */
    private data class VideoSearchSession(
        val extractor: SearchExtractor,
        var nextPage: Page?,
    )

    private data class PlaylistSearchSession(
        val extractor: SearchExtractor,
        var nextPage: Page?,
    )

    private val videoSearches = mutableMapOf<String, VideoSearchSession>()
    private val playlistSearches = mutableMapOf<String, PlaylistSearchSession>()

    fun resetSession() {
        videoSearches.clear()
        playlistSearches.clear()
    }

    /** Canal do YouTube (não-artista do YT Music): vídeos que ele postou. */
    data class ChannelPage(
        val videos: List<Track>,
        val channelName: String?,
        val avatarUrl: String?,
        val subscriberCount: String?,
    )

    data class VideoPage(
        val items: List<SearchHit.VideoHit>,
        val hasMore: Boolean,
    )

    // ================= SEARCH =================

    suspend fun searchVideos(
        query: String,
        loadMore: Boolean,
        pageSize: Int = 20,
        searchId: String = query,
    ): VideoPage = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) {
            Log.e(TAG, "searchVideos: NewPipe not initialized")
            return@withContext VideoPage(emptyList(), false)
        }
        runCatching {
            if (!loadMore) {
                val extractor = ServiceList.YouTube.getSearchExtractor(query)
                extractor.fetchPage()
                val session = VideoSearchSession(extractor, extractor.initialPage.nextPage)
                videoSearches[searchId] = session
                val items = parseVideoItems(extractor.initialPage.items).take(pageSize)
                VideoPage(items, session.nextPage != null)
            } else {
                val session = videoSearches[searchId]
                    ?: return@runCatching VideoPage(emptyList(), false)
                val page = session.nextPage
                    ?: return@runCatching VideoPage(emptyList(), false)
                val next = session.extractor.getPage(page)
                session.nextPage = next.nextPage
                VideoPage(parseVideoItems(next.items).take(pageSize), session.nextPage != null)
            }
        }.getOrElse { e ->
            Log.e(TAG, "searchVideos failed for query=$query", e)
            VideoPage(emptyList(), false)
        }
    }

    suspend fun searchPlaylists(
        query: String,
        loadMore: Boolean,
        pageSize: Int = 30,
        searchId: String = query,
    ): List<SearchHit.PlaylistHit> = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) {
            Log.e(TAG, "searchPlaylists: NewPipe not initialized")
            return@withContext emptyList()
        }
        runCatching {
            if (!loadMore) {
                val extractor = ServiceList.YouTube.getSearchExtractor(
                    query, listOf("playlists"), null,
                )
                extractor.fetchPage()
                val session = PlaylistSearchSession(extractor, extractor.initialPage.nextPage)
                playlistSearches[searchId] = session
                parsePlaylistItems(extractor.initialPage.items).take(pageSize)
            } else {
                val session = playlistSearches[searchId]
                    ?: return@runCatching emptyList<SearchHit.PlaylistHit>()
                val page = session.nextPage
                    ?: return@runCatching emptyList<SearchHit.PlaylistHit>()
                val next = session.extractor.getPage(page)
                session.nextPage = next.nextPage
                parsePlaylistItems(next.items).take(pageSize)
            }
        }.getOrElse { e ->
            Log.e(TAG, "searchPlaylists failed for query=$query", e)
            emptyList()
        }
    }

    // ================= SMART VIDEO MATCH (for artist fallback) =================

    /**
     * Finds the best YouTube video match for the given title/artist hints
     * and returns the full metadata (channel, duration, thumbnail).
     *
     * Scoring:
     *   +1000 if the videoId matches exactly (strongest signal)
     *   +200  if the thumbnail URL matches exactly
     *   +100  if the title matches case-insensitively
     *   +50   if the uploader contains the artist hint
     *   +30   if the duration is within 5s of durationHint
     *   +10   if the duration is within 15s of durationHint
     *
     * Returns null if no candidate reaches MIN_SCORE (60).
     */
    suspend fun findVideoMetadata(
        videoId: String?,
        title: String,
        artistHint: String?,
        durationHintSeconds: Long? = null,
        thumbnailHint: String? = null,
    ): VideoMetadata? = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) return@withContext null
        if (title.isBlank()) return@withContext null

        // 1) Try exact videoId lookup first — most accurate.
        if (!videoId.isNullOrBlank()) {
            runCatching {
                extractMetadataForId(videoId)
            }.getOrNull()?.let { return@withContext it }
        }

        // 2) Search + score.
        val query = buildString {
            append(title)
            if (!artistHint.isNullOrBlank()) {
                append(" ")
                append(artistHint)
            }
        }

        runCatching {
            val extractor = ServiceList.YouTube.getSearchExtractor(query)
            extractor.fetchPage()

            val titleLower = title.lowercase().trim()
            val artistLower = artistHint?.lowercase()?.trim().orEmpty()

            extractor.initialPage.items
                .asSequence()
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { stream ->
                    val candidateId = extractVideoId(stream.url) ?: return@mapNotNull null
                    val candidateTitle = stream.name ?: return@mapNotNull null
                    val candidateTitleLower = candidateTitle.lowercase().trim()
                    val candidateUploader = stream.uploaderName?.trim().orEmpty()
                    val candidateUploaderLower = candidateUploader.lowercase()
                    val candidateDuration = stream.duration
                    val candidateThumb = stream.thumbnails.lastOrNull()?.url

                    var score = 0

                    if (candidateId == videoId) score += 1000
                    if (!thumbnailHint.isNullOrBlank() && candidateThumb == thumbnailHint) score += 200
                    if (candidateTitleLower == titleLower) score += 100
                    if (artistLower.isNotBlank() && candidateUploaderLower.contains(artistLower)) score += 50

                    if (durationHintSeconds != null && durationHintSeconds > 0 && candidateDuration > 0) {
                        val diff = kotlin.math.abs(candidateDuration - durationHintSeconds)
                        when {
                            diff <= 5 -> score += 30
                            diff <= 15 -> score += 10
                        }
                    }

                    if (score < MIN_SCORE) return@mapNotNull null

                    score to VideoMetadata(
                        videoId = candidateId,
                        channelName = cleanChannelName(candidateUploader),
                        title = candidateTitle,
                        durationSeconds = candidateDuration,
                        thumbnailUrl = candidateThumb,
                    )
                }
                .maxByOrNull { it.first }
                ?.second
        }.getOrElse { e ->
            Log.w(TAG, "findVideoMetadata failed for '$title': ${e.message}")
            null
        }
    }

    /**
     * Direct lookup by videoId via getStreamExtractor — no scoring needed.
     * Falls back to null on any error (removed video, private, etc).
     */
    private fun extractMetadataForId(videoId: String): VideoMetadata? {
        return runCatching {
            val extractor = ServiceList.YouTube.getStreamExtractor(
                "https://www.youtube.com/watch?v=$videoId"
            )
            extractor.fetchPage()
            val uploader = extractor.uploaderName?.trim().orEmpty()
            if (uploader.isBlank()) return@runCatching null

            VideoMetadata(
                videoId = videoId,
                channelName = cleanChannelName(uploader),
                title = extractor.name ?: "",
                durationSeconds = extractor.length,
                thumbnailUrl = extractor.thumbnails?.lastOrNull()?.url,
            )
        }.getOrNull()
    }

    private fun cleanChannelName(raw: String): String {
        var s = raw.trim()
        s = s.removeSuffix(" - Topic").removeSuffix(" - topic").trim()
        if (s.startsWith("Provided to YouTube", true)) return ""
        if (s.startsWith("Auto-generated by YouTube", true)) return ""
        return s
    }

    // ================= STREAM RESOLUTION =================

    suspend fun getStreamUrl(
        videoId: String,
        titleHint: String? = null,
        artistHint: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) {
            Log.e(TAG, "getStreamUrl: NewPipe not initialized")
            return@withContext null
        }

        Log.d(TAG, "getStreamUrl: videoId=$videoId")

        val fromWww = runCatching {
            extractFromUrl("https://www.youtube.com/watch?v=$videoId")
        }.getOrElse { e ->
            Log.w(TAG, "www attempt failed for $videoId: ${e.message}")
            null
        }
        if (!fromWww.isNullOrBlank()) {
            Log.d(TAG, "resolved via www: ${fromWww.take(80)}...")
            return@withContext fromWww
        }

        val fromMusic = runCatching {
            extractFromUrl("https://music.youtube.com/watch?v=$videoId")
        }.getOrElse { e ->
            Log.w(TAG, "music attempt failed for $videoId: ${e.message}")
            null
        }
        if (!fromMusic.isNullOrBlank()) {
            Log.d(TAG, "resolved via music: ${fromMusic.take(80)}...")
            return@withContext fromMusic
        }

        if (!titleHint.isNullOrBlank()) {
            val altId = runCatching { findBestMatchVideoId(titleHint, artistHint) }.getOrNull()
            if (altId != null && altId != videoId) {
                val fromAlt = runCatching {
                    extractFromUrl("https://www.youtube.com/watch?v=$altId")
                }.getOrNull()
                if (!fromAlt.isNullOrBlank()) {
                    Log.d(TAG, "resolved via fallback $altId: ${fromAlt.take(80)}...")
                    return@withContext fromAlt
                }
            }
        }

        Log.e(TAG, "NO STREAMS for $videoId")
        null
    }

    /**
     * Retorna info técnica (itag / mime / bitrate) do melhor stream de áudio.
     * Usado pela tela TrackInfo pra exibir detalhes técnicos.
     */
    suspend fun getAudioStreamInfo(videoId: String): AudioStreamInfo? = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) {
            Log.e(TAG, "getAudioStreamInfo: NewPipe not initialized")
            return@withContext null
        }
        runCatching {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val extractor = ServiceList.YouTube.getStreamExtractor(url)
            extractor.fetchPage()

            val audio = extractor.audioStreams
                .filter { it.averageBitrate > 0 }
                .maxByOrNull { it.averageBitrate }
                ?: extractor.audioStreams.firstOrNull()
                ?: return@runCatching null

            AudioStreamInfo(
                itag = runCatching { audio.itag.toString() }.getOrNull(),
                mimeType = runCatching { audio.format?.mimeType }.getOrNull(),
                quality = audio.averageBitrate
                    .takeIf { it > 0 }
                    ?.let { "${it / 1000} kbps" },
            )
        }.getOrElse { e ->
            Log.w(TAG, "getAudioStreamInfo failed for $videoId: ${e.message}")
            null
        }
    }

    private fun extractFromUrl(url: String): String {
        val extractor = ServiceList.YouTube.getStreamExtractor(url)
        extractor.fetchPage()

        val audioStreams = extractor.audioStreams
        val bestAudio: AudioStream? = audioStreams
            .filter { it.averageBitrate > 0 }
            .maxByOrNull { it.averageBitrate }
            ?: audioStreams.firstOrNull()
        if (bestAudio?.content?.isNotBlank() == true) return bestAudio.content

        extractor.hlsUrl?.takeIf { it.isNotBlank() }?.let { return it }

        val progressive = extractor.videoStreams
            .filter { !it.isVideoOnly }
            .minByOrNull {
                it.resolution.removeSuffix("p").toIntOrNull() ?: Int.MAX_VALUE
            }
        if (progressive?.content?.isNotBlank() == true) return progressive.content

        throw Exception("No streams available for $url")
    }

    private fun findBestMatchVideoId(title: String, artist: String?): String? {
        val query = buildString {
            append(title)
            if (!artist.isNullOrBlank()) {
                append(" ")
                append(artist)
            }
        }
        val extractor = ServiceList.YouTube.getSearchExtractor(query)
        extractor.fetchPage()

        val titleWords = title.lowercase()
            .split(Regex("\\s+"))
            .map { it.trim { c -> !c.isLetterOrDigit() } }
            .filter { it.length > 2 }
            .toSet()
        val artistLower = artist?.lowercase()?.trim().orEmpty()

        return extractor.initialPage.items
            .asSequence()
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { stream ->
                val videoId = extractVideoId(stream.url) ?: return@mapNotNull null
                val name = stream.name?.lowercase() ?: return@mapNotNull null
                val uploader = stream.uploaderName?.lowercase().orEmpty()
                var score = titleWords.count { name.contains(it) } * 10
                if (artistLower.isNotBlank() && uploader.contains(artistLower)) score += 40
                videoId to score
            }
            .filter { it.second >= 20 }
            .maxByOrNull { it.second }
            ?.first
    }

    // ================= FALLBACK THUMBNAIL =================

    suspend fun findVideoThumbnail(
        videoId: String,
        title: String,
        artist: String?,
    ): String? = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) return@withContext null

        val query = buildString {
            append(title)
            if (!artist.isNullOrBlank()) {
                append(" ")
                append(artist)
            }
        }

        runCatching {
            val extractor = ServiceList.YouTube.getSearchExtractor(query)
            extractor.fetchPage()

            val titleWords = title.lowercase()
                .split(Regex("\\s+"))
                .map { it.trim { c -> !c.isLetterOrDigit() } }
                .filter { it.length > 2 }
                .toSet()
            val artistLower = artist?.lowercase()?.trim().orEmpty()

            extractor.initialPage.items
                .asSequence()
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { stream ->
                    val name = stream.name?.lowercase() ?: return@mapNotNull null
                    val uploader = stream.uploaderName?.lowercase().orEmpty()
                    val thumb = stream.thumbnails.lastOrNull()?.url ?: return@mapNotNull null
                    var score = titleWords.count { name.contains(it) } * 10
                    if (artistLower.isNotBlank() && uploader.contains(artistLower)) score += 40
                    thumb to score
                }
                .filter { it.second >= 20 }
                .maxByOrNull { it.second }
                ?.first
        }.getOrElse { e ->
            Log.w(TAG, "findVideoThumbnail failed for $videoId: ${e.message}")
            null
        }
    }

    // ================= PARSERS =================

    private fun parseVideoItems(items: List<InfoItem>): List<SearchHit.VideoHit> {
        return items.filterIsInstance<StreamInfoItem>().mapNotNull { stream ->
            val videoId = extractVideoId(stream.url) ?: return@mapNotNull null
            SearchHit.VideoHit(
                Track(
                    id = videoId,
                    title = stream.name ?: "Untitled",
                    artist = stream.uploaderName ?: "",
                    durationSeconds = stream.duration,
                    thumbnailUrl = stream.thumbnails.firstOrNull()?.url,
                    source = Source.YOUTUBE,
                )
            )
        }
    }

    private fun parsePlaylistItems(items: List<InfoItem>): List<SearchHit.PlaylistHit> {
        return items.filterIsInstance<PlaylistInfoItem>().mapNotNull { playlist ->
            val listId = extractListId(playlist.url) ?: return@mapNotNull null
            SearchHit.PlaylistHit(
                Playlist(
                    id = "YT:$listId",
                    title = playlist.name ?: "Playlist",
                    owner = playlist.uploaderName ?: "",
                    thumbnailUrl = playlist.thumbnails.firstOrNull()?.url,
                    source = Source.YOUTUBE,
                )
            )
        }
    }

    private fun extractVideoId(url: String): String? = try {
        val uri = Uri.parse(url)
        uri.getQueryParameter("v")
            ?: uri.lastPathSegment?.takeIf { uri.host?.contains("youtu.be") == true }
    } catch (e: Exception) {
        null
    }

    private fun extractListId(url: String): String? = try {
        Uri.parse(url).getQueryParameter("list")
    } catch (e: Exception) {
        null
    }

    /**
     * Resolve o CANAL que postou o vídeo pelo videoId, via StreamExtractor do NewPipe.
     * Nunca busca por nome: uploaderUrl é a fonte da verdade (UC... ou @handle).
     * Retorna null quando o NewPipe não expõe o canal -> o chamador deve ESCONDER
     * "Ir para o canal", nunca cair pra busca por nome no YT Music.
     */
    suspend fun getChannelForVideo(videoId: String): YouTubeChannelInfo? = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) return@withContext null
        if (videoId.isBlank()) return@withContext null
        runCatching {
            val extractor = ServiceList.YouTube.getStreamExtractor(
                "https://www.youtube.com/watch?v=$videoId"
            )
            extractor.fetchPage()
            val uploaderName = extractor.uploaderName?.trim().orEmpty()
            val uploaderUrl = extractor.uploaderUrl?.trim().orEmpty()
            // NewPipe 0.27: não existe getUploaderAvatarUrl(). O avatar vem como
            // lista em getUploaderAvatars() -> List<Image>. Fallback: thumb do vídeo.
            val avatarUrl = extractor.uploaderAvatars.lastOrNull()?.url
                ?: extractor.thumbnails?.lastOrNull()?.url
            if (uploaderName.isBlank() || uploaderUrl.isBlank()) return@runCatching null
            val channelId = extractChannelId(uploaderUrl) ?: return@runCatching null
            YouTubeChannelInfo(
                channelId = channelId,
                channelName = cleanChannelName(uploaderName),
                avatarUrl = avatarUrl,
                channelUrl = uploaderUrl,
            )
        }.getOrNull()
    }

    /**
     * Extrai o UC... de uma uploaderUrl do NewPipe.
     * Formas: .../channel/UC..., .../UC..., .../@handle, .../c/nome, .../user/nome.
     * @handle / /c / /user não têm UC na URL -> resolve via ChannelExtractor do NewPipe.
     */
    private fun extractChannelId(uploaderUrl: String): String? {
        val ucRegex = Regex("UC[\\w-]{22}")
        ucRegex.find(uploaderUrl)?.let { return it.value }
        // @handle, /c/, /user/: ChannelExtractor resolve o id real.
        return runCatching {
            val extractor = ServiceList.YouTube.getChannelExtractor(uploaderUrl)
            extractor.fetchPage()
            extractor.id?.takeIf { it.startsWith("UC") }
        }.getOrNull()
    }

    /**
     * FIX 6: vídeos do CANAL REAL, pelo UC... dele.
     * Usado pela tela de artista quando o YT Music NÃO tem página de artista
     * pra esse UC... (canal puro de vídeo). O ChannelExtractor do NewPipe abre
     * o canal pelo ID, não por nome — então nunca cai num canal/artista parecido.
     * Retorna null quando o canal não existe / não tem vídeos (o chamador mostra
     * erro, nunca chuta outro artista).
     */
    suspend fun browseChannel(channelId: String): ChannelPage? = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) return@withContext null
        if (channelId.isBlank()) return@withContext null
        runCatching {
            val extractor = ServiceList.YouTube.getChannelExtractor(
                "https://www.youtube.com/channel/$channelId"
            )
            extractor.fetchPage()
            val channelName = runCatching { extractor.name }.getOrNull()?.trim().orEmpty()
            val avatarUrl = runCatching { extractor.avatars?.lastOrNull()?.url }.getOrNull()
            val subscriberCount = runCatching {
                extractor.subscriberCount.takeIf { it > 0 }?.let { formatSubscribers(it) }
            }.getOrNull()
            val videos = extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { stream ->
                    val videoId = extractVideoId(stream.url) ?: return@mapNotNull null
                    Track(
                        id = videoId,
                        title = stream.name ?: "Untitled",
                        artist = channelName,
                        durationSeconds = stream.duration,
                        thumbnailUrl = stream.thumbnails.lastOrNull()?.url,
                        source = Source.YOUTUBE,
                    )
                }
            ChannelPage(
                videos = videos,
                channelName = channelName.takeIf { it.isNotBlank() },
                avatarUrl = avatarUrl,
                subscriberCount = subscriberCount,
            )
        }.getOrNull()
    }

    private fun formatSubscribers(count: Long): String {
        return when {
            count >= 1_000_000 -> {
                val v = count / 1_000_000.0
                "${trimDecimal(v)} mi de inscritos"
            }
            count >= 1_000 -> {
                val v = count / 1_000.0
                "${trimDecimal(v)} mil inscritos"
            }
            else -> "$count inscritos"
        }
    }

    private fun trimDecimal(v: Double): String {
        val s = "%.1f".format(v)
        return if (s.endsWith(".0")) s.dropLast(2) else s
    }

    suspend fun buildVideoQueue(seed: Track): List<Track> = withContext(Dispatchers.IO) {
        val query = buildString {
            if (seed.title.isNotBlank()) append(seed.title)
            if (seed.artist.isNotBlank()) {
                if (isNotEmpty()) append(" ")
                append(seed.artist)
            }
        }.trim()

        if (query.isBlank()) return@withContext listOf(seed)

        val page = searchVideos(query, loadMore = false, pageSize = 30)
        val tracks = page.items.map { it.track }

        val list = if (tracks.any { it.id == seed.id }) {
            listOf(seed) + tracks.filter { it.id != seed.id }
        } else {
            listOf(seed) + tracks
        }
        list.distinctBy { it.id }.take(50)
    }

    private companion object {
        const val TAG = "VzTuneYT"
        const val MIN_SCORE = 60
    }
}
