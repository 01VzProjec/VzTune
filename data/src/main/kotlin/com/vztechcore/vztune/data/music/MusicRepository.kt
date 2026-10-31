package com.vztechcore.vztune.data.music

import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchFilter
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.core.domain.search.SearchResult
import com.vztechcore.vztune.core.domain.search.SearchSuggestions
import com.vztechcore.vztune.data.youtube.YouTubeRepository
import com.vztechcore.vztune.data.ytmusic.YTMusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val youTubeRepository: YouTubeRepository,
    private val ytMusicRepository: YTMusicRepository,
) {

    private data class SearchState(
        val seenKeys: MutableSet<String> = mutableSetOf(),
        var ytHasMore: Boolean = true,
        var ytmContinuation: String? = null,
        var ytmHasMore: Boolean = true,
    )

    private val searchStates = mutableMapOf<String, SearchState>()

    suspend fun search(
        query: String,
        filter: SearchFilter,
        loadMore: Boolean,
    ): SearchResult = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext SearchResult.Success(emptyList(), false)

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

                hits += ytPage.items
                hits += ytmPage.items.take(ytmPageSize)

                state.ytHasMore = ytPage.hasMore
                state.ytmContinuation = ytmPage.continuation
                state.ytmHasMore = ytmPage.continuation != null

                hasMore = state.ytHasMore || state.ytmHasMore
            }

            SearchFilter.VIDEOS -> {
                val pageSize = 30
                val ytPage = youTubeRepository.searchVideos(
                    query = query,
                    loadMore = loadMore,
                    pageSize = pageSize,
                )
                hits += ytPage.items
                state.ytHasMore = ytPage.hasMore
                hasMore = state.ytHasMore
            }

            SearchFilter.SONGS -> {
                val pageSize = 30
                val ytmPage = ytMusicRepository.search(
                    query = query,
                    filter = filter,
                    continuation = state.ytmContinuation,
                )
                hits += ytmPage.items.take(pageSize)
                state.ytmContinuation = ytmPage.continuation
                state.ytmHasMore = ytmPage.continuation != null
                hasMore = state.ytmHasMore
            }

            SearchFilter.PLAYLISTS -> {
                if (!loadMore) {
                    hits += youTubeRepository.searchPlaylists(
                        query = query,
                        loadMore = false,
                    )
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

    /**
     * Resolve a playable audio URL.
     * Strategy for YOUTUBE_MUSIC: NewPipe first (returns direct googlevideo URL
     * with User-Agent, works with ExoPlayer out of the box). Fall back to
     * InnerTube's player response only if NewPipe fails.
     * Strategy for YOUTUBE: NewPipe direct.
     */
    suspend fun getStreamUrl(track: Track): String? = withContext(Dispatchers.IO) {
        when (track.source) {
            Source.YOUTUBE -> youTubeRepository.getStreamUrl(
                track.id, track.title, track.artist,
            )
            Source.YOUTUBE_MUSIC -> youTubeRepository.getStreamUrl(
                track.id, track.title, track.artist,
            ) ?: ytMusicRepository.getStreamUrl(track.id)
        }
    }
}