package com.vztechcore.vztune.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vztechcore.vztune.core.domain.search.SearchResult
import com.vztechcore.vztune.data.prefs.AppSettingsStore
import com.vztechcore.vztune.data.youtube.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val youTubeRepository: YouTubeRepository,
    private val appSettingsStore: AppSettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        // Restore last selected search tab
        viewModelScope.launch {
            val saved = appSettingsStore.settings.first().lastSearchTab
            val tab = runCatching { SearchTab.valueOf(saved) }.getOrDefault(SearchTab.ALL)
            _uiState.update { it.copy(selectedTab = tab) }
        }
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value, errorMessage = null) }
    }

    fun onTabSelect(tab: SearchTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        viewModelScope.launch {
            appSettingsStore.setLastSearchTab(tab.name)
        }
        // Re-run search if there is already a query
        if (_uiState.value.query.isNotBlank()) {
            onSubmit()
        }
    }

    fun onSubmit() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            when (val result = youTubeRepository.search(query, _uiState.value.selectedTab.name)) {
                is SearchResult.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        tracks = result.tracks,
                        artists = result.artists,
                        albums = result.albums,
                        playlists = result.playlists,
                        videos = result.videos,
                    )
                }
                is SearchResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }
}