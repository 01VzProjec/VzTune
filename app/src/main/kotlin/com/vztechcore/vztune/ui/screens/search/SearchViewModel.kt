package com.vztechcore.vztune.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vztechcore.vztune.core.domain.search.SearchFilter
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.core.domain.search.SearchResult
import com.vztechcore.vztune.core.domain.search.SearchSuggestions
import com.vztechcore.vztune.data.music.MusicRepository
import com.vztechcore.vztune.data.player.PlayerController
import com.vztechcore.vztune.data.prefs.AppSettingsStore
import com.vztechcore.vztune.data.prefs.SearchHistoryStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val historyStore: SearchHistoryStore,
    private val appSettingsStore: AppSettingsStore,
    private val playerController: PlayerController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var suggestJob: Job? = null
    private var autoSearchJob: Job? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val saved = appSettingsStore.settings.first().lastSearchTab
            val filter = runCatching { SearchFilter.valueOf(saved) }.getOrDefault(SearchFilter.ALL)
            _uiState.update { it.copy(selectedFilter = filter) }
        }
        viewModelScope.launch {
            historyStore.history.collect { list ->
                _uiState.update { it.copy(history = list) }
            }
        }
    }

    fun onQueryChange(value: String) {
        suggestJob?.cancel()
        autoSearchJob?.cancel()
        searchJob?.cancel()

        _uiState.update {
            it.copy(
                query = value,
                errorMessage = null,
                hasSearched = false,
                hits = emptyList(),
                hasMore = false,
                isLoading = false,
                isLoadingMore = false,
                isRefreshing = false,
            )
        }

        if (value.isBlank()) {
            _uiState.update { it.copy(suggestions = SearchSuggestions()) }
            return
        }

        // Suggestions (200ms debounce)
        suggestJob = viewModelScope.launch {
            delay(200)
            val s = musicRepository.suggestions(value)
            _uiState.update { it.copy(suggestions = s) }
        }

        // Auto-search (500ms debounce)
        autoSearchJob = viewModelScope.launch {
            delay(500)
            if (_uiState.value.query == value && !_uiState.value.hasSearched) {
                runSearch(value, _uiState.value.selectedFilter, isRefresh = false)
            }
        }
    }

    fun onFocusChanged(focused: Boolean) {
        _uiState.update { it.copy(isFocused = focused) }
    }

    fun onFilterSelect(filter: SearchFilter) {
        if (filter == _uiState.value.selectedFilter) return
        autoSearchJob?.cancel()
        _uiState.update { it.copy(selectedFilter = filter) }
        viewModelScope.launch { appSettingsStore.setLastSearchTab(filter.name) }

        // Always re-search when there's a query (even if not submitted yet)
        if (_uiState.value.query.isNotBlank()) {
            runSearch(_uiState.value.query, filter, isRefresh = false)
        }
    }

    fun onSubmit() {
        val q = _uiState.value.query.trim()
        if (q.isEmpty()) return
        autoSearchJob?.cancel()
        _uiState.update { it.copy(isFocused = false) }
        viewModelScope.launch { historyStore.add(q) }
        runSearch(q, _uiState.value.selectedFilter, isRefresh = false)
    }

    fun onRefresh() {
        if (_uiState.value.query.isBlank()) return
        runSearch(_uiState.value.query, _uiState.value.selectedFilter, isRefresh = true)
    }

    fun onRemoveHistory(query: String) {
        viewModelScope.launch { historyStore.remove(query) }
    }

    fun onClearHistory() {
        viewModelScope.launch { historyStore.clear() }
    }

    fun onLoadMore() {
        if (!_uiState.value.hasMore ||
            _uiState.value.isLoadingMore ||
            _uiState.value.isLoading
        ) return
        _uiState.update { it.copy(hasMore = false) }
    }

    fun onHitClick(hit: SearchHit) {
        when (hit) {
            is SearchHit.SongHit -> playerController.play(hit.track)
            is SearchHit.VideoHit -> playerController.play(hit.track)
            else -> Unit // TODO: navigate to artist / album / playlist detail
        }
    }

    private fun runSearch(query: String, filter: SearchFilter, isRefresh: Boolean) {
        searchJob?.cancel()
        autoSearchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !isRefresh,
                    isRefreshing = isRefresh,
                    hasSearched = true,
                    errorMessage = null,
                )
            }

            when (val result = musicRepository.search(query, filter)) {
                is SearchResult.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        hits = result.hits,
                        hasMore = false,
                    )
                }
                is SearchResult.Failure -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = result.message,
                    )
                }
            }
        }
    }
}