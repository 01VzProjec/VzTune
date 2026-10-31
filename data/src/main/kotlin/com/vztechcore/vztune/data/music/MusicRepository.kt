package com.vztechcore.vztune.data.music

import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchFilter
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.core.domain.search.SearchResult
import com.vztechcore.vztune.core.domain.search.SearchSuggestions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Facade over YouTube + YT Music repositories.
 * Aggregates both sources into a unified list of SearchHit.
 *
 * TODO: when YTMusicRepository (InnerTube) is added, this class fans out
 *       to both and interleaves results.
 * Currently returns stub data so the UI pipeline can be verified.
 */
@Singleton
class MusicRepository @Inject constructor() {

    suspend fun search(query: String, filter: SearchFilter): SearchResult =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext SearchResult.Success(emptyList())

            val hits = mutableListOf<SearchHit>()

            if (filter == SearchFilter.ALL || filter == SearchFilter.SONGS) {
                hits += SearchHit.SongHit(
                    Track(
                        id = "stub-ytm-song",
                        title = "[STUB YTM] Song for \"$query\"",
                        artist = "VzTune Stub",
                        durationSeconds = 200L,
                        source = Source.YOUTUBE_MUSIC,
                    )
                )
            }

            if (filter == SearchFilter.ALL || filter == SearchFilter.VIDEOS) {
                hits += SearchHit.VideoHit(
                    Track(
                        id = "stub-yt-video",
                        title = "[STUB YT] Video for \"$query\"",
                        artist = "VzTune Stub",
                        durationSeconds = 245L,
                        source = Source.YOUTUBE,
                    )
                )
            }

            SearchResult.Success(hits)
        }

    suspend fun suggestions(query: String): SearchSuggestions =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext SearchSuggestions()
            SearchSuggestions(queries = listOf("$query mix", "$query live"))
        }
}