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
                VideoPage(parseVideoItems(activeVideoSearch!!.initialPage.items).take(pageSize), lastVideoPage != null)
            } else {
                val extractor = activeVideoSearch ?: return@runCatching VideoPage(emptyList(), false)
                val page = lastVideoPage ?: return@runCatching VideoPage(emptyList(), false)
                val next = extractor.getPage(page)
                lastVideoPage = next.nextPage
                VideoPage(parseVideoItems(next.items).take(pageSize), lastVideoPage != null)
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
                parsePlaylistItems(activePlaylistSearch!!.initialPage.items).take(pageSize)
            } else {
                val extractor = activePlaylistSearch ?: return@runCatching emptyList<SearchHit.PlaylistHit>()
                val page = lastPlaylistPage ?: return@runCatching emptyList<SearchHit.PlaylistHit>()
                val next = extractor.getPage(page)
                lastPlaylistPage = next.nextPage
                parsePlaylistItems(next.items).take(pageSize)
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Resolve a YouTube video ID (non-music) to an audio URL.
     * Same strategy as VzMusic's YouTubeRepository.getAudioStreamUrl.
     */
    suspend fun getStreamUrl(
        videoId: String,
        titleHint: String? = null,
        artistHint: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        // 1) www.youtube.com first.
        runCatching {
            extractFromUrl("https://www.youtube.com/watch?v=$videoId")
        }.getOrNull()?.let { return@withContext it }

        // 2) music.youtube.com fallback.
        runCatching {
            extractFromUrl("https://music.youtube.com/watch?v=$videoId")
        }.getOrNull()?.let { return@withContext it }

        // 3) Search for a different playable video with the same title.
        if (!titleHint.isNullOrBlank()) {
            val altId = runCatching { findBestMatchVideoId(titleHint, artistHint) }.getOrNull()
            if (altId != null && altId != videoId) {
                runCatching {
                    extractFromUrl("https://www.youtube.com/watch?v=$altId")
                }.getOrNull()?.let { return@withContext it }
            }
        }

        null
    }

    private fun extractFromUrl(url: String): String {
        val extractor = ServiceList.YouTube.getStreamExtractor(url)
        extractor.fetchPage()

        val audio: AudioStream? = extractor.audioStreams
            .filter { it.averageBitrate > 0 }
            .maxByOrNull { it.averageBitrate }
            ?: extractor.audioStreams.firstOrNull()
        if (audio?.content?.isNotBlank() == true) return audio.content

        extractor.hlsUrl?.takeIf { it.isNotBlank() }?.let { return it }

        val progressive = extractor.videoStreams
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