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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val youTubeRepository: YouTubeRepository,
    private val ytMusicRepository: YTMusicRepository,
    private val youTubeMusicNewPipeRepository: YouTubeMusicNewPipeRepository,
) {

    private data class SearchState(
        val seenKeys: MutableSet<String> = mutableSetOf(),
        var ytHasMore: Boolean = true,
        var ytmContinuation: String? = null,
        var ytmHasMore: Boolean = true,
        var topArtistsLoaded: Boolean = false,
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
            state.topArtistsLoaded = false
            youTubeRepository.resetSession()
        }

        when (filter) {
            SearchFilter.ALL -> searchAll(query, loadMore, state)
            SearchFilter.VIDEOS -> searchVideos(query, loadMore, state)
            SearchFilter.SONGS -> searchSongs(query, loadMore, state)
            SearchFilter.PLAYLISTS -> searchPlaylists(query, loadMore, state)
            else -> searchOther(query, filter, loadMore, state)
        }
    }

    // ================= ALL (3 artists top + interleaved) =================

    private suspend fun searchAll(
        query: String,
        loadMore: Boolean,
        state: SearchState,
    ): SearchResult = coroutineScope {
        val pageSize = if (loadMore) 10 else 20

        // 1) First page: fetch top artists in parallel with videos/songs.
        val topArtistsDeferred = if (!state.topArtistsLoaded && !loadMore) {
            async {
                runCatching { ytMusicRepository.searchArtists(query) }
                    .getOrDefault(emptyList())
                    .take(3)
            }
        } else null

        // 2) Fetch videos + songs in parallel.
        val ytDeferred = async {
            youTubeRepository.searchVideos(
                query = query,
                loadMore = loadMore,
                pageSize = pageSize,
            )
        }
        val ytmDeferred = async {
            ytMusicRepository.search(
                query = query,
                filter = SearchFilter.ALL,
                continuation = state.ytmContinuation,
            )
        }

        val ytPage = ytDeferred.await()
        val ytmPage = ytmDeferred.await()
        val topArtists = topArtistsDeferred?.await().orEmpty()

        state.ytHasMore = ytPage.hasMore
        state.ytmContinuation = ytmPage.continuation
        state.ytmHasMore = ytmPage.continuation != null

        // Build merged list.
        val merged = mutableListOf<SearchHit>()

        // 3 artists at the very top (only on first page).
        if (topArtists.isNotEmpty()) {
            topArtists.forEach { artist ->
                merged += SearchHit.ArtistHit(artist)
            }
            state.topArtistsLoaded = true
        }

        // Interleave YT videos and YTM songs.
        val interleaved = interleave(
            ytPage.items,
            ytmPage.items.take(pageSize),
        )
        merged += interleaved

        val newHits = merged.filter { state.seenKeys.add(it.stableKey) }
        val hasMore = state.ytHasMore || state.ytmHasMore
        SearchResult.Success(newHits, hasMore)
    }

    // ================= VIDEOS =================

    private suspend fun searchVideos(
        query: String,
        loadMore: Boolean,
        state: SearchState,
    ): SearchResult {
        val ytPage = youTubeRepository.searchVideos(
            query = query,
            loadMore = loadMore,
            pageSize = 30,
        )
        state.ytHasMore = ytPage.hasMore
        val newHits = ytPage.items.filter { state.seenKeys.add(it.stableKey) }
        return SearchResult.Success(newHits, state.ytHasMore)
    }

    // ================= SONGS =================

    private suspend fun searchSongs(
        query: String,
        loadMore: Boolean,
        state: SearchState,
    ): SearchResult {
        val ytmPage = ytMusicRepository.search(
            query = query,
            filter = SearchFilter.SONGS,
            continuation = state.ytmContinuation,
        )
        state.ytmContinuation = ytmPage.continuation
        state.ytmHasMore = ytmPage.continuation != null
        val newHits = ytmPage.items.take(30).filter { state.seenKeys.add(it.stableKey) }
        return SearchResult.Success(newHits, state.ytmHasMore)
    }

    // ================= PLAYLISTS =================

    private suspend fun searchPlaylists(
        query: String,
        loadMore: Boolean,
        state: SearchState,
    ): SearchResult = coroutineScope {
        val ytDeferred = async {
            if (!loadMore) youTubeRepository.searchPlaylists(query, loadMore = false)
            else emptyList()
        }
        val ytmDeferred = async {
            ytMusicRepository.search(
                query = query,
                filter = SearchFilter.PLAYLISTS,
                continuation = state.ytmContinuation,
            )
        }
        val ytHits = ytDeferred.await()
        val ytmPage = ytmDeferred.await()
        state.ytmContinuation = ytmPage.continuation
        state.ytmHasMore = ytmPage.continuation != null

        val merged = ytHits + ytmPage.items
        val newHits = merged.filter { state.seenKeys.add(it.stableKey) }
        SearchResult.Success(newHits, state.ytmHasMore)
    }

    // ================= OTHERS (artists / albums) =================

    private suspend fun searchOther(
        query: String,
        filter: SearchFilter,
        loadMore: Boolean,
        state: SearchState,
    ): SearchResult {
        val ytmPage = ytMusicRepository.search(
            query = query,
            filter = filter,
            continuation = state.ytmContinuation,
        )
        state.ytmContinuation = ytmPage.continuation
        state.ytmHasMore = ytmPage.continuation != null
        val newHits = ytmPage.items.filter { state.seenKeys.add(it.stableKey) }
        return SearchResult.Success(newHits, state.ytmHasMore)
    }

    // ================= SUGGESTIONS =================

    suspend fun suggestions(query: String): SearchSuggestions =
        withContext(Dispatchers.IO) {
            runCatching { ytMusicRepository.suggestions(query) }
                .getOrDefault(SearchSuggestions())
        }

    // ================= PLAYBACK =================

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