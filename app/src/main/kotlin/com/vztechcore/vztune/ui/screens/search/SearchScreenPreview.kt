package com.vztechcore.vztune.ui.screens.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.vztechcore.vztune.ui.theme.VzTuneTheme

@Preview(name = "Search - Idle", showBackground = true)
@Composable
private fun SearchIdlePreview() {
    VzTuneTheme {
        SearchScreen(
            state = SearchUiState(),
            onQueryChange = {},
            onFocusChanged = {},
            onFilterSelect = {},
            onSubmit = {},
            onRefresh = {},
            onRemoveHistory = {},
            onClearHistory = {},
            onLoadMore = {},
            onHitClick = {},
        )
    }
}

@Preview(name = "Search - Loading", showBackground = true)
@Composable
private fun SearchLoadingPreview() {
    VzTuneTheme {
        SearchScreen(
            state = SearchUiState(query = "daft punk", isLoading = true, hasSearched = true),
            onQueryChange = {},
            onFocusChanged = {},
            onFilterSelect = {},
            onSubmit = {},
            onRefresh = {},
            onRemoveHistory = {},
            onClearHistory = {},
            onLoadMore = {},
            onHitClick = {},
        )
    }
}