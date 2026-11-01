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

    // ================= SEARCH SESSION STATE =================

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

    // ================= SEARCH =================

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

    // ================= STREAM URL RESOLUTION =================

    /**
     * Resolve a playable audio URL.
     * Mirrors the old YouTubeMusicNewPipeRepository that worked in VzMusic:
     *
     *   1. Try www.youtube.com first (most reliable with NewPipe).
     *   2. Fallback to music.youtube.com.
     *   3. Last resort: search for a different playable video with the same title.
     *
     * Uses getStreamExtractor + fetchPage — NOT StreamInfo.getInfo — because
     * that's the exact path that worked in the old project for both regular
     * YouTube videos and YouTube Music song IDs.
     *
     * The `source` parameter is kept for future source-aware tweaks; today
     * both endpoints return the same audio-only streams (ATV) when the
     * videoId refers to a song.
     */
    suspend fun getStreamUrl(
        videoId: String,
        titleHint: String? = null,
        artistHint: String? = null,
        source: Source,
    ): String? = withContext(Dispatchers.IO) {
        // 1) www.youtube.com first.
        val fromWww = runCatching {
            extractFromUrl("https://www.youtube.com/watch?v=$videoId")
        }.getOrNull()
        if (!fromWww.isNullOrBlank()) return@withContext fromWww

        // 2) music.youtube.com fallback.
        val fromMusic = runCatching {
            extractFromUrl("https://music.youtube.com/watch?v=$videoId")
        }.getOrNull()
        if (!fromMusic.isNullOrBlank()) return@withContext fromMusic

        // 3) Search for an alternative playable video with the same title.
        if (!titleHint.isNullOrBlank()) {
            val altId = runCatching { findBestMatchVideoId(titleHint, artistHint) }.getOrNull()
            if (altId != null && altId != videoId) {
                val fromAlt = runCatching {
                    extractFromUrl("https://www.youtube.com/watch?v=$altId")
                }.getOrNull()
                if (!fromAlt.isNullOrBlank()) return@withContext fromAlt
            }
        }

        null
    }

    /**
     * Exact strategy from the old YouTubeMusicNewPipeRepository.extractFrom():
     *   1. best pure-audio stream (highest bitrate)
     *   2. HLS manifest
     *   3. lowest-resolution progressive (audio+video) as last resort
     */
    private fun extractFromUrl(url: String): String {
        val extractor = ServiceList.YouTube.getStreamExtractor(url)
        extractor.fetchPage()

        // 1) Best pure-audio stream.
        val audioStreams = extractor.audioStreams
        val bestAudio: AudioStream? = audioStreams
            .filter { it.averageBitrate > 0 }
            .maxByOrNull { it.averageBitrate }
            ?: audioStreams.firstOrNull()

        if (bestAudio?.content?.isNotBlank() == true) {
            return bestAudio.content
        }

        // 2) HLS.
        extractor.hlsUrl?.takeIf { it.isNotBlank() }?.let { return it }

        // 3) Progressive video (audio + video), lowest resolution.
        val progressive: VideoStream? = extractor.videoStreams
            .filter { !it.isVideoOnly }
            .minByOrNull {
                it.resolution.removeSuffix("p").toIntOrNull() ?: Int.MAX_VALUE
            }

        if (progressive?.content?.isNotBlank() == true) {
            return progressive.content
        }

        throw Exception("Nenhum stream disponível para $url")
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

    // ================= PARSERS =================

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

    // ================= HELPERS =================

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