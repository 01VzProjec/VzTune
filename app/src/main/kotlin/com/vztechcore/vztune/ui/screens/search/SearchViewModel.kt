package com.vztechcore.vztune.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchFilter
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.core.domain.search.SearchResult
import com.vztechcore.vztune.core.domain.search.SearchSuggestions
import com.vztechcore.vztune.data.music.MusicRepository
import com.vztechcore.vztune.data.player.PlayerController
import com.vztechcore.vztune.data.prefs.models.LocalPlaylist
import com.vztechcore.vztune.data.prefs.models.StoredTrack
import com.vztechcore.vztune.data.prefs.store.FavoriteTracksStore
import com.vztechcore.vztune.data.prefs.store.LocalPlaylistsStore
import com.vztechcore.vztune.data.prefs.store.RecentPlayedStore
import com.vztechcore.vztune.data.prefs.store.SearchHistoryStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val historyStore: SearchHistoryStore,
    private val recentPlayedStore: RecentPlayedStore,
    private val favoriteTracksStore: FavoriteTracksStore,
    private val localPlaylistsStore: LocalPlaylistsStore,
    private val playerController: PlayerController,
) : ViewModel() {

    sealed interface NavigationEvent {
        data class OpenPlaylist(
            val id: String, val title: String, val owner: String?,
            val thumbnailUrl: String?, val mosaic: List<String>,
        ) : NavigationEvent
        data class OpenAlbum(
            val id: String, val title: String, val artist: String,
            val thumbnailUrl: String?,
        ) : NavigationEvent
        data class OpenArtist(
            val id: String, val name: String, val thumbnailUrl: String?,
        ) : NavigationEvent
    }

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    val favoriteKeys: StateFlow<Set<String>> = favoriteTracksStore.tracks
        .map { list -> list.map { StoredTrack.from(it).key }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val localPlaylists: StateFlow<List<LocalPlaylist>> = localPlaylistsStore.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private data class CachedTab(val query: String, val hits: List<SearchHit>, val hasMore: Boolean)

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val filterCache = mutableMapOf<SearchFilter, CachedTab>()
    private var suggestJob: Job? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            historyStore.history.collect { list ->
                _uiState.update { it.copy(history = list) }
            }
        }
        viewModelScope.launch {
            recentPlayedStore.tracks.collect { list ->
                _uiState.update { it.copy(recentTracks = list) }
            }
        }
    }

    fun onQueryChange(value: String) {
        suggestJob?.cancel()
        searchJob?.cancel()
        filterCache.clear()
        _uiState.update {
            it.copy(query = value, errorMessage = null, hasSearched = false, hits = emptyList(), hasMore = false, isLoading = false, isLoadingMore = false, isRefreshing = false)
        }
        if (value.isBlank() || value.trim().length < 2) {
            _uiState.update { it.copy(suggestions = SearchSuggestions()) }
            return
        }
        suggestJob = viewModelScope.launch {
            delay(400)
            val s = musicRepository.suggestions(value)
            _uiState.update { it.copy(suggestions = s) }
        }
    }

    fun onFocusChanged(focused: Boolean) {
        _uiState.update { it.copy(isFocused = focused) }
    }

    fun onFilterSelect(filter: SearchFilter) {
        if (filter == _uiState.value.selectedFilter) return
        val currentQuery = _uiState.value.query
        val cached = filterCache[filter]
        if (cached != null && cached.query == currentQuery) {
            _uiState.update { it.copy(selectedFilter = filter, hits = cached.hits, hasMore = cached.hasMore, hasSearched = true) }
            return
        }
        _uiState.update { it.copy(selectedFilter = filter) }
        if (currentQuery.isNotBlank()) {
            runSearch(currentQuery, filter, isRefresh = false, loadMore = false)
        }
    }

    fun onSubmit() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return
        _uiState.update { it.copy(isFocused = false) }
        viewModelScope.launch { historyStore.add(query) }
        filterCache.clear()
        runSearch(query, _uiState.value.selectedFilter, isRefresh = false, loadMore = false)
    }

    fun onRefresh() {
        if (_uiState.value.query.isBlank()) return
        filterCache.clear()
        runSearch(_uiState.value.query, _uiState.value.selectedFilter, isRefresh = true, loadMore = false)
    }

    fun onRemoveHistory(query: String) { viewModelScope.launch { historyStore.remove(query) } }
    fun onClearHistory() { viewModelScope.launch { historyStore.clear() } }

    fun onLoadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || state.isLoading || !state.hasMore) return
        if (state.query.isBlank()) return
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            val filter = state.selectedFilter
            when (val result = musicRepository.search(state.query, filter, loadMore = true)) {
                is SearchResult.Success -> {
                    val merged = _uiState.value.hits + result.hits
                    filterCache[filter] = CachedTab(state.query, merged, result.hasMore)
                    _uiState.update { it.copy(isLoadingMore = false, hits = merged, hasMore = result.hasMore) }
                }
                is SearchResult.Failure -> {
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
            }
        }
    }

    fun onHitClick(hit: SearchHit) {
        when (hit) {
            is SearchHit.SongHit -> playRadio(hit.track)
            is SearchHit.VideoHit -> playRadio(hit.track)
            is SearchHit.PlaylistHit -> {
                viewModelScope.launch {
                    _navigationEvent.emit(NavigationEvent.OpenPlaylist(hit.playlist.id, hit.playlist.title, hit.playlist.owner, hit.playlist.thumbnailUrl, hit.playlist.mosaicThumbnails))
                }
            }
            is SearchHit.AlbumHit -> {
                viewModelScope.launch {
                    _navigationEvent.emit(NavigationEvent.OpenAlbum(hit.album.id, hit.album.title, hit.album.artist, hit.album.thumbnailUrl))
                }
            }
            is SearchHit.ArtistHit -> {
                viewModelScope.launch {
                    _navigationEvent.emit(NavigationEvent.OpenArtist(hit.artist.id, hit.artist.name, hit.artist.thumbnailUrl))
                }
            }
        }
    }

    fun onPlayNextClick(track: Track) {
        playerController.addToQueueNext(track)
    }

    fun onAddToQueueClick(track: Track) {
        playerController.addToQueueEnd(track)
    }

    fun onToggleFavorite(track: Track) {
        viewModelScope.launch { favoriteTracksStore.toggle(track) }
    }

    fun onAddToPlaylist(playlistId: String, track: Track) {
        viewModelScope.launch { localPlaylistsStore.addTrack(playlistId, track) }
    }

    fun onOpenArtistByName(artistName: String) {
        viewModelScope.launch {
            val result = musicRepository.search(artistName, SearchFilter.ARTISTS, loadMore = false)
            if (result is SearchResult.Success) {
                val artistHit = result.hits.filterIsInstance<SearchHit.ArtistHit>().firstOrNull()
                if (artistHit != null) {
                    _navigationEvent.emit(
                        NavigationEvent.OpenArtist(
                            id = artistHit.artist.id,
                            name = artistHit.artist.name,
                            thumbnailUrl = artistHit.artist.thumbnailUrl,
                        )
                    )
                }
            }
        }
    }

    private fun playRadio(seed: Track) {
        if (_uiState.value.isBuildingQueue) return
        _uiState.update { it.copy(isBuildingQueue = true) }
        viewModelScope.launch { recentPlayedStore.add(seed) }
        viewModelScope.launch {
            val queue = runCatching { musicRepository.buildRadioQueue(seed) }.getOrDefault(listOf(seed))
            _uiState.update { it.copy(isBuildingQueue = false) }
            val idx = queue.indexOfFirst { it.id == seed.id && it.source == seed.source }
            playerController.playQueue(queue, if (idx >= 0) idx else 0)
        }
    }

    private fun runSearch(query: String, filter: SearchFilter, isRefresh: Boolean, loadMore: Boolean) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = !isRefresh && !loadMore, isRefreshing = isRefresh, hasSearched = true, errorMessage = null) }
            when (val result = musicRepository.search(query, filter, loadMore)) {
                is SearchResult.Success -> {
                    filterCache[filter] = CachedTab(query, result.hits, result.hasMore)
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false, hits = result.hits, hasMore = result.hasMore) }
                }
                is SearchResult.Failure -> {
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false, errorMessage = result.message) }
                }
            }
        }
    }
}