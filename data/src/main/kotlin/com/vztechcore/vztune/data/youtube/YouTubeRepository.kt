package com.vztechcore.vztune.data.youtube

import android.net.Uri
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
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeRepository @Inject constructor(
    downloader: NewPipeDownloader,
) {
    init {
        NewPipeInitializer.ensureInitialized(downloader)
    }

    private var activeVideoSearch: SearchExtractor? = null
    private var lastVideoPage: Page? = null

    private var activePlaylistSearch: SearchExtractor? = null
    private var lastPlaylistPage: Page? = null

    fun resetSession() {
        activeVideoSearch = null
        lastVideoPage = null
        activePlaylistSearch = null
        lastPlaylistPage = null
    }

    data class VideoPage(
        val items: List<SearchHit.VideoHit>,
        val hasMore: Boolean,
    )

    suspend fun searchVideos(
        query: String,
        loadMore: Boolean,
        pageSize: Int = 20,
    ): VideoPage = withContext(Dispatchers.IO) {
        runCatching {
            if (!loadMore) {
                activeVideoSearch = ServiceList.YouTube.getSearchExtractor(query)
                activeVideoSearch!!.fetchPage()
                lastVideoPage = activeVideoSearch!!.initialPage.nextPage
                val items = parseVideoItems(activeVideoSearch!!.initialPage.items)
                    .take(pageSize)
                VideoPage(items, lastVideoPage != null)
            } else {
                val extractor = activeVideoSearch
                    ?: return@runCatching VideoPage(emptyList(), false)
                val page = lastVideoPage
                    ?: return@runCatching VideoPage(emptyList(), false)
                val next = extractor.getPage(page)
                lastVideoPage = next.nextPage
                val items = parseVideoItems(next.items).take(pageSize)
                VideoPage(items, lastVideoPage != null)
            }
        }.getOrDefault(VideoPage(emptyList(), false))
    }

    suspend fun searchPlaylists(
        query: String,
        loadMore: Boolean,
        pageSize: Int = 30,
    ): List<SearchHit.PlaylistHit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!loadMore) {
                activePlaylistSearch = ServiceList.YouTube.getSearchExtractor(
                    query, listOf("playlists"), null,
                )
                activePlaylistSearch!!.fetchPage()
                lastPlaylistPage = activePlaylistSearch!!.initialPage.nextPage
                parsePlaylistItems(activePlaylistSearch!!.initialPage.items)
                    .take(pageSize)
            } else {
                val extractor = activePlaylistSearch
                    ?: return@runCatching emptyList<SearchHit.PlaylistHit>()
                val page = lastPlaylistPage
                    ?: return@runCatching emptyList<SearchHit.PlaylistHit>()
                val next = extractor.getPage(page)
                lastPlaylistPage = next.nextPage
                parsePlaylistItems(next.items).take(pageSize)
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Resolve a playable audio URL for a given track.
     *
     * SOURCE-AWARE: the URL we ask for matches the `source` of the track.
     *  - YOUTUBE       -> youtube.com (OMV / UGC video version)
     *  - YOUTUBE_MUSIC -> music.youtube.com (ATV audio-only version)
     *
     * Why: the SAME videoId can serve different content depending on the
     * URL. Asking youtube.com for a YT Music track may return the music
     * video (OMV) instead of the album audio (ATV) — causing the "song
     * plays but it's the video version" conflict.
     *
     * Uses StreamInfo.getInfo() — NOT getStreamExtractor() — because only
     * getInfo() decodes the `n` cipher that YouTube injects into googlevideo
     * URLs. Without it, Google throttles the download to 0 bytes.
     */
    suspend fun getStreamUrl(
        videoId: String,
        titleHint: String? = null,
        artistHint: String? = null,
        source: Source,
    ): String? = withContext(Dispatchers.IO) {
        // 1) Try the source-matching URL first.
        val primaryUrl = when (source) {
            Source.YOUTUBE -> "https://www.youtube.com/watch?v=$videoId"
            Source.YOUTUBE_MUSIC -> "https://music.youtube.com/watch?v=$videoId"
        }
        runCatching { extractFromUrl(primaryUrl) }.getOrNull()?.let { return@withContext it }

        // 2) Fallback to the other endpoint.
        val fallbackUrl = when (source) {
            Source.YOUTUBE -> "https://music.youtube.com/watch?v=$videoId"
            Source.YOUTUBE_MUSIC -> "https://www.youtube.com/watch?v=$videoId"
        }
        runCatching { extractFromUrl(fallbackUrl) }.getOrNull()?.let { return@withContext it }

        // 3) Last resort: search for a different playable video with the same title.
        if (!titleHint.isNullOrBlank()) {
            val altId = runCatching { findBestMatchVideoId(titleHint, artistHint) }.getOrNull()
            if (altId != null && altId != videoId) {
                val altUrl = when (source) {
                    Source.YOUTUBE -> "https://www.youtube.com/watch?v=$altId"
                    Source.YOUTUBE_MUSIC -> "https://music.youtube.com/watch?v=$altId"
                }
                runCatching { extractFromUrl(altUrl) }.getOrNull()
                    ?.let { return@withContext it }
            }
        }

        null
    }

    private fun extractFromUrl(url: String): String {
        // StreamInfo.getInfo resolves cipher + `n` + signature.
        val info = StreamInfo.getInfo(ServiceList.YouTube, url)

        // 1) Prefer audio-only streams, highest bitrate, mp4 (AAC) first.
        val audioStreams: List<AudioStream> = info.audioStreams
            .filter { it.averageBitrate > 0 }

        val bestMp4 = audioStreams
            .filter { it.getFormat()?.mimeType?.contains("mp4", ignoreCase = true) == true }
            .maxByOrNull { it.averageBitrate }

        if (bestMp4?.content?.isNotBlank() == true) return bestMp4.content

        // 1b) Any audio stream (webm/opus also works on ExoPlayer).
        val bestAudio = audioStreams.maxByOrNull { it.averageBitrate }
        if (bestAudio?.content?.isNotBlank() == true) return bestAudio.content

        // 2) Progressive video (audio + video in one container).
        val progressive: VideoStream? = info.videoStreams
            .filter { !it.isVideoOnly }
            .minByOrNull {
                it.resolution.removeSuffix("p").toIntOrNull() ?: Int.MAX_VALUE
            }
        if (progressive?.content?.isNotBlank() == true) return progressive.content

        throw Exception("Sem streams para $url")
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

    private fun parseVideoItems(items: List<InfoItem>): List<SearchHit.VideoHit> {
        return items.filterIsInstance<StreamInfoItem>().mapNotNull { stream ->
            val videoId = extractVideoId(stream.url) ?: return@mapNotNull null
            SearchHit.VideoHit(
                Track(
                    id = videoId,
                    title = stream.name ?: "Sem título",
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
}