package com.vztechcore.vztune.ui.screens.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchFilter
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.ui.theme.VzTuneTheme

private val sampleHits = listOf(
    SearchHit.SongHit(
        Track(
            id = "1",
            title = "Get Lucky",
            artist = "Daft Punk",
            durationSeconds = 369L,
            source = Source.YOUTUBE_MUSIC,
        ),
    ),
    SearchHit.VideoHit(
        Track(
            id = "2",
            title = "Get Lucky (Official Video)",
            artist = "Daft Punk",
            durationSeconds = 248L,
            source = Source.YOUTUBE,
        ),
    ),
)

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
        )
    }
}

@Preview(name = "Search - History", showBackground = true)
@Composable
private fun SearchHistoryPreview() {
    VzTuneTheme {
        SearchScreen(
            state = SearchUiState(
                isFocused = true,
                history = listOf("daft punk", "gorillaz", "the weeknd"),
            ),
            onQueryChange = {},
            onFocusChanged = {},
            onFilterSelect = {},
            onSubmit = {},
            onRefresh = {},
            onRemoveHistory = {},
            onClearHistory = {},
            onLoadMore = {},
        )
    }
}

@Preview(name = "Search - Loading", showBackground = true)
@Composable
private fun SearchLoadingPreview() {
    VzTuneTheme {
        SearchScreen(
            state = SearchUiState(
                query = "daft punk",
                isLoading = true,
                hasSearched = true,
            ),
            onQueryChange = {},
            onFocusChanged = {},
            onFilterSelect = {},
            onSubmit = {},
            onRefresh = {},
            onRemoveHistory = {},
            onClearHistory = {},
            onLoadMore = {},
        )
    }
}

@Preview(name = "Search - Results", showBackground = true)
@Composable
private fun SearchResultsPreview() {
    VzTuneTheme {
        SearchScreen(
            state = SearchUiState(
                query = "daft punk",
                hasSearched = true,
                selectedFilter = SearchFilter.ALL,
                hits = sampleHits,
            ),
            onQueryChange = {},
            onFocusChanged = {},
            onFilterSelect = {},
            onSubmit = {},
            onRefresh = {},
            onRemoveHistory = {},
            onClearHistory = {},
            onLoadMore = {},
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
                hasSearched = true,
                errorMessage = "Sem conexão com a internet.",
            ),
            onQueryChange = {},
            onFocusChanged = {},
            onFilterSelect = {},
            onSubmit = {},
            onRefresh = {},
            onRemoveHistory = {},
            onClearHistory = {},
            onLoadMore = {},
        )
    }
}