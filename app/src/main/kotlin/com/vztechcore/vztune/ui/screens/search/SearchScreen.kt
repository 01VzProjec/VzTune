package com.vztechcore.vztune.ui.screens.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchFilter
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.ui.screens.search.components.PlaylistFilterType
import com.vztechcore.vztune.ui.screens.search.components.SearchAutocomplete
import com.vztechcore.vztune.ui.screens.search.components.SearchBar
import com.vztechcore.vztune.ui.screens.search.components.SearchEmptyState
import com.vztechcore.vztune.ui.screens.search.components.SearchErrorState
import com.vztechcore.vztune.ui.screens.search.components.SearchLoadingState
import com.vztechcore.vztune.ui.screens.search.components.SearchNoResultsState
import com.vztechcore.vztune.ui.screens.search.components.SearchPlaylistFilterTab
import com.vztechcore.vztune.ui.screens.search.components.SearchResultsContent

@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onFilterSelect: (SearchFilter) -> Unit,
    onSubmit: () -> Unit,
    onRefresh: () -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onLoadMore: () -> Unit,
    onHitClick: (SearchHit) -> Unit = {},
    onPlayNextClick: (Track) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var playlistFilter by remember { mutableStateOf(PlaylistFilterType.ALL) }

    // Interleaves YT Music and YouTube playlists when ALL is selected
    val displayedHits = remember(state.hits, state.selectedFilter, playlistFilter) {
        if (state.selectedFilter == SearchFilter.PLAYLISTS) {
            val playlistHits = state.hits.filterIsInstance<SearchHit.PlaylistHit>()
            val otherHits = state.hits.filter { it !is SearchHit.PlaylistHit }

            val filteredPlaylists = when (playlistFilter) {
                PlaylistFilterType.MUSIC -> playlistHits.filter { it.playlist.source == Source.YOUTUBE_MUSIC }
                PlaylistFilterType.VIDEO -> playlistHits.filter { it.playlist.source == Source.YOUTUBE }
                PlaylistFilterType.ALL -> {
                    val ytm = playlistHits.filter { it.playlist.source == Source.YOUTUBE_MUSIC }
                    val yt = playlistHits.filter { it.playlist.source == Source.YOUTUBE }
                    val interleaved = mutableListOf<SearchHit.PlaylistHit>()
                    val maxLen = maxOf(ytm.size, yt.size)
                    for (i in 0 until maxLen) {
                        if (i < ytm.size) interleaved.add(ytm[i])
                        if (i < yt.size) interleaved.add(yt[i])
                    }
                    interleaved
                }
            }
            otherHits + filteredPlaylists
        } else {
            state.hits
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        SearchBar(
            query = state.query,
            onQueryChange = onQueryChange,
            onFocusChanged = onFocusChanged,
            onSubmit = onSubmit,
            selectedFilter = state.selectedFilter,
            onFilterSelect = onFilterSelect,
            showFilters = state.hasSearched,
        )

        if (state.hasSearched && !state.showHistoryState && !state.showSuggestionsState && state.selectedFilter == SearchFilter.PLAYLISTS) {
            SearchPlaylistFilterTab(
                selected = playlistFilter,
                onSelect = { playlistFilter = it }
            )
        }

        when {
            state.showHistoryState || state.showSuggestionsState -> {
                SearchAutocomplete(
                    query = state.query,
                    history = state.history,
                    suggestions = state.suggestions,
                    showHistory = state.showHistoryState,
                    onPickQuery = { q ->
                        onQueryChange(q)
                        onSubmit()
                    },
                    onRemoveHistory = onRemoveHistory,
                    onClearHistory = onClearHistory,
                    onPickSong = { track -> onHitClick(SearchHit.SongHit(track)) },
                )
            }
            state.showIdleState -> SearchEmptyState()
            state.isLoading -> SearchLoadingState()
            state.errorMessage != null -> SearchErrorState(
                message = state.errorMessage,
                onRetry = onSubmit,
            )
            state.isEmptyResult || (state.selectedFilter == SearchFilter.PLAYLISTS && displayedHits.isEmpty()) -> SearchNoResultsState(query = state.query)
            else -> SearchResultsContent(
                hits = displayedHits,
                isLoadingMore = state.isLoadingMore,
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                onLoadMore = onLoadMore,
                onHitClick = onHitClick,
                onPlayNextClick = onPlayNextClick,
            )
        }
    }
}