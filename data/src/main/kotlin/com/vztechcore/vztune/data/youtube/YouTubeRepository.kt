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
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import com.vztechcore.vztune.data.innertube.InnerTubeYouTube
import java.time.OffsetDateTime
import org.schabi.newpipe.extractor.localization.DateWrapper
import java.time.temporal.ChronoUnit
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

    // Sessao de paginacao dos videos do canal (UU): guarda o extractor + a proxima pagina.
    // Sem guardar o extractor nao da pra chamar getPage() depois.
    private data class ChannelVideosSession(
        val extractor: PlaylistExtractor,
        var nextPage: Page?,
        val channelName: String,
    )

    private val channelVideoSessions = mutableMapOf<String, ChannelVideosSession>()

    // PART 2+3 - InnerTube continuation tokens (separate from NewPipe Page)
    private val ytVideoContinuations = mutableMapOf<String, String?>()
    private val ytPlaylistContinuations = mutableMapOf<String, String?>()
    private val ytShortContinuations = mutableMapOf<String, String?>()
    private val ytChannelVideoContinuations = mutableMapOf<String, String?>()
    private val ytChannelPlaylistContinuations = mutableMapOf<String, String?>()
    private val videoSearches = mutableMapOf<String, VideoSearchSession>()
    private val playlistSearches = mutableMapOf<String, PlaylistSearchSession>()
    private val shortSearches = mutableMapOf<String, VideoSearchSession>()

    // Sessao de paginacao do canal: guarda o extractor + a proxima pagina.
    // Sem guardar o extractor nao da pra chamar getPage() depois.
    private fun parseChannelStreamItems(items: List<InfoItem>, channelName: String): List<Track> {
        return items.filterIsInstance<StreamInfoItem>().mapNotNull { stream ->
            val vid = extractVideoId(stream.url) ?: return@mapNotNull null
            Track(id = vid, title = stream.name ?: "Untitled", artist = channelName, durationSeconds = stream.duration, thumbnailUrl = stream.thumbnails.lastOrNull()?.url, source = Source.YOUTUBE, viewCount = stream.viewCount.takeIf { it > 0 }, publishedText = formatPublished(stream.uploadDate))
        }
    }

    // FIX18 - Tem mais videos no UU? Usado pra mostrar/esconder o botao Buscar mais.
    fun hasMoreChannelVideos(channelId: String): Boolean {
        if (ytChannelVideoContinuations[channelId] != null) return true
        if (ytChannelVideoContinuations.entries.any { (k, v) -> v != null && (k == channelId || channelId.contains(k) || k.contains(channelId)) }) return true
        if (channelVideoSessions[channelId]?.nextPage != null) return true
        return channelVideoSessions.entries.any { (k, v) -> v.nextPage != null && (k == channelId || channelId.contains(k) || k.contains(channelId)) }
    }

    // FIX18 - Buscar mais: proxima pagina do UU (mais ~100). Retorna vazio se acabou.
    // FIX20 - Buscar mais videos: +500 por clique (5 paginas). Antes era 1 pagina (100).
    suspend fun loadMoreChannelVideos(channelId: String, pageSize: Int = 500): List<Track> = withContext(Dispatchers.IO) {

        // PART 2+3 - InnerTube continuation first for loadMoreChannelVideos
        val ytCont = ytChannelVideoContinuations[channelId]
            ?: ytChannelVideoContinuations.entries.firstOrNull { (k, v) -> v != null && (channelId.contains(k) || k.contains(channelId)) }?.value
        if (ytCont != null) {
            runCatching {
                val json = InnerTubeYouTube.browseContinuation(ytCont)
                val (items, next) = YouTubeChannelParser.parseVideosContinuation(json)
                ytChannelVideoContinuations[channelId] = next
                if (items.isNotEmpty() || next != null) return@withContext items.distinctBy { it.id }.take(pageSize)
            }.onFailure { Log.w(TAG, "InnerTube loadMoreChannelVideos failed: ${it.message}") }
        }

        val session = channelVideoSessions[channelId]
            ?: channelVideoSessions.entries.firstOrNull { (k, v) -> v.nextPage != null && (channelId.contains(k) || k.contains(channelId)) }?.value
            ?: return@withContext emptyList()
        var page: Page? = session.nextPage ?: return@withContext emptyList()
        runCatching {
            val collected = mutableListOf<Track>()
            var pages = 0
            while (collected.size < pageSize && page != null && pages < 5) {
                val next = session.extractor.getPage(page!!)
                collected += parseChannelStreamItems(next.items, session.channelName)
                page = next.nextPage
                session.nextPage = page
                pages++
            }
            collected.distinctBy { it.id }.take(pageSize)
        }.getOrDefault(emptyList())
    }

    fun resetSession() {
        channelVideoSessions.clear()
        channelPlaylistSessions.clear()
        videoSearches.clear()
        playlistSearches.clear()
        shortSearches.clear()
        ytVideoContinuations.clear()
        ytPlaylistContinuations.clear()
        ytShortContinuations.clear()
        ytChannelVideoContinuations.clear()
        ytChannelPlaylistContinuations.clear()
    }

    /** Canal do YouTube (não-artista do YT Music): vídeos que ele postou. */
    data class ChannelPage(
        val videos: List<Track>,
        val shorts: List<Track> = emptyList(),
        val playlists: List<Playlist> = emptyList(),
        val channelName: String?,
        val avatarUrl: String?,
        val bannerUrl: String? = null,
        val description: String? = null,
        val subscriberCount: String?,
        val hasMoreVideos: Boolean = false,
        val hasMorePlaylists: Boolean = false,
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

        // PART 2+3 - InnerTube first for searchVideos
        if (!loadMore) {
            runCatching {
                val json = InnerTubeYouTube.search(query)
                val items = YouTubeSearchParser.parseVideos(json).take(pageSize)
                val cont = YouTubeSearchParser.parseContinuation(json)
                ytVideoContinuations[searchId] = cont
                if (items.isNotEmpty() || cont != null) return@withContext VideoPage(items, cont != null)
            }.onFailure { Log.w(TAG, "InnerTube searchVideos failed, fallback NewPipe: ${it.message}") }
        } else {
            val cont = ytVideoContinuations[searchId]
            if (cont != null) {
                runCatching {
                    val json = InnerTubeYouTube.searchContinuation(cont)
                    val items = YouTubeSearchParser.parseVideos(json).take(pageSize)
                    val next = YouTubeSearchParser.parseContinuation(json)
                    ytVideoContinuations[searchId] = next
                    return@withContext VideoPage(items, next != null)
                }.onFailure { Log.w(TAG, "InnerTube searchVideos continuation failed: ${it.message}") }
            }
        }

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
                val next = session.extractor.getPage(page!!)
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

        // PART 2+3 - InnerTube first for searchPlaylists
        if (!loadMore) {
            runCatching {
                val json = InnerTubeYouTube.search(query, "EgIQAw%3D%3D")
                val items = YouTubeSearchParser.parsePlaylists(json).take(pageSize)
                val cont = YouTubeSearchParser.parseContinuation(json)
                ytPlaylistContinuations[searchId] = cont
                if (items.isNotEmpty() || cont != null) return@withContext items
                // Fallback: busca sem filtro - as vezes o filtro de playlist volta 0 no YouTube
                runCatching {
                    val json2 = InnerTubeYouTube.search(query)
                    val items2 = YouTubeSearchParser.parsePlaylists(json2).take(pageSize)
                    if (items2.isNotEmpty()) return@withContext items2
                }
            }.onFailure { Log.w(TAG, "InnerTube searchPlaylists failed, fallback NewPipe: ${it.message}") }
        } else {
            val cont = ytPlaylistContinuations[searchId]
            if (cont != null) {
                runCatching {
                    val json = InnerTubeYouTube.searchContinuation(cont)
                    val items = YouTubeSearchParser.parsePlaylists(json).take(pageSize)
                    val next = YouTubeSearchParser.parseContinuation(json)
                    ytPlaylistContinuations[searchId] = next
                    return@withContext items
                }.onFailure { Log.w(TAG, "InnerTube searchPlaylists continuation failed: ${it.message}") }
            }
        }

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
                val next = session.extractor.getPage(page!!)
                session.nextPage = next.nextPage
                parsePlaylistItems(next.items).take(pageSize)
            }
        }.getOrElse { e ->
            Log.e(TAG, "searchPlaylists failed for query=$query", e)
            emptyList()
        }
    }

    
    data class ShortPage(
        val items: List<SearchHit.ShortHit>,
        val hasMore: Boolean,
    )


        // FIX9_SEARCHSHORTS_DONE - Shorts definitivo (FIX8 + All)
    // FIX8_SHORTS - Shorts definitivo: 3 tentativas + parser que nao joga tudo fora.
    // Antes: filtro "shorts" falhava e o parser so aceitava /shorts/, entao busca generica voltava vazia.
    // Agora: 1) filtro shorts, 2) busca normal, 3) busca "query shorts". Parser aceita /shorts/ e, como fallback, watch curto (<=65s).

    suspend fun searchShorts(
        query: String,
        loadMore: Boolean,
        pageSize: Int = 30,
        searchId: String = query,
    ): ShortPage = withContext(Dispatchers.IO) {

        // PART 2+3 - InnerTube first for searchShorts
        if (!loadMore) {
            runCatching {
                val json = InnerTubeYouTube.search(query)
                val items = YouTubeSearchParser.parseShorts(json).take(pageSize)
                val cont = YouTubeSearchParser.parseContinuation(json)
                ytShortContinuations[searchId] = cont
                if (items.isNotEmpty() || cont != null) return@withContext ShortPage(items, cont != null)
            }.onFailure { Log.w(TAG, "InnerTube searchShorts failed, fallback NewPipe: ${it.message}") }
        } else {
            val cont = ytShortContinuations[searchId]
            if (cont != null) {
                runCatching {
                    val json = InnerTubeYouTube.searchContinuation(cont)
                    val items = YouTubeSearchParser.parseShorts(json).take(pageSize)
                    val next = YouTubeSearchParser.parseContinuation(json)
                    ytShortContinuations[searchId] = next
                    return@withContext ShortPage(items, next != null)
                }.onFailure { Log.w(TAG, "InnerTube searchShorts continuation failed: ${it.message}") }
            }
        }

        if (!NewPipeInitializer.ensureInitialized(downloader)) {
            Log.e(TAG, "searchShorts: NewPipe not initialized")
            return@withContext ShortPage(emptyList(), false)
        }
        if (loadMore) {
            val session = shortSearches[searchId] ?: return@withContext ShortPage(emptyList(), false)
            val page = session.nextPage ?: return@withContext ShortPage(emptyList(), false)
            return@withContext runCatching {
                val next = session.extractor.getPage(page!!)
                session.nextPage = next.nextPage
                ShortPage(parseShortItemsLoose(next.items).take(pageSize), session.nextPage != null)
            }.getOrElse { e ->
                Log.e(TAG, "searchShorts loadMore failed for query=$query", e)
                ShortPage(emptyList(), false)
            }
        }
        // Tentativa 1: filtro shorts oficial
        val attempt1 = runCatching {
            val extractor = ServiceList.YouTube.getSearchExtractor(query, listOf("shorts"), null)
            extractor.fetchPage()
            val items = parseShortItemsAll(extractor.initialPage.items).take(pageSize)
            if (items.isNotEmpty()) {
                shortSearches[searchId] = VideoSearchSession(extractor, extractor.initialPage.nextPage)
                return@withContext ShortPage(items, extractor.initialPage.nextPage != null)
            }
            null
        }.getOrNull()
        if (attempt1 != null) return@withContext attempt1
        // Tentativa 2: busca normal, filtra so /shorts/
        val attempt2 = runCatching {
            val extractor = ServiceList.YouTube.getSearchExtractor(query)
            extractor.fetchPage()
            val items = parseShortItemsStrict(extractor.initialPage.items).take(pageSize)
            if (items.isNotEmpty()) {
                shortSearches[searchId] = VideoSearchSession(extractor, extractor.initialPage.nextPage)
                return@withContext ShortPage(items, extractor.initialPage.nextPage != null)
            }
            null
        }.getOrNull()
        if (attempt2 != null) return@withContext attempt2
        // Tentativa 3: busca "query shorts" + parser frouxo (watch curto conta como Shorts)
        return@withContext runCatching {
            val extractor = ServiceList.YouTube.getSearchExtractor("$query shorts")
            extractor.fetchPage()
            val strict = parseShortItemsStrict(extractor.initialPage.items).take(pageSize)
            val loose = if (strict.isNotEmpty()) strict else parseShortItemsLoose(extractor.initialPage.items).take(pageSize)
            val items = if (loose.isNotEmpty()) loose else parseShortItemsAll(extractor.initialPage.items).take(pageSize)
            shortSearches[searchId] = VideoSearchSession(extractor, extractor.initialPage.nextPage)
            ShortPage(items, extractor.initialPage.nextPage != null)
        }.getOrElse { e ->
            Log.e(TAG, "searchShorts failed for query=$query", e)
            ShortPage(emptyList(), false)
        }
    }

    // FIX9_ALL - Quando a busca ja veio filtrada pra Shorts, TODO item E Shorts.
    // Nao filtra por /shorts/ nem por duracao: o NewPipe devolve Shorts com URL watch?v=
    // mesmo filtrado, e o FIX8 jogava tudo fora por causa disso.
    private fun parseShortItemsAll(items: List<InfoItem>): List<SearchHit.ShortHit> {
        return items.filterIsInstance<StreamInfoItem>().mapNotNull { stream ->
            val rawUrl = stream.url ?: return@mapNotNull null
            val videoId = extractVideoId(rawUrl) ?: return@mapNotNull null
            SearchHit.ShortHit(
                Track(
                    id = videoId,
                    title = stream.name ?: "Untitled",
                    artist = stream.uploaderName ?: "",
                    durationSeconds = stream.duration,
                    thumbnailUrl = stream.thumbnails.lastOrNull()?.url,
                    source = Source.YOUTUBE,
                )
            )
        }
    }

    private fun parseShortItemsStrict(items: List<InfoItem>): List<SearchHit.ShortHit> {
        return items.filterIsInstance<StreamInfoItem>().mapNotNull { stream ->
            val rawUrl = stream.url ?: return@mapNotNull null
            if (!rawUrl.contains("/shorts/")) return@mapNotNull null
            val videoId = extractVideoId(rawUrl) ?: return@mapNotNull null
            SearchHit.ShortHit(
                Track(
                    id = videoId,
                    title = stream.name ?: "Untitled",
                    artist = stream.uploaderName ?: "",
                    durationSeconds = stream.duration,
                    thumbnailUrl = stream.thumbnails.lastOrNull()?.url,
                    source = Source.YOUTUBE,
                )
            )
        }
    }

    private fun parseShortItemsLoose(items: List<InfoItem>): List<SearchHit.ShortHit> {
        return items.filterIsInstance<StreamInfoItem>().mapNotNull { stream ->
            val rawUrl = stream.url ?: return@mapNotNull null
            val isShortsUrl = rawUrl.contains("/shorts/")
            // Fallback: video curto (1s a 65s) com URL de watch pode ser Shorts que o YouTube devolveu como video normal.
            val isShortDuration = stream.duration in 1..65
            if (!isShortsUrl && !isShortDuration) return@mapNotNull null
            val videoId = extractVideoId(rawUrl) ?: return@mapNotNull null
            SearchHit.ShortHit(
                Track(
                    id = videoId,
                    title = stream.name ?: "Untitled",
                    artist = stream.uploaderName ?: "",
                    durationSeconds = stream.duration,
                    thumbnailUrl = stream.thumbnails.lastOrNull()?.url,
                    source = Source.YOUTUBE,
                )
            )
        }
    }

    // Compat: nome antigo usado em outros lugares aponta para o parser estrito.
    private fun parseShortItems(items: List<InfoItem>): List<SearchHit.ShortHit> = parseShortItemsStrict(items)

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

        // PART 5 - InnerTube first for getStreamUrl (player streamingData)
        runCatching {
            val playerJson = InnerTubeYouTube.player(videoId)
            val streaming = playerJson.optJSONObject("streamingData")
            val adaptive = streaming?.optJSONArray("adaptiveFormats")
            var bestUrl: String? = null
            var bestBitrate = -1L
            if (adaptive != null) {
                for (i in 0 until adaptive.length()) {
                    val f = adaptive.optJSONObject(i) ?: continue
                    val mime = f.optString("mimeType")
                    if (!mime.contains("audio")) continue
                    val bitrate = f.optLong("bitrate", 0)
                    val url = f.optString("url").takeIf { it.isNotBlank() }
                    if (url != null && bitrate > bestBitrate) {
                        bestBitrate = bitrate
                        bestUrl = url
                    }
                }
            }
            if (bestUrl == null) {
                val formats = streaming?.optJSONArray("formats")
                if (formats != null) {
                    for (i in 0 until formats.length()) {
                        val f = formats.optJSONObject(i) ?: continue
                        val url = f.optString("url").takeIf { it.isNotBlank() } ?: continue
                        bestUrl = url
                        break
                    }
                }
            }
            if (!bestUrl.isNullOrBlank()) return@withContext bestUrl
        }.onFailure { Log.w(TAG, "InnerTube getStreamUrl failed, fallback NewPipe: ${it.message}") }

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

        // PART 5 - InnerTube first for getAudioStreamInfo
        runCatching {
            val playerJson5 = InnerTubeYouTube.player(videoId)
            val streaming5 = playerJson5.optJSONObject("streamingData")
            val adaptive5 = streaming5?.optJSONArray("adaptiveFormats")
            var bestItag: String? = null
            var bestMime: String? = null
            var bestBitrate5 = -1L
            if (adaptive5 != null) {
                for (i in 0 until adaptive5.length()) {
                    val f = adaptive5.optJSONObject(i) ?: continue
                    val mime = f.optString("mimeType")
                    if (!mime.contains("audio")) continue
                    val bitrate = f.optLong("bitrate", 0)
                    if (bitrate > bestBitrate5) {
                        bestBitrate5 = bitrate
                        bestItag = f.optString("itag").takeIf { it.isNotBlank() }
                        bestMime = mime
                    }
                }
            }
            if (bestBitrate5 > 0) {
                return@withContext AudioStreamInfo(
                    itag = bestItag,
                    mimeType = bestMime,
                    quality = "${bestBitrate5 / 1000} kbps",
                )
            }
        }.onFailure { Log.w(TAG, "InnerTube getAudioStreamInfo failed, fallback NewPipe: ${it.message}") }

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
            ?: run {
                val path = uri.path ?: ""
                if (path.contains("/shorts/")) {
                    path.substringAfter("/shorts/").substringBefore("/").substringBefore("?").takeIf { it.isNotBlank() }
                } else {
                    uri.lastPathSegment?.takeIf { uri.host?.contains("youtu.be") == true }
                }
            }
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
    // FIX8_CHANNEL_VIDEO - Canal real do video via NewPipe, 3 URLs. Sem busca por nome, sem YT Music.
    suspend fun getChannelForVideo(videoId: String): YouTubeChannelInfo? = withContext(Dispatchers.IO) {


        if (!NewPipeInitializer.ensureInitialized(downloader)) return@withContext null
        val cleanId = videoId.trim().substringAfter("v=").substringAfter("/shorts/").substringBefore("?").substringBefore("&").trim()
        if (cleanId.isBlank()) return@withContext null
        // PART 2+3 - InnerTube first for getChannelForVideo
        runCatching {
            val player = InnerTubeYouTube.player(cleanId)
            val details = player.optJSONObject("videoDetails")
            val chId = details?.optString("channelId").orEmpty()
            val author = details?.optString("author").orEmpty()
            if (chId.startsWith("UC") && author.isNotBlank()) {
                return@withContext YouTubeChannelInfo(
                    channelId = chId,
                    channelName = cleanChannelName(author),
                    avatarUrl = null,
                    channelUrl = "https://www.youtube.com/channel/$chId"
                )
            }
        }.onFailure { Log.w(TAG, "InnerTube getChannelForVideo failed: ${it.message}") }
        val urls = listOf(
            "https://www.youtube.com/watch?v=$cleanId",
            "https://www.youtube.com/shorts/$cleanId",
            "https://youtu.be/$cleanId",
        )
        for (url in urls) {
            val info = runCatching {
                val extractor = ServiceList.YouTube.getStreamExtractor(url)
                extractor.fetchPage()
                val uploaderName = extractor.uploaderName?.trim().orEmpty()
                val uploaderUrl = extractor.uploaderUrl?.trim().orEmpty()
                if (uploaderName.isBlank() || uploaderUrl.isBlank()) return@runCatching null
                val channelId = extractChannelId(uploaderUrl) ?: return@runCatching null
                val avatarUrl = runCatching { extractor.uploaderAvatars.lastOrNull()?.url }.getOrNull()
                    ?: runCatching { extractor.thumbnails?.lastOrNull()?.url }.getOrNull()
                YouTubeChannelInfo(
                    channelId = channelId,
                    channelName = cleanChannelName(uploaderName),
                    avatarUrl = avatarUrl,
                    channelUrl = uploaderUrl,
                )
            }.getOrNull()
            if (info != null) return@withContext info
        }
        null
    }


    // FIX8_CHANNEL_PLAYLIST - Dono real da playlist. Distingue video de musica.
    // PL/UU/UC = playlist de video -> NewPipe. OLAK/RD/RDAMVM = radio/mix do YT Music -> nao tem dono de canal, retorna null.
    // VL = prefixo do YT Music, tira. UU = playlist de uploads -> UC direto, sem precisar de extractor.
    suspend fun getChannelForPlaylist(playlistId: String): YouTubeChannelInfo? = withContext(Dispatchers.IO) {


        if (!NewPipeInitializer.ensureInitialized(downloader)) return@withContext null
        var rawId = playlistId.trim().removePrefix("YT:").removePrefix("VL").trim()
        if (rawId.isBlank()) return@withContext null
        // PART 2+3 - InnerTube first for getChannelForPlaylist (UC/UU only)
        runCatching {
            val ucFromUu = if (rawId.startsWith("UU") && rawId.length >= 24) "UC" + rawId.drop(2) else null
            val ucDirect = if (rawId.startsWith("UC") && rawId.length >= 24) rawId else ucFromUu
            if (ucDirect != null) {
                val json = InnerTubeYouTube.browse(ucDirect)
                val parsed = YouTubeChannelParser.parseChannelPage(json)
                if (parsed.channelName.isNotBlank()) {
                    return@withContext YouTubeChannelInfo(
                        channelId = ucDirect,
                        channelName = cleanChannelName(parsed.channelName),
                        avatarUrl = parsed.avatarUrl,
                        channelUrl = "https://www.youtube.com/channel/$ucDirect"
                    )
                }
            }
        }.onFailure { Log.w(TAG, "InnerTube getChannelForPlaylist failed: ${it.message}") }
        // Radio / mix nao tem dono de canal
        if (rawId.startsWith("OLAK") || rawId.startsWith("RD") || rawId.startsWith("RDAMVM") || rawId.startsWith("RDEM")) {
            return@withContext null
        }
        // UU... = uploads do canal -> UC... direto
        if (rawId.startsWith("UU") && rawId.length >= 24) {
            val uc = "UC" + rawId.drop(2)
            val header = runCatching {
                val ex = ServiceList.YouTube.getChannelExtractor("https://www.youtube.com/channel/$uc")
                ex.fetchPage()
                YouTubeChannelInfo(
                    channelId = uc,
                    channelName = cleanChannelName(ex.name ?: ""),
                    avatarUrl = runCatching { ex.avatars?.lastOrNull()?.url }.getOrNull(),
                    channelUrl = "https://www.youtube.com/channel/$uc",
                )
            }.getOrNull()
            if (header != null && header.channelName.isNotBlank()) return@withContext header
            return@withContext YouTubeChannelInfo(uc, "", null, "https://www.youtube.com/channel/$uc")
        }
        // UC direto = ja e o canal
        if (rawId.startsWith("UC") && rawId.length >= 24) {
            return@withContext YouTubeChannelInfo(rawId, "", null, "https://www.youtube.com/channel/$rawId")
        }
        // Playlist de video normal: PlaylistExtractor -> uploaderUrl -> UC
        return@withContext runCatching {
            val extractor = ServiceList.YouTube.getPlaylistExtractor("https://www.youtube.com/playlist?list=$rawId")
            extractor.fetchPage()
            val uploaderName = extractor.uploaderName?.trim().orEmpty()
            val uploaderUrl = extractor.uploaderUrl?.trim().orEmpty()
            if (uploaderName.isBlank() || uploaderUrl.isBlank()) return@runCatching null
            val channelId = extractChannelId(uploaderUrl) ?: return@runCatching null
            YouTubeChannelInfo(
                channelId = channelId,
                channelName = cleanChannelName(uploaderName),
                avatarUrl = null,
                channelUrl = uploaderUrl,
            )
        }.getOrNull()
    }


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
     * FIX 6 HOTFIX: vídeos do CANAL REAL, pelo UC... dele.
     *
     * Por que NÃO ChannelExtractor.initialPage: na NewPipeExtractor v0.26.5 o
     * ChannelExtractor NÃO estende ListExtractor<InfoItem> — não tem initialPage/getPage.
     * Foi exatamente isso que quebrou o build (linha 596). Ele só expõe
     * cabeçalho (nome, avatar, inscritos), que continua sendo usado aqui.
     *
     * Os vídeos vêm da playlist "Uploads" do canal: todo canal do YouTube tem
     * uma playlist de sistema com id UU + resto do UC... (ex.: UCabc -> UUabc).
     * PlaylistExtractor SIM estende ListExtractor<InfoItem> — mesma API do SearchExtractor
     * que este arquivo já usa (initialPage/getPage) — então lista os vídeos do
     * canal pelo ID, nunca por nome. O comportamento é o mesmo do FIX 6,
     * só muda a fonte da lista.
     */
    private data class ChannelHeader(
        val name: String,
        val avatar: String?,
        val banner: String?,
        val description: String,
        val subs: String?,
    )

    // FIX8_BROWSE_CHANNEL - Canal grande abre mesmo se UU vier vazio. Aceita UC, @handle e URL.
    // Antes: se UU viesse vazio, retornava null e a tela mostrava erro. Agora: retorna cabecalho + tenta 3 fontes.
        // FIX19 - So aceita conteudo do MESMO criador. Normaliza: minusculo, sem " - Topic", sem espacos extras.
    // "TH" vs "TH - Topic" = mesmo criador. "th" vs "TH2" / "The ..." = criador diferente, descarta.
    private fun isSameCreator(owner: String?, channelName: String): Boolean {
        if (owner.isNullOrBlank() || channelName.isBlank()) return false
        fun norm(s: String): String {
            var v = s.trim().lowercase()
            if (v.endsWith(" - topic")) v = v.removeSuffix(" - topic").trim()
            if (v.endsWith("- topic")) v = v.removeSuffix("- topic").trim()
            return v.replace(Regex("\\s+"), " ").trim()
        }
        return norm(owner) == norm(channelName)
    }

    // FIX20 - Sessao de playlists do canal (/playlists). Igual a de videos: guarda extractor + nextPage pra Buscar mais nao recomecar.
    private data class ChannelPlaylistsSession(
        val extractor: SearchExtractor,
        var nextPage: Page?,
        val channelName: String,
    )
    private val channelPlaylistSessions = mutableMapOf<String, ChannelPlaylistsSession>()
    private fun parseChannelPlaylistItems(items: List<InfoItem>, channelName: String): List<Playlist> {
        return items.filterIsInstance<PlaylistInfoItem>().mapNotNull { item ->
            val pid = Regex("list=([^&]+)").find(item.url ?: "")?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (channelName.isNotBlank() && !item.uploaderName.isNullOrBlank() && !isSameCreator(item.uploaderName, channelName)) return@mapNotNull null
            Playlist(id = pid, title = item.name ?: "Playlist", owner = item.uploaderName?.takeIf { it.isNotBlank() } ?: channelName, thumbnailUrl = item.thumbnails.lastOrNull()?.url, trackCount = item.streamCount.takeIf { it > 0 }?.toInt(), source = Source.YOUTUBE)
        }
    }
    fun hasMoreChannelPlaylists(channelId: String): Boolean {
        if (ytChannelPlaylistContinuations[channelId] != null) return true
        if (ytChannelPlaylistContinuations.entries.any { (k, v) -> v != null && (k == channelId || channelId.contains(k) || k.contains(channelId)) }) return true
        if (channelPlaylistSessions[channelId]?.nextPage != null) return true
        return channelPlaylistSessions.entries.any { (k, v) -> v.nextPage != null && (k == channelId || channelId.contains(k) || k.contains(channelId)) }
    }
    suspend fun loadMoreChannelPlaylists(channelId: String, pageSize: Int = 500): List<Playlist> = withContext(Dispatchers.IO) {

        // PART 2+3 - InnerTube continuation first for loadMoreChannelPlaylists
        val ytContPl = ytChannelPlaylistContinuations[channelId]
            ?: ytChannelPlaylistContinuations.entries.firstOrNull { (k, v) -> v != null && (channelId.contains(k) || k.contains(channelId)) }?.value
        if (ytContPl != null) {
            runCatching {
                val json = InnerTubeYouTube.browseContinuation(ytContPl)
                val (items, next) = YouTubeChannelParser.parsePlaylistsContinuation(json)
                ytChannelPlaylistContinuations[channelId] = next
                if (items.isNotEmpty() || next != null) return@withContext items.distinctBy { it.id }.take(pageSize)
            }.onFailure { Log.w(TAG, "InnerTube loadMoreChannelPlaylists failed: ${it.message}") }
        }

        val session = channelPlaylistSessions[channelId]
            ?: channelPlaylistSessions.entries.firstOrNull { (k, v) -> v.nextPage != null && (channelId.contains(k) || k.contains(channelId)) }?.value
            ?: return@withContext emptyList()
        var page: Page? = session.nextPage ?: return@withContext emptyList()
        runCatching {
            val collected = mutableListOf<Playlist>()
            var pages = 0
            while (collected.size < pageSize && page != null && pages < 5) {
                val next = session.extractor.getPage(page!!)
                collected += parseChannelPlaylistItems(next.items, session.channelName)
                page = next.nextPage
                session.nextPage = page
                pages++
            }
            collected.distinctBy { it.id }.take(pageSize)
        }.getOrDefault(emptyList())
    }
    data class YouTubePlaylistPage(
        val title: String?,
        val owner: String?,
        val ownerChannelId: String?,
        val coverUrl: String?,
        val description: String?,
        val tracks: List<Track>,
    )
    suspend fun browseYouTubePlaylist(playlistId: String): YouTubePlaylistPage? = withContext(Dispatchers.IO) {

        // PART 4 - InnerTube first for browseYouTubePlaylist
        runCatching {
            val raw4 = playlistId.trim().removePrefix("YT:").trim()
            val listId4 = if (raw4.startsWith("http")) {
                Regex("[?&]list=([^&]+)").find(raw4)?.groupValues?.getOrNull(1) ?: raw4
            } else raw4
            if (listId4.isNotBlank() && !listId4.startsWith("OLAK") && !listId4.startsWith("RD")) {
                val browseId4 = if (listId4.startsWith("VL")) listId4 else "VL" + listId4
                val json4 = InnerTubeYouTube.browse(browseId4)
                val parsed4 = YouTubePlaylistParser.parsePlaylistPage(json4)
                val all4 = parsed4.tracks.toMutableList()
                var cont4 = parsed4.continuation
                var pages4 = 0
                while (all4.size < 500 && cont4 != null && pages4 < 5) {
                    val cj = InnerTubeYouTube.browseContinuation(cont4)
                    val (more, next) = YouTubePlaylistParser.parsePlaylistContinuation(cj)
                    all4 += more
                    cont4 = next
                    pages4++
                }
                if (all4.isNotEmpty() || !parsed4.title.isNullOrBlank()) {
                    return@withContext YouTubePlaylistPage(parsed4.title, parsed4.owner, parsed4.ownerChannelId, parsed4.coverUrl, parsed4.description, all4.distinctBy { it.id })
                }
            }
        }.onFailure { Log.w(TAG, "InnerTube browseYouTubePlaylist failed, fallback NewPipe: ${it.message}") }

        if (!NewPipeInitializer.ensureInitialized(downloader)) return@withContext null
        runCatching {
            val raw = playlistId.trim().removePrefix("YT:").trim()
            val url = if (raw.startsWith("http")) raw else "https://www.youtube.com/playlist?list=$raw"
            val pl = ServiceList.YouTube.getPlaylistExtractor(url)
            pl.fetchPage()
            val ownerName = runCatching { pl.uploaderName?.trim() }.getOrNull()?.takeIf { it.isNotBlank() }
            val uploaderUrl = runCatching { pl.uploaderUrl?.trim() }.getOrNull().orEmpty()
            val ownerId = if (uploaderUrl.isNotBlank()) extractChannelId(uploaderUrl) else null
            val title = runCatching { pl.name?.trim() }.getOrNull()?.takeIf { it.isNotBlank() }
            val desc = runCatching { pl.description?.content }.getOrNull()?.takeIf { it.isNotBlank() }
            val collected = parseChannelStreamItems(pl.initialPage.items, ownerName.orEmpty()).toMutableList()
            var nextPage: Page? = pl.initialPage.nextPage
            var pages = 0
            while (collected.size < 500 && nextPage != null && pages < 5) {
                val next = pl.getPage(nextPage!!)
                collected += parseChannelStreamItems(next.items, ownerName.orEmpty())
                nextPage = next.nextPage
                pages++
            }
            val cover = collected.firstOrNull()?.thumbnailUrl
            YouTubePlaylistPage(title, ownerName, ownerId, cover, desc, collected.distinctBy { it.id })
        }.getOrNull()
    }
suspend fun browseChannel(channelIdOrHandle: String): ChannelPage? = withContext(Dispatchers.IO) {


        if (!NewPipeInitializer.ensureInitialized(downloader)) return@withContext null
        val input = channelIdOrHandle.trim()
        if (input.isBlank()) return@withContext null
                        // FIX CHANNEL COMPLETO - busca COMPLETA, nao so a 1a pagina.
        // Antes: pegava 1 pagina (100 no UU, ~30 na aba) e guardava token pro loadMore. Se a tela nao chama loadMore, vinha incompleto.
        // Agora: pagina UU + aba ate acabar ou bater o teto (1000 videos, 500 shorts, 200 playlists, 10 paginas cada).
        // E SEMPRE busca a aba cheia, mesmo que a Home ja tenha 5 videos em destaque (destaque != lista completa).
        // Fonte primaria pra videos/shorts e UU (ordem estavel, 100/pagina): UULF = videos, UUSH = shorts. Aba e fallback.
        // Playlists nao tem UU, entao pagina a aba Playlists com continuation ate o fim.
        runCatching {
            val uc = Regex("UC[\\w-]{22}").find(input)?.value
            if (uc != null) {
                val homeJson = InnerTubeYouTube.browse(uc)
                val homeParsed = YouTubeChannelParser.parseChannelPage(homeJson)
                val channelName = homeParsed.channelName
                // ---- VIDEOS via UULF loop ----
                var videos = mutableListOf<com.vztechcore.vztune.core.domain.music.Track>()
                var videosCont: String? = null
                runCatching {
                    var seenTokens = mutableSetOf<String>()
                    var json = InnerTubeYouTube.browse("VL" + "UULF" + uc.drop(2))
                    var parsed = YouTubePlaylistParser.parsePlaylistPage(json, channelName)
                    videos += parsed.tracks
                    videosCont = parsed.continuation
                    var pages = 1
                    while (videos.size < 1000 && videosCont != null && pages < 10) {
                        val token = videosCont ?: break
                        if (!seenTokens.add(token)) break
                        val cj = InnerTubeYouTube.browseContinuation(token)
                        val (more, next) = YouTubePlaylistParser.parsePlaylistContinuation(cj)
                        if (more.isEmpty() && next == null) { videosCont = null; break }
                        videos += more
                        videosCont = next
                        pages++
                    }
                }.onFailure { Log.w(TAG, "UULF loop failed: ${it.message}") }
                // Se UULF deu 0, tenta UU (tudo) e depois aba Videos
                if (videos.isEmpty()) {
                    runCatching {
                        var seenTokens = mutableSetOf<String>()
                        var json = InnerTubeYouTube.browse("VL" + "UU" + uc.drop(2))
                        var parsed = YouTubePlaylistParser.parsePlaylistPage(json, channelName)
                        videos += parsed.tracks
                        videosCont = parsed.continuation
                        var pages = 1
                        while (videos.size < 1000 && videosCont != null && pages < 10) {
                            val token = videosCont ?: break
                            if (!seenTokens.add(token)) break
                            val cj = InnerTubeYouTube.browseContinuation(token)
                            val (more, next) = YouTubePlaylistParser.parsePlaylistContinuation(cj)
                            if (more.isEmpty() && next == null) { videosCont = null; break }
                            videos += more
                            videosCont = next
                            pages++
                        }
                    }.onFailure { Log.w(TAG, "UU loop failed: ${it.message}") }
                }
                if (videos.isEmpty()) {
                    val videosParams = YouTubeChannelParser.tabParams(homeJson, "videos")
                    if (videosParams != null) {
                        runCatching {
                            var seenTokens = mutableSetOf<String>()
                            var json = InnerTubeYouTube.browse(uc, videosParams)
                            var parsed = YouTubeChannelParser.parseChannelPage(json)
                            videos += parsed.videos
                            videosCont = parsed.videosContinuation
                            var pages = 1
                            while (videos.size < 1000 && videosCont != null && pages < 10) {
                                val token = videosCont ?: break
                                if (!seenTokens.add(token)) break
                                val cj = InnerTubeYouTube.browseContinuation(token)
                                val (more, next) = YouTubeChannelParser.parseVideosContinuation(cj)
                                if (more.isEmpty() && next == null) { videosCont = null; break }
                                videos += more
                                videosCont = next
                                pages++
                            }
                        }.onFailure { Log.w(TAG, "Videos tab loop failed: ${it.message}") }
                    }
                }
                // ---- SHORTS via UUSH loop ----
                var shorts = mutableListOf<com.vztechcore.vztune.core.domain.music.Track>()
                var shortsCont: String? = null
                runCatching {
                    var seenTokens = mutableSetOf<String>()
                    var json = InnerTubeYouTube.browse("VL" + "UUSH" + uc.drop(2))
                    var parsed = YouTubePlaylistParser.parsePlaylistPage(json, channelName)
                    shorts += parsed.tracks
                    shortsCont = parsed.continuation
                    var pages = 1
                    while (shorts.size < 500 && shortsCont != null && pages < 10) {
                        val token = shortsCont ?: break
                        if (!seenTokens.add(token)) break
                        val cj = InnerTubeYouTube.browseContinuation(token)
                        val (more, next) = YouTubePlaylistParser.parsePlaylistContinuation(cj)
                        if (more.isEmpty() && next == null) { shortsCont = null; break }
                        shorts += more
                        shortsCont = next
                        pages++
                    }
                }.onFailure { Log.w(TAG, "UUSH loop failed: ${it.message}") }
                if (shorts.isEmpty()) {
                    val shortsParams = YouTubeChannelParser.tabParams(homeJson, "shorts")
                    if (shortsParams != null) {
                        runCatching {
                            var seenTokens = mutableSetOf<String>()
                            var json = InnerTubeYouTube.browse(uc, shortsParams)
                            var parsed = YouTubeChannelParser.parseChannelPage(json)
                            shorts += parsed.shorts
                            shortsCont = parsed.shortsContinuation
                            var pages = 1
                            while (shorts.size < 500 && shortsCont != null && pages < 10) {
                                val token = shortsCont ?: break
                                if (!seenTokens.add(token)) break
                                val cj = InnerTubeYouTube.browseContinuation(token)
                                val (more, next) = YouTubeChannelParser.parseShortsContinuation(cj)
                                if (more.isEmpty() && next == null) { shortsCont = null; break }
                                shorts += more
                                shortsCont = next
                                pages++
                            }
                            // Shorts tab as vezes devolve videos longos no campo videos - junta se shorts ainda vazio
                            if (shorts.isEmpty() && parsed.videos.isNotEmpty()) shorts += parsed.videos
                        }.onFailure { Log.w(TAG, "Shorts tab loop failed: ${it.message}") }
                    }
                }
                // Se videos vieram de UU (tudo), tira os que sao shorts
                if (shorts.isNotEmpty() && videos.isNotEmpty()) {
                    val shortIds = shorts.map { it.id }.toSet()
                    videos = videos.filter { it.id !in shortIds }.toMutableList()
                }
                // Preenche artista em branco com nome do canal (lockup nao traz autor)
                if (channelName.isNotBlank()) {
                    videos = videos.map { if (it.artist.isBlank()) it.copy(artist = channelName) else it }.toMutableList()
                    shorts = shorts.map { if (it.artist.isBlank()) it.copy(artist = channelName) else it }.toMutableList()
                }
                // ---- PLAYLISTS via aba Playlists loop (SEMPRE, mesmo que Home tenha destaque) ----
                var playlists = mutableListOf<com.vztechcore.vztune.core.domain.music.Playlist>()
                var playlistsCont: String? = null
                val playlistsParams = YouTubeChannelParser.tabParams(homeJson, "playlists")
                if (playlistsParams != null) {
                    runCatching {
                        var seenTokens = mutableSetOf<String>()
                        var json = InnerTubeYouTube.browse(uc, playlistsParams)
                        var parsed = YouTubeChannelParser.parseChannelPage(json)
                        playlists += parsed.playlists
                        playlistsCont = parsed.playlistsContinuation
                        var pages = 1
                        while (playlists.size < 200 && playlistsCont != null && pages < 5) {
                            val token = playlistsCont ?: break
                            if (!seenTokens.add(token)) break
                            val cj = InnerTubeYouTube.browseContinuation(token)
                            val (more, next) = YouTubeChannelParser.parsePlaylistsContinuation(cj)
                            if (more.isEmpty() && next == null) { playlistsCont = null; break }
                            playlists += more
                            playlistsCont = next
                            pages++
                        }
                    }.onFailure { Log.w(TAG, "Playlists tab loop failed: ${it.message}") }
                }
                // FIX PLAYLIST CANAL VAZIA - se aba Playlists voltou 0, usa Home + busca NewPipe antes de devolver vazio.
                if (playlists.isEmpty()) {
                    if (homeParsed.playlists.isNotEmpty()) {
                        playlists += homeParsed.playlists
                        if (playlistsCont == null) playlistsCont = homeParsed.playlistsContinuation
                    }
                }
                if (playlists.isEmpty() && channelName.isNotBlank()) {
                    runCatching {
                        val q = channelName.ifBlank { input.takeIf { it.startsWith("@") }.orEmpty() }
                        if (q.isNotBlank()) {
                            val ex = ServiceList.YouTube.getSearchExtractor(q, listOf("playlists"), null)
                            ex.fetchPage()
                            val collected = parseChannelPlaylistItems(ex.initialPage.items, channelName).toMutableList()
                            var np: Page? = ex.initialPage.nextPage
                            var pg = 0
                            while (collected.size < 100 && np != null && pg < 2) {
                                val nxt = ex.getPage(np!!)
                                collected += parseChannelPlaylistItems(nxt.items, channelName)
                                np = nxt.nextPage
                                pg++
                            }
                            if (collected.isNotEmpty()) {
                                playlists += collected
                                val sess = ChannelPlaylistsSession(ex, np, channelName)
                                channelPlaylistSessions[uc] = sess
                                channelPlaylistSessions[input] = sess
                            }
                        }
                    }.onFailure { Log.w(TAG, "Playlists NewPipe fallback failed: ${it.message}") }
                }
                // Guarda token restante pro loadMore (se bateu o teto, ainda tem mais)
                ytChannelVideoContinuations[uc] = videosCont
                ytChannelVideoContinuations[input] = videosCont
                // FIX PLAYLISTS V2 - param FIXO NewPipe, nao depende de tabParams da Home.
                // tabParams pode vir null / errado e ai a aba nunca era nem buscada.
                runCatching {
                    var jsonV2 = InnerTubeYouTube.browse(uc, "EglwbGF5bGlzdHPyBgQKAkIA")
                    var parsedV2 = YouTubeChannelParser.parseChannelPage(jsonV2)
                    var fixedAll = parsedV2.playlists.distinctBy { it.id }
                    fixedAll = fixedAll.map { if (it.owner.isNullOrBlank() && channelName.isNotBlank()) it.copy(owner = channelName) else it }
                    val idsV2 = playlists.map { it.id }.toSet()
                    playlists += fixedAll.filter { it.id !in idsV2 }
                    var fixedCont: String? = parsedV2.playlistsContinuation ?: InnerTubeYouTube.findContinuationToken(jsonV2)
                    var seenV2 = mutableSetOf<String>()
                    var pagesV2 = 1
                    while (playlists.size < 200 && fixedCont != null && pagesV2 < 5) {
                        val tokenV2 = fixedCont ?: break
                        if (!seenV2.add(tokenV2)) break
                        val cjV2 = InnerTubeYouTube.browseContinuation(tokenV2)
                        val (moreV2, nextV2) = YouTubeChannelParser.parsePlaylistsContinuation(cjV2)
                        if (moreV2.isEmpty() && nextV2 == null) { fixedCont = null; break }
                        val moreFixed = moreV2.map { if (it.owner.isNullOrBlank() && channelName.isNotBlank()) it.copy(owner = channelName) else it }
                        val idsV2b = playlists.map { it.id }.toSet()
                        playlists += moreFixed.filter { it.id !in idsV2b }
                        fixedCont = nextV2
                        pagesV2++
                    }
                    if (fixedCont != null) playlistsCont = fixedCont
                }.onFailure { Log.w(TAG, "Playlists fixed-param failed: ${it.message}") }
                // FIX PLAYLISTS V2 - Home scan: Home as vezes tem prateleira Playlists com lockup.
                if (playlists.isEmpty()) {
                    runCatching {
                        val homeAll = YouTubeChannelParser.parseChannelPage(homeJson).playlists
                        val idsHome = playlists.map { it.id }.toSet()
                        playlists += homeAll.filter { it.id !in idsHome }.map { if (it.owner.isNullOrBlank() && channelName.isNotBlank()) it.copy(owner = channelName) else it }
                    }.onFailure { Log.w(TAG, "Playlists home scan failed: ${it.message}") }
                }
                // FIX PLAYLISTS V2 - busca InnerTube por playlists (usa nosso parser lockup, nao o NewPipe).
                if (playlists.isEmpty() && channelName.isNotBlank()) {
                    runCatching {
                        val searchJson = InnerTubeYouTube.search(channelName, "EgIQAw%3D%3D")
                        val hits = YouTubeSearchParser.parsePlaylists(searchJson)
                        val filtered = hits.map { it.playlist }.filter { pl ->
                            val owner = pl.owner
                            owner.isNullOrBlank() || isSameCreator(owner, channelName)
                        }.map { if (it.owner.isNullOrBlank()) it.copy(owner = channelName) else it }
                        val idsSearch = playlists.map { it.id }.toSet()
                        playlists += filtered.filter { it.id !in idsSearch }
                    }.onFailure { Log.w(TAG, "Playlists InnerTube search failed: ${it.message}") }
                }
                // FIX PLAYLISTS V3 - whole-JSON, ignora aba selecionada. So roda se ainda ta vazio.
                if (playlists.isEmpty()) {
                    runCatching {
                        val jsonV3 = InnerTubeYouTube.browse(uc, "EglwbGF5bGlzdHPyBgQKAkIA")
                        val wholeV3 = YouTubeChannelParser.parsePlaylistsWholeJson(jsonV3, channelName)
                        val idsV3 = playlists.map { it.id }.toSet()
                        playlists += wholeV3.filter { it.id !in idsV3 }
                        Log.w(TAG, "Playlists V3 whole fixed-param: ${wholeV3.size}, total agora ${playlists.size}")
                    }.onFailure { Log.w(TAG, "Playlists V3 whole fixed failed: ${it.message}") }
                }
                if (playlists.isEmpty()) {
                    runCatching {
                        val tpV3 = YouTubeChannelParser.tabParams(homeJson, "playlists")
                        if (tpV3 != null) {
                            val jsonTpV3 = InnerTubeYouTube.browse(uc, tpV3)
                            val wholeTpV3 = YouTubeChannelParser.parsePlaylistsWholeJson(jsonTpV3, channelName)
                            val idsTpV3 = playlists.map { it.id }.toSet()
                            playlists += wholeTpV3.filter { it.id !in idsTpV3 }
                            Log.w(TAG, "Playlists V3 whole tabParams: ${wholeTpV3.size}, total agora ${playlists.size}")
                        } else {
                            Log.w(TAG, "Playlists V3 tabParams veio null pra $channelName")
                        }
                    }.onFailure { Log.w(TAG, "Playlists V3 whole tabParams failed: ${it.message}") }
                }
                if (playlists.isEmpty()) {
                    runCatching {
                        val homeWholeV3 = YouTubeChannelParser.parsePlaylistsWholeJson(homeJson, channelName)
                        val idsHomeV3 = playlists.map { it.id }.toSet()
                        playlists += homeWholeV3.filter { it.id !in idsHomeV3 }
                        Log.w(TAG, "Playlists V3 whole home: ${homeWholeV3.size}, total agora ${playlists.size}")
                    }.onFailure { Log.w(TAG, "Playlists V3 whole home failed: ${it.message}") }
                }
                ytChannelPlaylistContinuations[uc] = playlistsCont
                ytChannelPlaylistContinuations[input] = playlistsCont
                // FIX PLAYLISTS V5 Path1 - debug NO COMECO, mesmo se falhar mostra o erro.
                var debugPlV5 = ""
                if (playlists.isEmpty()) {
                    runCatching {
                        val dbgJson = InnerTubeYouTube.browse(uc, "EglwbGF5bGlzdHPyBgQKAkIA")
                        val dbgWhole = YouTubeChannelParser.parsePlaylistsWholeJson(dbgJson, channelName)
                        val dbgInfo = YouTubeChannelParser.debugLockups(dbgJson)
                        debugPlV5 = "[DEBUG V5 Path1 whole=${dbgWhole.size} $dbgInfo]"
                        Log.w(TAG, "Playlists V5 Path1 $debugPlV5")
                    }.onFailure { debugPlV5 = "[DEBUG V5 Path1 browse falhou: ${it.message}]"; Log.w(TAG, "Playlists V5 Path1 failed: ${it.message}") }
                }
                if (videos.isNotEmpty() || shorts.isNotEmpty() || playlists.isNotEmpty()) {
                    return@withContext ChannelPage(
                        videos = videos.distinctBy { it.id },
                        shorts = shorts.distinctBy { it.id },
                        playlists = playlists.distinctBy { it.id },
                        channelName = channelName.ifBlank { null },
                        avatarUrl = homeParsed.avatarUrl,
                        bannerUrl = homeParsed.bannerUrl,
                        description = if (playlists.isEmpty() && debugPlV5.isNotBlank()) debugPlV5 + "\n\n" + (homeParsed.description ?: "") else homeParsed.description,
                        subscriberCount = homeParsed.subscriberCount,
                        hasMoreVideos = videosCont != null,
                        hasMorePlaylists = playlistsCont != null
                    )
                }
                // Tudo vazio via InnerTube: nao retorna, deixa o NewPipe abaixo tentar.
            }
        }.onFailure { Log.w(TAG, "InnerTube browseChannel failed, fallback NewPipe: ${it.message}") }
        // Normaliza: UC..., @handle, youtube.com/channel/UC, youtube.com/@handle, youtube.com/c/nome
        val channelUrl = when {
            input.startsWith("https://") || input.startsWith("http://") -> input
            input.startsWith("@") -> "https://www.youtube.com/$input"
            input.startsWith("UC") -> "https://www.youtube.com/channel/$input"
            input.startsWith("youtube.com/") -> "https://www.$input"
            else -> "https://www.youtube.com/@$input"
        }
        val header = runCatching {
            val extractor = ServiceList.YouTube.getChannelExtractor(channelUrl)
            extractor.fetchPage()
            ChannelHeader(
                name = runCatching { extractor.name }.getOrNull()?.trim().orEmpty(),
                avatar = runCatching { extractor.avatars?.lastOrNull()?.url }.getOrNull(),
                banner = runCatching { extractor.banners?.lastOrNull()?.url }.getOrNull(),
                description = runCatching { extractor.description }.getOrNull().orEmpty(),
                subs = runCatching { extractor.subscriberCount.takeIf { it > 0 }?.let { formatSubscribers(it) } }.getOrNull(),
            )
        }.getOrNull()
        // Resolve UC real: do header via ChannelExtractor.id, ou do input se ja for UC, ou via uploaderUrl
        val resolvedUc = runCatching {
            val ex = ServiceList.YouTube.getChannelExtractor(channelUrl)
            ex.fetchPage()
            ex.id?.takeIf { it.startsWith("UC") }
        }.getOrNull() ?: Regex("UC[\\w-]{22}").find(input)?.value
        val channelName = header?.name.orEmpty()
        if (header == null && resolvedUc == null) return@withContext null
        val uc = resolvedUc ?: "UC"
        // Videos: fonte 1 = UU, fonte 2 = /videos como playlist, fonte 3 = busca por nome do canal
        var videos: List<Track> = emptyList()
        // FIX18 - Videos: de comeco 300 (paginando o UU, ~100 por pagina). Guarda sessao pra Buscar mais.
        if (uc.startsWith("UC") && uc.length >= 24) {
            videos = runCatching {
                val uploadsId = "UU" + uc.drop(2)
                val pl = ServiceList.YouTube.getPlaylistExtractor("https://www.youtube.com/playlist?list=$uploadsId")
                pl.fetchPage()
                val collected = parseChannelStreamItems(pl.initialPage.items, channelName).toMutableList()
                var nextPage: Page? = pl.initialPage.nextPage
                while (collected.size < 500 && nextPage != null) {
                    val next = pl.getPage(nextPage!!)
                    collected += parseChannelStreamItems(next.items, channelName)
                    nextPage = next.nextPage
                }
                val session = ChannelVideosSession(pl, nextPage, channelName)
                channelVideoSessions[uc] = session
                channelVideoSessions[input] = session
                channelVideoSessions[channelUrl] = session
                collected.distinctBy { it.id }.take(500)
            }.getOrDefault(emptyList())
        }
        if (videos.isEmpty() && uc.startsWith("UC")) {
            videos = runCatching {
                val pl = ServiceList.YouTube.getPlaylistExtractor("https://www.youtube.com/channel/$uc/videos")
                pl.fetchPage()
                pl.initialPage.items.filterIsInstance<StreamInfoItem>().mapNotNull { stream ->
                    val vid = extractVideoId(stream.url) ?: return@mapNotNull null
                    Track(id = vid, title = stream.name ?: "Untitled", artist = channelName, durationSeconds = stream.duration, thumbnailUrl = stream.thumbnails.lastOrNull()?.url, source = Source.YOUTUBE, viewCount = stream.viewCount.takeIf { it > 0 }, publishedText = formatPublished(stream.uploadDate))
                }
            }.getOrDefault(emptyList())
        }
        if (videos.isEmpty() && channelName.isNotBlank()) {
            videos = runCatching {
                val ex = ServiceList.YouTube.getSearchExtractor(channelName, listOf("videos"), null)
                ex.fetchPage()
                parseVideoItems(ex.initialPage.items).map { it.track }.filter { isSameCreator(it.artist, channelName) }.take(20)
            }.getOrDefault(emptyList())
        }
        // Shorts do canal: tenta /shorts como playlist, se falhar busca Shorts pelo nome
        var shorts: List<Track> = emptyList()
        // FIX18 - Shorts: so da aba /shorts (resolucao de Shorts) E com hashtag no titulo.
        // FIX20 - Shorts fora do canal (UI sem aba Shorts). Nao busca /shorts aqui.
        shorts = emptyList()
        // FIX20 - Fallback Shorts removido (canal sem Shorts).
        // Playlists do canal: busca de playlists pelo nome do canal (ou @handle), paginada.
        // Por que NAO PlaylistExtractor em /channel/UC/playlists: ele espera uma URL de playlist (?list=...),
        // nao a aba de playlists do canal, entao sempre voltava vazio. A busca e a fonte de playlists
        // que o NewPipe expoe aqui: devolve as playlists do canal (as principais). Sessao de verdade = Buscar mais funciona.
        var channelPlaylists: List<Playlist> = emptyList()
        // FIX PLAYLISTS V2 - Path 2: tenta aba fixa InnerTube ANTES da busca NewPipe.
        if (uc.startsWith("UC") && uc.length >= 24 && channelName.isNotBlank()) {
            runCatching {
                val tabJson = InnerTubeYouTube.browse(uc, "EglwbGF5bGlzdHPyBgQKAkIA")
                val parsedTab = YouTubeChannelParser.parseChannelPage(tabJson)
                var tabAll = parsedTab.playlists.distinctBy { it.id }
                tabAll = tabAll.map { if (it.owner.isNullOrBlank()) it.copy(owner = channelName) else it }
                if (tabAll.isNotEmpty()) channelPlaylists = tabAll
                val tabCont = parsedTab.playlistsContinuation ?: InnerTubeYouTube.findContinuationToken(tabJson)
                if (tabCont != null) {
                    ytChannelPlaylistContinuations[uc] = tabCont
                    ytChannelPlaylistContinuations[input] = tabCont
                    ytChannelPlaylistContinuations[channelUrl] = tabCont
                }
            }.onFailure { Log.w(TAG, "Playlists Path2 fixed-param failed: ${it.message}") }
        }
        // FIX PLAYLISTS V3 Path2 - whole-JSON, ignora aba selecionada.
        if (channelPlaylists.isEmpty() && uc.startsWith("UC") && uc.length >= 24 && channelName.isNotBlank()) {
            runCatching {
                val tabJsonV3 = InnerTubeYouTube.browse(uc, "EglwbGF5bGlzdHPyBgQKAkIA")
                val wholeV3b = YouTubeChannelParser.parsePlaylistsWholeJson(tabJsonV3, channelName)
                if (wholeV3b.isNotEmpty()) channelPlaylists = wholeV3b
                Log.w(TAG, "Playlists V3 Path2 whole: ${wholeV3b.size}")
            }.onFailure { Log.w(TAG, "Playlists V3 Path2 whole failed: ${it.message}") }
        }
        val playlistQuery = channelName.ifBlank { input.takeIf { it.startsWith("@") }.orEmpty() }
        if (channelPlaylists.isEmpty() && playlistQuery.isNotBlank()) {
            channelPlaylists = runCatching {
                val extractor = ServiceList.YouTube.getSearchExtractor(playlistQuery, listOf("playlists"), null)
                extractor.fetchPage()
                val collected = parseChannelPlaylistItems(extractor.initialPage.items, channelName).toMutableList()
                var nextPage: Page? = extractor.initialPage.nextPage
                var pages = 0
                while (collected.size < 500 && nextPage != null && pages < 5) {
                    val next = extractor.getPage(nextPage!!)
                    collected += parseChannelPlaylistItems(next.items, channelName)
                    nextPage = next.nextPage
                    pages++
                }
                val session = ChannelPlaylistsSession(extractor, nextPage, channelName)
                channelPlaylistSessions[uc] = session
                channelPlaylistSessions[input] = session
                channelPlaylistSessions[channelUrl] = session
                collected.distinctBy { it.id }.take(500)
            }.getOrDefault(emptyList())
        }
        // FIX PLAYLISTS V5 Path2 - debug NO COMECO, cobre quando cai no NewPipe.
        var debugPlV5Path2 = ""
        if (channelPlaylists.isEmpty() && uc.startsWith("UC") && uc.length >= 24 && channelName.isNotBlank()) {
            runCatching {
                val dbgJson2 = InnerTubeYouTube.browse(uc, "EglwbGF5bGlzdHPyBgQKAkIA")
                val dbgWhole2 = YouTubeChannelParser.parsePlaylistsWholeJson(dbgJson2, channelName)
                val dbgInfo2 = YouTubeChannelParser.debugLockups(dbgJson2)
                debugPlV5Path2 = "[DEBUG V5 Path2 whole=${dbgWhole2.size} $dbgInfo2]"
                Log.w(TAG, "Playlists V5 Path2 $debugPlV5Path2")
            }.onFailure { debugPlV5Path2 = "[DEBUG V5 Path2 browse falhou: ${it.message}]"; Log.w(TAG, "Playlists V5 Path2 failed: ${it.message}") }
        }
        ChannelPage(
            videos = videos,
            shorts = shorts,
            playlists = channelPlaylists,
            channelName = channelName.takeIf { it.isNotBlank() },
            avatarUrl = header?.avatar,
            bannerUrl = header?.banner,
            description = if (channelPlaylists.isEmpty() && debugPlV5Path2.isNotBlank()) debugPlV5Path2 + "\n\n" + (header?.description ?: "") else header?.description?.takeIf { it.isNotBlank() },
            subscriberCount = header?.subs,
            hasMoreVideos = hasMoreChannelVideos(uc),
            hasMorePlaylists = hasMoreChannelPlaylists(uc),
        )
    }


    private fun parseChannelStreams(items: List<*>, channelName: String): List<Track> {
        return items.filterIsInstance<StreamInfoItem>().mapNotNull { stream ->
            val vid = extractVideoId(stream.url) ?: return@mapNotNull null
            Track(id = vid, title = stream.name ?: "Untitled", artist = channelName, durationSeconds = stream.duration, thumbnailUrl = stream.thumbnails.lastOrNull()?.url, source = Source.YOUTUBE, viewCount = stream.viewCount.takeIf { it > 0 }, publishedText = formatPublished(stream.uploadDate))
        }
    }
    private fun parseChannelPlaylists(items: List<*>, channelName: String): List<Playlist> {
        return items.filterIsInstance<PlaylistInfoItem>().mapNotNull { item ->
            val pid = Regex("list=([^&]+)").find(item.url ?: "")?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Playlist(id = pid, title = item.name ?: "Playlist", owner = channelName, thumbnailUrl = item.thumbnails.lastOrNull()?.url, trackCount = item.streamCount.takeIf { it > 0 }?.toInt(), source = Source.YOUTUBE)
        }
    }
    private fun resolveUc(input: String): String? {
        val raw = input.trim()
        if (raw.startsWith("UU") && raw.length >= 24) return "UC" + raw.drop(2)
        return Regex("UC[\\w-]{22}").find(raw)?.value
    }

    /** Carrega mais videos do canal: avanca ate 5 paginas da UU por chamada. Retorna SO os novos. */
    /** Carrega mais playlists do canal: avanca ate 3 paginas por chamada. Retorna SO as novas. */
    /** "hoje", "ha 1 dia", "ha 3 semanas", "ha 5 meses", "ha 2 anos" a partir do uploadDate do NewPipe. */
    fun formatPublished(uploadDate: DateWrapper?): String? {
        val odt = runCatching { uploadDate?.offsetDateTime() }.getOrNull() ?: return null
        val now = OffsetDateTime.now()
        val days = ChronoUnit.DAYS.between(odt, now)
        if (days < 0) return null
        return when {
            days == 0L -> "hoje"
            days == 1L -> "ha 1 dia"
            days < 7 -> "ha $days dias"
            days < 30 -> { val w = days / 7; if (w == 1L) "ha 1 semana" else "ha $w semanas" }
            days < 365 -> { val m = days / 30; if (m == 1L) "ha 1 mes" else "ha $m meses" }
            else -> { val y = days / 365; if (y == 1L) "ha 1 ano" else "ha $y anos" }
        }
    }

    /** Faixas de uma playlist do YouTube (PL..., UU..., OL...). Usado no menu da playlist do canal. */
    suspend fun getYouTubePlaylistTracks(playlistId: String): List<Track> = withContext(Dispatchers.IO) {

        // PART 4 - InnerTube first for getYouTubePlaylistTracks
        runCatching {
            val raw4t = playlistId.trim().removePrefix("YT:").trim()
            val listId4t = if (raw4t.startsWith("http")) {
                Regex("[?&]list=([^&]+)").find(raw4t)?.groupValues?.getOrNull(1) ?: raw4t
            } else raw4t
            if (listId4t.isNotBlank() && !listId4t.startsWith("OLAK") && !listId4t.startsWith("RD")) {
                val browseId4t = if (listId4t.startsWith("VL")) listId4t else "VL" + listId4t
                val json4t = InnerTubeYouTube.browse(browseId4t)
                val parsed4t = YouTubePlaylistParser.parsePlaylistPage(json4t)
                val all4t = parsed4t.tracks.toMutableList()
                var cont4t = parsed4t.continuation
                var pages4t = 0
                while (all4t.size < 500 && cont4t != null && pages4t < 10) {
                    val cj = InnerTubeYouTube.browseContinuation(cont4t)
                    val (more, next) = YouTubePlaylistParser.parsePlaylistContinuation(cj)
                    all4t += more
                    cont4t = next
                    pages4t++
                }
                if (all4t.isNotEmpty()) return@withContext all4t.distinctBy { it.id }
            }
        }.onFailure { Log.w(TAG, "InnerTube getYouTubePlaylistTracks failed, fallback NewPipe: ${it.message}") }

        if (!NewPipeInitializer.ensureInitialized(downloader)) return@withContext emptyList()
        val url = if (playlistId.startsWith("http")) playlistId else "https://www.youtube.com/playlist?list=$playlistId"
        runCatching {
            val pl = ServiceList.YouTube.getPlaylistExtractor(url)
            pl.fetchPage()
            val all = pl.initialPage.items.filterIsInstance<StreamInfoItem>().mapNotNull { stream ->
                val vid = extractVideoId(stream.url) ?: return@mapNotNull null
                Track(id = vid, title = stream.name ?: "Untitled", artist = stream.uploaderName ?: "", durationSeconds = stream.duration, thumbnailUrl = stream.thumbnails.lastOrNull()?.url, source = Source.YOUTUBE, viewCount = stream.viewCount.takeIf { it > 0 }, publishedText = formatPublished(stream.uploadDate))
            }.toMutableList()
            var nextPl = pl.initialPage.nextPage
            var pagesPl = 1
            while (nextPl != null && pagesPl < 10) {
                val page = runCatching { pl.getPage(nextPl) }.getOrNull() ?: break
                all += page.items.filterIsInstance<StreamInfoItem>().mapNotNull { stream ->
                    val vid = extractVideoId(stream.url) ?: return@mapNotNull null
                    Track(id = vid, title = stream.name ?: "Untitled", artist = stream.uploaderName ?: "", durationSeconds = stream.duration, thumbnailUrl = stream.thumbnails.lastOrNull()?.url, source = Source.YOUTUBE, viewCount = stream.viewCount.takeIf { it > 0 }, publishedText = formatPublished(stream.uploadDate))
                }
                nextPl = page.nextPage
                pagesPl++
            }
            all.distinctBy { it.id }
        }.getOrDefault(emptyList())
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
