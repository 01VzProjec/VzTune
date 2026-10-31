package com.vztechcore.vztune.data.youtube

import android.net.Uri
import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
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

    suspend fun searchVideos(query: String): List<SearchHit.VideoHit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val extractor = ServiceList.YouTube.getSearchExtractor(query)
                extractor.fetchPage()
                extractor.initialPage.items
                    .filterIsInstance<StreamInfoItem>()
                    .mapNotNull { stream ->
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
            }.getOrDefault(emptyList())
        }

    suspend fun searchPlaylists(query: String): List<SearchHit.PlaylistHit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val extractor = ServiceList.YouTube.getSearchExtractor(
                    query, listOf("playlists"), null
                )
                extractor.fetchPage()
                extractor.initialPage.items
                    .filterIsInstance<PlaylistInfoItem>()
                    .mapNotNull { playlist ->
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
            }.getOrDefault(emptyList())
        }

    suspend fun getStreamUrl(
        videoId: String,
        titleHint: String? = null,
        artistHint: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        // Try direct extraction
        runCatching { extractStreamUrl(videoId) }.getOrNull()?.let { return@withContext it }

        // Fallback: search for a playable alternative
        if (!titleHint.isNullOrBlank()) {
            val altId = runCatching { findBestMatchVideoId(titleHint, artistHint) }.getOrNull()
            if (altId != null && altId != videoId) {
                runCatching { extractStreamUrl(altId) }.getOrNull()?.let { return@withContext it }
            }
        }
        null
    }

    private fun extractStreamUrl(videoId: String): String {
        runCatching {
            extractFromUrl("https://www.youtube.com/watch?v=$videoId")
        }.getOrNull()?.let { return it }
        return extractFromUrl("https://music.youtube.com/watch?v=$videoId")
    }

    private fun extractFromUrl(url: String): String {
        val extractor = ServiceList.YouTube.getStreamExtractor(url)
        extractor.fetchPage()

        val audio = extractor.audioStreams
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