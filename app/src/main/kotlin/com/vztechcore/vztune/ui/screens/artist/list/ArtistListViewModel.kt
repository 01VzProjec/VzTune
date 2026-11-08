package com.vztechcore.vztune.ui.screens.artist.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vztechcore.vztune.core.domain.music.ArtistListKind
import com.vztechcore.vztune.core.domain.music.BrowseRef
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
class ArtistListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val ytMusicRepository: YTMusicRepository,
    private val playerController: PlayerController,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Routes.ArtistList>()
    private val kind = runCatching { ArtistListKind.valueOf(route.kind) }.getOrDefault(ArtistListKind.SONGS)
    private val ref = BrowseRef(route.browseId, route.params)

    private val _uiState = MutableStateFlow(
        ArtistListUiState(kind = kind, artistName = route.artistName)
    )
    val uiState: StateFlow<ArtistListUiState> = _uiState.asStateFlow()

    init {
        loadFirstPage()
    }

    private fun loadFirstPage() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val page = runCatching {
                ytMusicRepository.browseArtistList(kind, route.artistName, ref, continuation = null)
            }.getOrNull()

            if (page == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Não foi possível carregar a lista.") }
                return@launch
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    tracks = page.tracks.distinctBy { t -> t.id },
                    albums = page.albums.distinctBy { a -> a.id },
                    playlists = page.playlists.distinctBy { p -> p.id },
                    continuation = page.continuation,
                    errorMessage = if (page.tracks.isEmpty() && page.albums.isEmpty() && page.playlists.isEmpty()) {
                        "Nada encontrado."
                    } else null,
                )
            }
        }
    }

    /** Fetches the next page; called when the user scrolls near the end of the list. */
    fun onLoadMore() {
        val state = _uiState.value
        val token = state.continuation ?: return
        if (state.isLoading || state.isLoadingMore) return

        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            val page = runCatching {
                ytMusicRepository.browseArtistList(kind, route.artistName, ref, continuation = token)
            }.getOrNull()

            _uiState.update { current ->
                if (page == null) {
                    // Stop paginating on failure so we don't loop on a broken token
                    current.copy(isLoadingMore = false, continuation = null)
                } else {
                    val next = page.continuation?.takeIf { it != token }
                    current.copy(
                        isLoadingMore = false,
                        tracks = (current.tracks + page.tracks).distinctBy { t -> t.id },
                        albums = (current.albums + page.albums).distinctBy { a -> a.id },
                        playlists = (current.playlists + page.playlists).distinctBy { p -> p.id },
                        continuation = next,
                    )
                }
            }
        }
    }

    fun onTrackClick(track: Track) {
        val tracks = _uiState.value.tracks
        val idx = tracks.indexOfFirst { it.id == track.id }
        if (idx >= 0) playerController.playQueue(tracks, idx) else playerController.playQueue(listOf(track), 0)
    }
}
