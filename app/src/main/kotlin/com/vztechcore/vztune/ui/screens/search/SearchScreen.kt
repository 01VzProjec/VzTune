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
    // Estado local para controlar a aba de playlists (Tudo, Músicas, Vídeos)
    var playlistFilter by remember { mutableStateOf(PlaylistFilterType.ALL) }

    // Filtra a lista de resultados em tempo real baseado no filtro de playlists
    val displayedHits = remember(state.hits, state.selectedFilter, playlistFilter) {
        if (state.selectedFilter == SearchFilter.PLAYLISTS) {
            state.hits.filter { hit ->
                if (hit is SearchHit.PlaylistHit) {
                    when (playlistFilter) {
                        PlaylistFilterType.ALL -> true
                        PlaylistFilterType.MUSIC -> hit.playlist.source == Source.YOUTUBE_MUSIC
                        PlaylistFilterType.VIDEO -> hit.playlist.source == Source.YOUTUBE
                    }
                } else true
            }
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

        // Exibe a barra secundária de filtros SOMENTE se a aba Playlists estiver selecionada
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
            // Se a busca principal for vazia, OU se o nosso filtro secundário ocultar tudo, mostramos a tela de "Sem Resultados"
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