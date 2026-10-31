package com.vztechcore.vztune.data.youtube

import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.core.domain.search.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeRepository @Inject constructor(
    private val downloader: NewPipeDownloaderImpl,
) {
    @Volatile private var initialized = false

    private fun ensureInit() {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    NewPipe.init(downloader)
                    initialized = true
                }
            }
        }
    }

    /** Search for videos. Returns a list of SearchHit.VideoHit. */
    suspend fun searchVideos(query: String): SearchResult = withContext(Dispatchers.IO) {
        ensureInit()
        try {
            val service = ServiceList.YouTube
            val extractor = SearchExtractor(service, query)
            extractor.fetchPage()
            val items = extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .map { item ->
                    SearchHit.VideoHit(
                        Track(
                            id = item.url.substringAfter("v="),
                            title = item.name,
                            artist = item.uploaderName ?: "Unknown",
                            durationSeconds = item.duration,
                            thumbnailUrl = item.thumbnails.firstOrNull()?.url,
                            source = Source.YOUTUBE,
                        )
                    )
                }
            SearchResult.Success(items)
        } catch (e: Exception) {
            SearchResult.Failure("YouTube search failed: ${e.message}")
        }
    }

    /** Resolve a playable stream URL for a given video ID. */
    suspend fun getStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        ensureInit()
        try {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val info = StreamInfo.getInfo(ServiceList.YouTube, url)
            // Prefer audio-only streams (music). Fall back to progressive video+audio.
            val audio: AudioStream? = info.audioStreams
                .sortedByDescending { it.averageBitrate }
                .firstOrNull()
            if (audio != null) return@withContext audio.content
            val progressive: VideoStream? = info.videoStreams
                .sortedByDescending { it.resolution }
                .firstOrNull()
            progressive?.content
        } catch (e: Exception) {
            null
        }
    }
}