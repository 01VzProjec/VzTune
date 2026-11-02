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
        )
    )
    val uiState: StateFlow<MediaListUiState> = _uiState.asStateFlow()

    init {
        loadContent()
    }

    private fun loadContent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                if (route.isAlbum) {
                    val albumResult = ytMusicRepository.browseAlbum(route.id)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            title = albumResult.title ?: it.title,
                            subtitle = albumResult.artist ?: it.subtitle,
                            thumbnailUrl = albumResult.coverUrl ?: it.thumbnailUrl,
                            tracks = albumResult.tracks,
                        )
                    }
                } else {
                    val tracks = ytMusicRepository.browseTracks(route.id)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            tracks = tracks,
                            // O mosaico já veio no route ou pode ser buscado se necessário
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Erro ao carregar: ${e.message}"
                    )
                }
            }
        }
    }

    fun onTrackClick(track: Track) {
        playerController.play(track)
    }

    fun onPlayAll() {
        val tracks = _uiState.value.tracks
        if (tracks.isNotEmpty()) {
            playerController.play(tracks.first())
        }
    }
}