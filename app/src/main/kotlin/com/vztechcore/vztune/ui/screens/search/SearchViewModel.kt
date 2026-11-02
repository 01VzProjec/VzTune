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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    // ================= NAVIGATION EVENTS =================

    sealed interface NavigationEvent {
        data class OpenPlaylist(
            val id: String,
            val title: String,
            val owner: String?,
            val thumbnailUrl: String?,
            val mosaic: List<String>,
        ) : NavigationEvent

        data class OpenAlbum(
            val id: String,
            val title: String,
            val artist: String,
            val thumbnailUrl: String?,
        ) : NavigationEvent
    }

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    // ================= STATE =================

    private data class CachedTab(
        val query: String,
        val hits: List<SearchHit>,
        val hasMore: Boolean,
    )

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val filterCache = mutableMapOf<SearchFilter, CachedTab>()

    private var suggestJob: Job? = null
    private var searchJob: Job? = null

    // ================= INIT =================

    init {
        viewModelScope.launch {
            val saved = appSettingsStore.settings.first().lastSearchTab
            val filter = runCatching { SearchFilter.valueOf(saved) }
                .getOrDefault(SearchFilter.ALL)
            _uiState.update { it.copy(selectedFilter = filter) }
        }

        viewModelScope.launch {
            historyStore.history.collect { list ->
                _uiState.update { it.copy(history = list) }
            }
        }
    }

    // ================= QUERY =================

    fun onQueryChange(value: String) {
        suggestJob?.cancel()
        searchJob?.cancel()
        filterCache.clear()

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

        suggestJob = viewModelScope.launch {
            delay(250)
            val suggestions = musicRepository.suggestions(value)
            _uiState.update { it.copy(suggestions = suggestions) }
        }
    }

    fun onFocusChanged(focused: Boolean) {
        _uiState.update { it.copy(isFocused = focused) }
    }

    // ================= FILTER =================

    fun onFilterSelect(filter: SearchFilter) {
        if (filter == _uiState.value.selectedFilter) return

        val currentQuery = _uiState.value.query

        viewModelScope.launch { appSettingsStore.setLastSearchTab(filter.name) }

        val cached = filterCache[filter]
        if (cached != null && cached.query == currentQuery) {
            _uiState.update {
                it.copy(
                    selectedFilter = filter,
                    hits = cached.hits,
                    hasMore = cached.hasMore,
                    hasSearched = true,
                )
            }
            return
        }

        _uiState.update { it.copy(selectedFilter = filter) }

        if (currentQuery.isNotBlank()) {
            runSearch(currentQuery, filter, isRefresh = false, loadMore = false)
        }
    }

    // ================= SUBMIT / REFRESH =================

    fun onSubmit() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return

        _uiState.update { it.copy(isFocused = false) }
        viewModelScope.launch { historyStore.add(query) }

        filterCache.clear()
        runSearch(
            query = query,
            filter = _uiState.value.selectedFilter,
            isRefresh = false,
            loadMore = false,
        )
    }

    fun onRefresh() {
        if (_uiState.value.query.isBlank()) return
        filterCache.clear()
        runSearch(
            query = _uiState.value.query,
            filter = _uiState.value.selectedFilter,
            isRefresh = true,
            loadMore = false,
        )
    }

    // ================= HISTORY =================

    fun onRemoveHistory(query: String) {
        viewModelScope.launch { historyStore.remove(query) }
    }

    fun onClearHistory() {
        viewModelScope.launch { historyStore.clear() }
    }

    // ================= PAGINATION =================

    fun onLoadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || state.isLoading || !state.hasMore) return
        if (state.query.isBlank()) return

        _uiState.update { it.copy(isLoadingMore = true) }

        viewModelScope.launch {
            val filter = state.selectedFilter
            when (val result = musicRepository.search(
                query = state.query,
                filter = filter,
                loadMore = true,
            )) {
                is SearchResult.Success -> {
                    val merged = _uiState.value.hits + result.hits
                    filterCache[filter] = CachedTab(
                        query = state.query,
                        hits = merged,
                        hasMore = result.hasMore,
                    )
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            hits = merged,
                            hasMore = result.hasMore,
                        )
                    }
                }
                is SearchResult.Failure -> {
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
            }
        }
    }

    // ================= HIT CLICK =================

    fun onHitClick(hit: SearchHit) {
        when (hit) {
            is SearchHit.SongHit -> playerController.play(hit.track)
            is SearchHit.VideoHit -> playerController.play(hit.track)
            is SearchHit.PlaylistHit -> {
                viewModelScope.launch {
                    _navigationEvent.emit(
                        NavigationEvent.OpenPlaylist(
                            id = hit.playlist.id,
                            title = hit.playlist.title,
                            owner = hit.playlist.owner,
                            thumbnailUrl = hit.playlist.thumbnailUrl,
                            mosaic = hit.playlist.mosaicThumbnails,
                        )
                    )
                }
            }
            is SearchHit.AlbumHit -> {
                viewModelScope.launch {
                    _navigationEvent.emit(
                        NavigationEvent.OpenAlbum(
                            id = hit.album.id,
                            title = hit.album.title,
                            artist = hit.album.artist,
                            thumbnailUrl = hit.album.thumbnailUrl,
                        )
                    )
                }
            }
            is SearchHit.ArtistHit -> {
                // TODO: navigate to ArtistScreen
            }
        }
    }

    // ================= INTERNAL =================

    private fun runSearch(
        query: String,
        filter: SearchFilter,
        isRefresh: Boolean,
        loadMore: Boolean,
    ) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !isRefresh && !loadMore,
                    isRefreshing = isRefresh,
                    hasSearched = true,
                    errorMessage = null,
                )
            }

            when (val result = musicRepository.search(query, filter, loadMore)) {
                is SearchResult.Success -> {
                    filterCache[filter] = CachedTab(
                        query = query,
                        hits = result.hits,
                        hasMore = result.hasMore,
                    )
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            hits = result.hits,
                            hasMore = result.hasMore,
                        )
                    }
                }
                is SearchResult.Failure -> {
                    _uiState.update {
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
}