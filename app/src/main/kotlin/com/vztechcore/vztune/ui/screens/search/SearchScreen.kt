package com.vztechcore.vztune.ui.screens.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.R
import com.vztechcore.vztune.ui.components.AppEmptyView
import com.vztechcore.vztune.ui.components.AppErrorView
import com.vztechcore.vztune.ui.components.AppLoadingView
import com.vztechcore.vztune.ui.components.AppSearchBar
import com.vztechcore.vztune.ui.theme.VzOnSurface
import com.vztechcore.vztune.ui.theme.VzOnSurfaceVariant
import com.vztechcore.vztune.ui.theme.VzSecondary
import com.vztechcore.vztune.ui.theme.VzSurfaceVariant

@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onTabSelect: (SearchTab) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        AppSearchBar(
            query = state.query,
            onQueryChange = onQueryChange,
            onSubmit = onSubmit,
            placeholder = stringResource(R.string.search_hint),
        )

        SearchTabsRow(
            selected = state.selectedTab,
            onTabSelect = onTabSelect,
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> AppLoadingView()

                state.errorMessage != null -> AppErrorView(
                    message = state.errorMessage,
                    onRetry = onSubmit,
                    retryLabel = stringResource(R.string.search_error_retry),
                )

                state.isIdle -> AppEmptyView(
                    title = stringResource(R.string.search_empty_title),
                    description = stringResource(R.string.search_empty_description),
                )

                state.isEmptyResult -> AppEmptyView(
                    title = stringResource(R.string.search_no_results_title),
                    description = stringResource(R.string.search_no_results_description),
                )

                else -> SearchResultsContent(state = state)
            }
        }
    }
}

@Composable
private fun SearchTabsRow(
    selected: SearchTab,
    onTabSelect: (SearchTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.padding(horizontal = 12.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        items(SearchTab.entries.toList()) { tab ->
            FilterChip(
                selected = tab == selected,
                onClick = { onTabSelect(tab) },
                label = {
                    Text(
                        text = stringResource(tab.labelRes),
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = VzSecondary,
                    selectedLabelColor = VzOnSurface,
                    containerColor = VzSurfaceVariant,
                    labelColor = VzOnSurfaceVariant,
                ),
            )
        }
    }
}

@Composable
private fun SearchResultsContent(
    state: SearchUiState,
    modifier: Modifier = Modifier,
) {
    // TODO(search-ui): replace with real cards per type (TrackCard, ArtistCard, etc.)
    // For now this renders simple rows so the state pipeline is verifiable.
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        when (state.selectedTab) {
            SearchTab.ALL -> {
                items(state.tracks) { Text(text = it.title, color = VzOnSurface) }
                items(state.videos) { Text(text = it.title, color = VzOnSurface) }
            }
            SearchTab.SONGS -> items(state.tracks) { Text(text = it.title, color = VzOnSurface) }
            SearchTab.ARTISTS -> items(state.artists) { Text(text = it.name, color = VzOnSurface) }
            SearchTab.ALBUMS -> items(state.albums) { Text(text = it.title, color = VzOnSurface) }
            SearchTab.PLAYLISTS -> items(state.playlists) { Text(text = it.title, color = VzOnSurface) }
            SearchTab.VIDEOS -> items(state.videos) { Text(text = it.title, color = VzOnSurface) }
        }
    }
}