package com.vztechcore.vztune.ui.screens.medialist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.player.PlayerController
import com.vztechcore.vztune.data.ytmusic.YTMusicRepository
import com.vztechcore.vztune.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
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
        loadContent()
    }

    private fun loadContent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                if (route.isAlbum) {
                    val album = ytMusicRepository.browseAlbum(route.id)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            title = album.title ?: it.title,
                            subtitle = album.artist
                                ?.takeIf { a -> a != "Desconhecido" }
                                ?: it.subtitle,
                            thumbnailUrl = album.coverUrl ?: it.thumbnailUrl,
                            description = album.description,
                            allTracks = album.tracks,
                        )
                    }
                } else {
                    val playlist = ytMusicRepository.browsePlaylist(route.id)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            title = playlist.title ?: it.title,
                            subtitle = playlist.owner ?: it.subtitle,
                            thumbnailUrl = playlist.coverUrl ?: it.thumbnailUrl,
                            description = playlist.description,
                            allTracks = playlist.tracks,
                        )
                    }
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = "Erro: ${e.message}") }
            }
        }
    }

    fun onTabSelected(tab: MediaListTab) {
        if (tab == _uiState.value.selectedTab) return
        _uiState.update { it.copy(selectedTab = tab) }
    }

    /**
     * Tap a track -> build a queue starting at THAT position, using the
     * *filtered* tracks of the current tab. So:
     *   - Tudo     -> mixed songs + videos, from tapped index to the end
     *   - Músicas  -> only songs, from tapped index
     *   - Vídeos   -> only videos, from tapped index
     *   - Album    -> album tracks in order (no tabs, so filteredTracks == all)
     */
    fun onTrackClick(track: Track) {
        val tracks = _uiState.value.filteredTracks
        val idx = tracks.indexOfFirst { it.id == track.id && it.source == track.source }
        if (idx >= 0) {
            playerController.playQueue(tracks, idx)
        } else {
            playerController.playQueue(listOf(track), 0)
        }
    }

    fun onPlayAll() {
        val tracks = _uiState.value.filteredTracks
        if (tracks.isNotEmpty()) playerController.playQueue(tracks, 0)
    }

    fun onShuffle() {
        val shuffled = _uiState.value.filteredTracks.shuffled()
        if (shuffled.isNotEmpty()) playerController.playQueue(shuffled, 0)
    }
}