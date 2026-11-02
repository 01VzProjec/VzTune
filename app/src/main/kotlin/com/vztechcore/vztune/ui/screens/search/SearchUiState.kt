package com.vztechcore.vztune.ui.screens.search

import com.vztechcore.vztune.core.domain.search.SearchFilter
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.core.domain.search.SearchSuggestions

data class SearchUiState(
    val query: String = "",
    val isFocused: Boolean = false,
    val selectedFilter: SearchFilter = SearchFilter.ALL,
    val hits: List<SearchHit> = emptyList(),
    val history: List<String> = emptyList(),
    val suggestions: SearchSuggestions = SearchSuggestions(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasSearched: Boolean = false,
    val hasMore: Boolean = false,
    val isBuildingQueue: Boolean = false,
    val errorMessage: String? = null,
) {
    val isEmptyResult: Boolean
        get() = hasSearched && !isLoading && errorMessage == null &&
            hits.isEmpty() && query.isNotBlank()

    val showHistoryState: Boolean
        get() = isFocused && query.isBlank()

    val showSuggestionsState: Boolean
        get() = !hasSearched && query.isNotBlank() && !isLoading

    val showIdleState: Boolean
        get() = !isFocused && query.isBlank() && !hasSearched
}