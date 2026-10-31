package com.vztechcore.vztune.data.youtube

import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube search via NewPipeExtractor.
 *
 * TODO(youtube): wire to NewPipeExtractor v0.24.8.
 *   Flow: NewPipe.init(downloader) once -> getService(YouTube) -> SearchExtractor(service, query, filters)
 *   -> fetchPage() -> initialPage.items
 *   Needs NewPipeDownloaderImpl (see upcoming file when we wire it for real).
 * Currently returns a stubbed Success so the UI/data pipeline can be verified end-to-end.
 */
@Singleton
class YouTubeRepository @Inject constructor() {

    suspend fun search(query: String, tabName: String): SearchResult = withContext(Dispatchers.IO) {
        // STUB — remove when NewPipe is integrated.
        if (query.isBlank()) return@withContext SearchResult.Success()

        SearchResult.Success(
            videos = listOf(
                Track(
                    id = "stub-v1",
                    title = "[STUB] Video result for \"$query\"",
                    artist = "VzTune Stub",
                    durationSeconds = 215L,
                    source = Source.YOUTUBE,
                ),
            ),
            tracks = listOf(
                Track(
                    id = "stub-t1",
                    title = "[STUB] Song result for \"$query\"",
                    artist = "VzTune Stub",
                    durationSeconds = 180L,
                    source = Source.YOUTUBE,
                ),
            ),
            playlists = emptyList(),
        )
    }
}