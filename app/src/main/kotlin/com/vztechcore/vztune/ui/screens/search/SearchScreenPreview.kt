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
            onTabSelect = {},
            onSubmit = {},
        )
    }
}

@Preview(name = "Search - Loading", showBackground = true)
@Composable
private fun SearchLoadingPreview() {
    VzTuneTheme {
        SearchScreen(
            state = SearchUiState(query = "daft punk", isLoading = true),
            onQueryChange = {},
            onTabSelect = {},
            onSubmit = {},
        )
    }
}

@Preview(name = "Search - Error", showBackground = true)
@Composable
private fun SearchErrorPreview() {
    VzTuneTheme {
        SearchScreen(
            state = SearchUiState(
                query = "daft punk",
                errorMessage = "No internet connection.",
            ),
            onQueryChange = {},
            onTabSelect = {},
            onSubmit = {},
        )
    }
}

@Preview(name = "Search - Empty results", showBackground = true)
@Composable
private fun SearchEmptyPreview() {
    VzTuneTheme {
        SearchScreen(
            state = SearchUiState(query = "xyzxyzxyz"),
            onQueryChange = {},
            onTabSelect = {},
            onSubmit = {},
        )
    }
}