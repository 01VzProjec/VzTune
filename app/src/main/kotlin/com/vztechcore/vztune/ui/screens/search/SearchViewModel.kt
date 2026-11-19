package com.vztechcore.vztune.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchFilter
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.core.domain.search.SearchResult
import com.vztechcore.vztune.core.domain.search.SearchSuggestions
import com.vztechcore.vztune.data.download.TrackDownloadItem
import com.vztechcore.vztune.data.download.TrackDownloader
import com.vztechcore.vztune.data.music.MusicRepository
import com.vztechcore.vztune.data.player.PlayerController
import com.vztechcore.vztune.data.prefs.models.LocalPlaylist
import com.vztechcore.vztune.data.prefs.models.StoredTrack
import com.vztechcore.vztune.data.prefs.store.DownloadedTracksStore
import com.vztechcore.vztune.data.prefs.store.FavoriteTracksStore
import com.vztechcore.vztune.data.prefs.store.LocalPlaylistsStore
import com.vztechcore.vztune.data.prefs.store.RecentPlayedStore
import com.vztechcore.vztune.data.prefs.store.SearchHistoryStore
import com.vztechcore.vztune.data.youtube.YouTubeRepository
import com.vztechcore.vztune.data.ytmusic.YTMusicRepository
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
    private val ytMusicRepository: YTMusicRepository,
    private val youTubeRepository: YouTubeRepository,
    private val historyStore: SearchHistoryStore,
    private val recentPlayedStore: RecentPlayedStore,
    private val favoriteTracksStore: FavoriteTracksStore,
    private val localPlaylistsStore: LocalPlaylistsStore,
    private val downloadedTracksStore: DownloadedTracksStore,
    private val trackDownloader: TrackDownloader,
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

    private data class CachedTab(val query: String, val hits: List<SearchHit>, val hasMore: Boolean)

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val favoriteKeys: StateFlow<Set<String>> = favoriteTracksStore.tracks
        .map { list -> list.map { StoredTrack.from(it).key }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val downloadedKeys: StateFlow<Set<String>> = downloadedTracksStore.items
        .map { list -> list.map { it.key }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val activeDownloads: StateFlow<Map<String, TrackDownloadItem>> = trackDownloader.downloads

    val localPlaylists: StateFlow<List<LocalPlaylist>> = localPlaylistsStore.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val filterCache = mutableMapOf<SearchFilter, CachedTab>()
    private var suggestJob: Job? = null
    private var searchJob: Job? = null
    private var prefetchJob: Job? = null

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
        prefetchJob?.cancel()
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
        if (value.isBlank() || value.trim().length < 1) {
            _uiState.update { it.copy(suggestions = SearchSuggestions()) }
            return
        }
        // FIX 6: sugestões a partir de 1 letra (antes: 2) e sem o delay de 400 ms.
        // 1) Instantâneo (0 ms): histórico local que casa com o que foi digitado.
        //    É local, não bate na rede — aparece na hora, igual ao SimpMusic.
        // 2) Rede (debounce curto de 120 ms, não 400 ms): sugestões do YT Music +
        //    faixas. O debounce só evita disparar uma request por tecla quando o
        //    usuário está digitando rápido; 120 ms é imperceptível.
        //    A rede em si continua levando o tempo dela — o que some é a espera
        //    artificial antes de disparar + a espera por algo local.
        val trimmed = value.trim()
        val localQueries = _uiState.value.history
            .filter { it.contains(trimmed, ignoreCase = true) && !it.equals(trimmed, ignoreCase = true) }
            .take(5)
        if (localQueries.isNotEmpty()) {
            _uiState.update { it.copy(suggestions = SearchSuggestions(queries = localQueries)) }
        }
        suggestJob = viewModelScope.launch {
            delay(120)
            val s = musicRepository.suggestions(value)
            // Usuário já digitou outra coisa? Não sobrescreve sugestões de outra query.
            if (_uiState.value.query != value) return@launch
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
            // A aba ainda não foi pré-carregada: busca ela agora e, depois, pré-carrega
            // o resto (o prefetch anterior é cancelado pra não duplicar a busca).
            prefetchJob?.cancel()
            runSearch(currentQuery, filter, isRefresh = false, loadMore = false)
            prefetchOtherTabs(currentQuery, filter)
        }
    }

    fun onSubmit() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return
        _uiState.update { it.copy(isFocused = false) }
        viewModelScope.launch { historyStore.add(query) }
        filterCache.clear()
        prefetchJob?.cancel()
        val selected = _uiState.value.selectedFilter
        runSearch(query, selected, isRefresh = false, loadMore = false)
        prefetchOtherTabs(query, selected)
    }

    fun onRefresh() {
        if (_uiState.value.query.isBlank()) return
        filterCache.clear()
        prefetchJob?.cancel()
        val selected = _uiState.value.selectedFilter
        runSearch(_uiState.value.query, selected, isRefresh = true, loadMore = false)
        prefetchOtherTabs(_uiState.value.query, selected)
    }

    /**
     * FIX 6: pré-carrega TODAS as outras abas em segundo plano, assim que a busca
     * da aba aberta dispara. Quando o usuário troca de aba, o resultado já está
     * no filterCache e aparece na hora (onFilterSelect só mostra o cache).
     *
     * Por que SEQUENCIAL e não paralelo: o MusicRepository guarda um SearchState
     * por aba (ok em paralelo), mas as buscas do YouTube (NewPipe) compartilham
     * o OkHttp/downloader — rodar 6 buscas ao mesmo tempo estoura timeout e
     * mistura paginação. Sequencial é mais lento no total, mas a aba aberta já
     * mostra o resultado dela primeiro; o resto chega em segundo plano.
     *
     * Cada aba tem sua própria sessão NewPipe por searchId (FIX 6 no
     * YouTubeRepository), então o prefetch NÃO quebra o "carregar mais" da aba aberta.
     */
    private fun prefetchOtherTabs(query: String, selected: SearchFilter) {
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            for (filter in SearchFilter.entries) {
                if (filter == selected) continue
                // Query mudou no meio do prefetch? Para, a busca nova vai pré-carregar de novo.
                if (_uiState.value.query.trim() != query) break
                val already = filterCache[filter]
                if (already != null && already.query == query) continue
                val result = runCatching { musicRepository.search(query, filter, loadMore = false) }.getOrNull()
                if (result is SearchResult.Success) {
                    filterCache[filter] = CachedTab(query, result.hits, result.hasMore)
                    // Usuário trocou pra essa aba enquanto ela carregava: mostra na hora.
                    if (_uiState.value.selectedFilter == filter && _uiState.value.query.trim() == query) {
                        _uiState.update { it.copy(hits = result.hits, hasMore = result.hasMore, isLoading = false) }
                    }
                }
            }
        }
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
            is SearchHit.ShortHit -> playRadio(hit.track)
            is SearchHit.PlaylistHit -> {
                viewModelScope.launch {
                    _navigationEvent.emit(
                        NavigationEvent.OpenPlaylist(
                            hit.playlist.id,
                            hit.playlist.title,
                            hit.playlist.owner,
                            hit.playlist.thumbnailUrl,
                            hit.playlist.mosaicThumbnails,
                        )
                    )
                }
            }
            is SearchHit.AlbumHit -> {
                viewModelScope.launch {
                    _navigationEvent.emit(
                        NavigationEvent.OpenAlbum(
                            hit.album.id,
                            hit.album.title,
                            hit.album.artist,
                            hit.album.thumbnailUrl,
                        )
                    )
                }
            }
            is SearchHit.ArtistHit -> {
                viewModelScope.launch {
                    _navigationEvent.emit(
                        NavigationEvent.OpenArtist(
                            hit.artist.id,
                            hit.artist.name,
                            hit.artist.thumbnailUrl,
                        )
                    )
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

    /** Strictly toggles favorite status without triggering any automatic download. */
    fun onToggleFavorite(track: Track) {
        viewModelScope.launch {
            favoriteTracksStore.toggle(track)
        }
    }

    fun onAddToPlaylist(playlistId: String, track: Track) {
        viewModelScope.launch {
            localPlaylistsStore.addTrack(playlistId, track)
        }
    }

    fun onDownloadTrack(track: Track) {
        trackDownloader.download(track)
    }

    fun onRemoveDownload(track: Track) {
        trackDownloader.deleteDownload(track)
    }

    fun onOpenArtistByName(artistName: String) {
        viewModelScope.launch {
            // Mais parecido por Levenshtein, nunca mais o primeiro da busca
            val found = runCatching {
                ytMusicRepository.resolveArtist(artistName)
            }.getOrNull()
            if (found != null) {
                _navigationEvent.emit(NavigationEvent.OpenArtist(found.id, found.name, found.thumbnailUrl))
            }
        }
    }

    /** FIX9: Clique em artista DENTRO de uma faixa no Search. Igual ao TrackMenuViewModel. */
    fun onOpenArtistForTrack(track: Track, artistName: String) {
        // Video do YouTube: o "artista" e o CANAL. Nunca resolve por nome no YT Music.
        if (track.source == Source.YOUTUBE) {
            onOpenChannelForTrack(track)
            return
        }
        viewModelScope.launch {
            val found = runCatching {
                ytMusicRepository.resolveArtist(
                    artistName,
                    trackTitle = track.title,
                    albumTitle = track.album?.takeIf { it.isNotBlank() },
                )
            }.getOrNull()
            if (found != null) {
                _navigationEvent.emit(NavigationEvent.OpenArtist(found.id, found.name, found.thumbnailUrl))
            }
        }
    }

    /** FIX9: Canal real do video no Search. 1) InnerTube player -> UC. 2) NewPipe StreamExtractor -> UC. Sem nome. */
    fun onOpenChannelForTrack(track: Track) {
        viewModelScope.launch {
            val fromInnerTube = runCatching { ytMusicRepository.getArtistFromVideo(track.id) }.getOrNull()
            if (fromInnerTube != null) {
                _navigationEvent.emit(NavigationEvent.OpenArtist(fromInnerTube.id, fromInnerTube.name, fromInnerTube.thumbnailUrl))
                return@launch
            }
            val channel = runCatching { youTubeRepository.getChannelForVideo(track.id) }.getOrNull()
            if (channel != null) {
                _navigationEvent.emit(NavigationEvent.OpenArtist(channel.channelId, channel.channelName, channel.avatarUrl))
            }
            // channel == null -> nao navega. Nunca chuta artista por nome.
        }
    }

    private fun playRadio(seed: Track) {
        if (_uiState.value.isBuildingQueue) return
        _uiState.update { it.copy(isBuildingQueue = true) }
        viewModelScope.launch { recentPlayedStore.add(seed) }
        viewModelScope.launch {
            val queue = runCatching { musicRepository.buildRadioQueue(seed) }.getOrDefault(listOf(seed))
            _uiState.update { it.copy(isBuildingQueue = false) }
            // Rádio infinito: startRadio() faz a fila crescer sozinha quando está acabando.
            playerController.startRadio(seed, queue)
        }
    }

    private fun runSearch(query: String, filter: SearchFilter, isRefresh: Boolean, loadMore: Boolean) {
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
                    filterCache[filter] = CachedTab(query, result.hits, result.hasMore)
                    // Só mostra se a query/aba ainda for essa (prefetch ou troca de aba
                    // no meio da busca não pode sobrescrever a aba atual).
                    if (_uiState.value.query.trim() == query.trim() && _uiState.value.selectedFilter == filter) {
                        _uiState.update { it.copy(isLoading = false, isRefreshing = false, hits = result.hits, hasMore = result.hasMore) }
                    }
                }
                is SearchResult.Failure -> {
                    if (_uiState.value.query.trim() == query.trim() && _uiState.value.selectedFilter == filter) {
                        _uiState.update { it.copy(isLoading = false, isRefreshing = false, errorMessage = result.message) }
                    }
                }
            }
        }
    }

}
