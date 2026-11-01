package com.vztechcore.vztune.data.music

import android.util.Log
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchFilter
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.core.domain.search.SearchResult
import com.vztechcore.vztune.core.domain.search.SearchSuggestions
import com.vztechcore.vztune.data.youtube.YouTubeMusicNewPipeRepository
import com.vztechcore.vztune.data.youtube.YouTubeRepository
import com.vztechcore.vztune.data.ytmusic.YTMusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Facade over YouTube + YT Music.
 *
 *   - SEARCH:    delegates to both repositories, interleaves results,
 *                filters duplicates across pagination pages.
 *   - PLAYBACK:  always uses NewPipe (decodes the `n` cipher that InnerTube
 *                does not), but picks the right endpoint based on `source`:
 *                  YOUTUBE_MUSIC -> YouTubeMusicNewPipeRepository
 *                  YOUTUBE       -> YouTubeRepository.getStreamUrl
 *
 * `YTMusicRepository.search()` already enriches hits with real artist names
 * (via the player endpoint + caches), so this class does NOT do any extra
 * enrichment — it only merges and filters.
 */
@Singleton
class MusicRepository @Inject constructor(
    private val youTubeRepository: YouTubeRepository,
    private val ytMusicRepository: YTMusicRepository,
    private val youTubeMusicNewPipeRepository: YouTubeMusicNewPipeRepository,
) {

    // ================= PAGINATION STATE =================

    private data class SearchState(
        val seenKeys: MutableSet<String> = mutableSetOf(),
        var ytHasMore: Boolean = true,
        var ytmContinuation: String? = null,
        var ytmHasMore: Boolean = true,
    )

    private val searchStates = mutableMapOf<String, SearchState>()

    // ================= SEARCH =================

    suspend fun search(
        query: String,
        filter: SearchFilter,
        loadMore: Boolean,
    ): SearchResult = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            return@withContext SearchResult.Success(emptyList(), false)
        }

        val stateKey = "$query|${filter.name}"
        val state = searchStates.getOrPut(stateKey) { SearchState() }

        if (!loadMore) {
            state.seenKeys.clear()
            state.ytHasMore = true
            state.ytmContinuation = null
            state.ytmHasMore = true
            youTubeRepository.resetSession()
        }

        val hits = mutableListOf<SearchHit>()
        var hasMore: Boolean

        when (filter) {
            SearchFilter.ALL -> {
                val ytPageSize = if (loadMore) 15 else 20
                val ytmPageSize = if (loadMore) 15 else 20

                val ytPage = youTubeRepository.searchVideos(
                    query = query,
                    loadMore = loadMore,
                    pageSize = ytPageSize,
                )
                val ytmPage = ytMusicRepository.search(
                    query = query,
                    filter = filter,
                    continuation = state.ytmContinuation,
                )

                hits += interleave(ytPage.items, ytmPage.items.take(ytmPageSize))

                state.ytHasMore = ytPage.hasMore
                state.ytmContinuation = ytmPage.continuation
                state.ytmHasMore = ytmPage.continuation != null
                hasMore = state.ytHasMore || state.ytmHasMore
            }

            SearchFilter.VIDEOS -> {
                val ytPage = youTubeRepository.searchVideos(
                    query = query,
                    loadMore = loadMore,
                    pageSize = 30,
                )
                hits += ytPage.items
                state.ytHasMore = ytPage.hasMore
                hasMore = state.ytHasMore
            }

            SearchFilter.SONGS -> {
                val ytmPage = ytMusicRepository.search(
                    query = query,
                    filter = filter,
                    continuation = state.ytmContinuation,
                )
                hits += ytmPage.items.take(30)
                state.ytmContinuation = ytmPage.continuation
                state.ytmHasMore = ytmPage.continuation != null
                hasMore = state.ytmHasMore
            }

            SearchFilter.PLAYLISTS -> {
                if (!loadMore) {
                    hits += youTubeRepository.searchPlaylists(query, loadMore = false)
                }
                val ytmPage = ytMusicRepository.search(
                    query = query,
                    filter = filter,
                    continuation = state.ytmContinuation,
                )
                hits += ytmPage.items
                state.ytmContinuation = ytmPage.continuation
                state.ytmHasMore = ytmPage.continuation != null
                hasMore = state.ytmHasMore
            }

            else -> {
                val ytmPage = ytMusicRepository.search(
                    query = query,
                    filter = filter,
                    continuation = state.ytmContinuation,
                )
                hits += ytmPage.items
                state.ytmContinuation = ytmPage.continuation
                state.ytmHasMore = ytmPage.continuation != null
                hasMore = state.ytmHasMore
            }
        }

        val newHits = hits.filter { state.seenKeys.add(it.stableKey) }
        SearchResult.Success(newHits, hasMore)
    }

    suspend fun suggestions(query: String): SearchSuggestions =
        withContext(Dispatchers.IO) {
            runCatching { ytMusicRepository.suggestions(query) }
                .getOrDefault(SearchSuggestions())
        }

    // ================= PLAYBACK =================

    /**
     * Resolve a playable audio URL.
     *
     * Always NewPipe (it decodes the `n` cipher that InnerTube does NOT),
     * but the endpoint matches the source:
     *   - YOUTUBE_MUSIC -> YouTubeMusicNewPipeRepository (tries music.youtube.com)
     *   - YOUTUBE       -> YouTubeRepository.getStreamUrl  (tries youtube.com)
     */
    suspend fun getStreamUrl(track: Track): String? = withContext(Dispatchers.IO) {
        when (track.source) {
            Source.YOUTUBE_MUSIC -> runCatching {
                youTubeMusicNewPipeRepository.getAudioStreamUrl(track.id)
            }.getOrElse { e ->
                Log.w(TAG, "YTM stream failed for ${track.id}: ${e.message}")
                null
            }

            Source.YOUTUBE -> youTubeRepository.getStreamUrl(
                videoId = track.id,
                titleHint = track.title,
                artistHint = track.artist,
            )
        }
    }

    // ================= HELPERS =================

    /**
     * Interleaves two lists, preserving the order of each.
     * Example: [a,b,c] + [x,y] -> [a,x,b,y,c]
     */
    private fun <T> interleave(a: List<T>, b: List<T>): List<T> {
        val out = mutableListOf<T>()
        val max = maxOf(a.size, b.size)
        for (i in 0 until max) {
            a.getOrNull(i)?.let { out += it }
            b.getOrNull(i)?.let { out += it }
        }
        return out
    }

    private companion object {
        const val TAG = "VzTuneMusic"
    }
}