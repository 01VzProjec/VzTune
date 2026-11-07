package com.vztechcore.vztune.ui.screens.medialist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SortOrder
import com.vztechcore.vztune.data.player.PlayerController
import com.vztechcore.vztune.data.prefs.MediaListPreferencesStore
import com.vztechcore.vztune.data.ytmusic.YTMusicRepository
import com.vztechcore.vztune.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MediaListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val ytMusicRepository: YTMusicRepository,
    private val playerController: PlayerController,
    private val store: MediaListPreferencesStore,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Routes.MediaList>()

    private val _uiState = MutableStateFlow(
        MediaListUiState(
            title = route.title,
            subtitle = route.subtitle,
            thumbnailUrl = route.thumbnailUrl,
            isAlbum = route.isAlbum,
        )
    )
    val uiState: StateFlow<MediaListUiState> = _uiState.asStateFlow()

    init {
        val savedSort = store.getSortOrder(route.id)
        val savedManual = store.getManualOrder(route.id)
        _uiState.update { it.copy(sortOrder = savedSort, manualOrder = savedManual) }
        loadContent()
    }

    private fun loadContent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                if (route.isAlbum) {
                    val album = ytMusicRepository.browseAlbum(route.id)

                    // Reject numbers and junk, prefer the subtitle coming from the card
                    val resolvedArtist = listOfNotNull(
                        album.artist,
                        route.subtitle,
                        _uiState.value.subtitle
                    ).firstOrNull { isValidOwner(it) }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            title = album.title ?: it.title,
                            subtitle = resolvedArtist ?: it.subtitle,
                            owner = resolvedArtist,
                            ownerAvatarUrl = null,
                            thumbnailUrl = album.coverUrl ?: it.thumbnailUrl,
                            description = album.description,
                            views = album.views,
                            allTracks = album.tracks,
                        )
                    }
                } else {
                    var playlist = ytMusicRepository.browsePlaylist(route.id)
                    if (playlist.tracks.isEmpty()) {
                        delay(1000)
                        playlist = ytMusicRepository.browsePlaylist(route.id)
                    }

                    // Same here: pick the first candidate that is a REAL account name.
                    // If YTM returns "20 songs" or "1.2M views", isValidOwner rejects it.
                    // The owner coming from the card (same name shown in the list) wins over
                    // the parsed header, which may contain labels such as "21 itens".
                    val resolvedOwner = listOfNotNull(
                        route.subtitle,
                        playlist.owner,
                        _uiState.value.subtitle
                    ).firstOrNull { isValidOwner(it) }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            title = playlist.title ?: it.title,
                            subtitle = resolvedOwner ?: it.subtitle,
                            owner = resolvedOwner,
                            ownerAvatarUrl = null,
                            year = playlist.year,
                            views = playlist.views,
                            originalTrackCount = playlist.trackCount,
                            thumbnailUrl = playlist.coverUrl ?: it.thumbnailUrl,
                            description = playlist.description,
                            mosaicThumbnails = playlist.tracks
                                .asSequence()
                                .mapNotNull { t -> t.thumbnailUrl }
                                .distinct()
                                .take(4)
                                .toList(),
                            allTracks = playlist.tracks,
                        )
                    }
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = "Erro: ${e.message}") }
            }
        }
    }

    /**
     * Checks whether a string looks like a real account/channel name.
     * Rejects counts ("12 songs"), view counts ("1.2M views"), total durations
     * ("1h 30min") and placeholders ("Creator", "Unknown").
     * Uses whole-string patterns so real names containing "min", "hora", "total"
     * or "track" (e.g. "Admin Music", "Total Tracks") are no longer discarded.
     */
    private fun isValidOwner(text: String?): Boolean {
        if (text.isNullOrBlank()) return false

        val trimmed = text.trim()
        if (trimmed.lowercase() in PLACEHOLDER_OWNERS) return false
        if (NON_OWNER_PATTERNS.any { it.matches(trimmed) }) return false
        if (trimmed.toIntOrNull() != null) return false

        return true
    }

    fun onTabSelected(tab: MediaListTab) {
        if (tab == _uiState.value.selectedTab) return
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun onSortOrderSelected(order: SortOrder) {
        if (order == _uiState.value.sortOrder) return
        _uiState.update { it.copy(sortOrder = order, isShuffling = false) }
        store.setSortOrder(route.id, order)
    }

    fun onReorder(fromIndex: Int, toIndex: Int) {
        val currentTracks = _uiState.value.getDisplayTracks()
        if (fromIndex !in currentTracks.indices) return
        if (toIndex !in currentTracks.indices) return
        val newList = currentTracks.toMutableList()
        val item = newList.removeAt(fromIndex)
        newList.add(toIndex, item)
        val newOrderIds = newList.map { it.id }
        _uiState.update { it.copy(manualOrder = newOrderIds, sortOrder = SortOrder.MANUAL) }
        store.setManualOrder(route.id, newOrderIds)
        store.setSortOrder(route.id, SortOrder.MANUAL)
    }

    fun startShuffle() { _uiState.update { it.copy(isShuffling = true) } }

    fun shuffleOnce() {
        val currentTracks = _uiState.value.allTracks
        if (currentTracks.isEmpty()) return
        val newOrderIds = currentTracks.map { it.id }.shuffled()
        _uiState.update { it.copy(manualOrder = newOrderIds, sortOrder = SortOrder.SHUFFLE) }
    }

    fun stopShuffle() {
        _uiState.update { it.copy(isShuffling = false) }
        store.setManualOrder(route.id, _uiState.value.manualOrder)
        store.setSortOrder(route.id, SortOrder.SHUFFLE)
    }

    fun onTrackClick(track: Track) {
        val sorted = _uiState.value.getDisplayTracks()
        val idx = sorted.indexOfFirst { it.id == track.id && it.source == track.source }
        if (idx >= 0) playerController.playQueue(sorted, idx)
        else playerController.playQueue(listOf(track), 0)
    }

    fun onPlayNextClick(track: Track) {
        playerController.addToQueueNext(track)
    }

    fun onPlayAll() {
        val sorted = _uiState.value.getDisplayTracks()
        if (sorted.isNotEmpty()) playerController.playQueue(sorted, 0)
    }

    fun onShuffle() {
        val shuffled = _uiState.value.filteredTracks.shuffled()
        if (shuffled.isNotEmpty()) playerController.playQueue(shuffled, 0)
    }
}

private val PLACEHOLDER_OWNERS = setOf(
    "criador", "creator", "unknown", "desconhecido", "álbum", "album", "playlist",
)

private val NON_OWNER_PATTERNS = listOf(
    // "20 songs", "12 músicas", "1.2M views", "3 mil visualizações"
    Regex(
        "^\\d[\\d.,]*\\s*(k|m|b|mil|mi|bi)?\\s*(músicas?|musicas?|songs?|faixas?|tracks?|itens?|items?|vídeos?|videos?|episódios?|episodes?|views?|visualizações|visualizacoes|reproduções|reproducoes|plays?)\\b.*$",
        RegexOption.IGNORE_CASE,
    ),
    // "1h 30min", "1 hora e 20 minutos total"
    Regex(
        "^(\\d+\\s*(h|hr|hrs|hora|horas|hour|hours|min|mins|minuto|minutos|minute|minutes|s|seg|sec)\\b[\\s,.]*(e|and)?[\\s,.]*)+(total)?$",
        RegexOption.IGNORE_CASE,
    ),
)
