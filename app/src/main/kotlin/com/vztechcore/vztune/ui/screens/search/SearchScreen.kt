package com.vztechcore.vztune.ui.screens.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchFilter
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.ui.screens.search.components.SearchAutocomplete
import com.vztechcore.vztune.ui.screens.search.components.SearchBar
import com.vztechcore.vztune.ui.screens.search.components.SearchEmptyState
import com.vztechcore.vztune.ui.screens.search.components.SearchErrorState
import com.vztechcore.vztune.ui.screens.search.components.SearchLoadingState
import com.vztechcore.vztune.ui.screens.search.components.SearchNoResultsState
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

        when {
            state.showHistoryState || state.showSuggestionsState -> {
                SearchAutocomplete(
                    history = state.history,
                    suggestions = state.suggestions,
                    showHistory = state.showHistoryState,
                    onPickQuery = { q ->
                        onQueryChange(q)
                        onSubmit()
                    },
                    onRemoveHistory = onRemoveHistory,
                    onClearHistory = onClearHistory,
                )
            }
            state.showIdleState -> SearchEmptyState()
            state.isLoading -> SearchLoadingState()
            state.errorMessage != null -> SearchErrorState(
                message = state.errorMessage,
                onRetry = onSubmit,
            )
            state.isEmptyResult -> SearchNoResultsState(query = state.query)
            else -> SearchResultsContent(
                hits = state.hits,
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