package com.vztechcore.vztune.ui.screens.trackinfo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.music.MusicRepository
import com.vztechcore.vztune.data.prefs.FavoriteTracksStore
import com.vztechcore.vztune.data.prefs.LocalPlaylistsStore
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
class TrackInfoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val musicRepository: MusicRepository,
    private val ytMusicRepository: YTMusicRepository,
    private val favoriteTracksStore: FavoriteTracksStore,
    private val localPlaylistsStore: LocalPlaylistsStore,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Routes.TrackInfo>()

    private val source = runCatching { Source.valueOf(route.sourceName) }.getOrDefault(Source.YOUTUBE_MUSIC)
    private val track = Track(
        id = route.id,
        title = route.title,
        artist = route.artist,
        album = route.album,
        thumbnailUrl = route.thumbnailUrl,
        durationSeconds = route.durationSeconds,
        source = source,
    )

    private val _uiState = MutableStateFlow(
        TrackInfoUiState(
            isLoading = true,
            title = route.title,
            artist = route.artist,
            album = route.album,
            thumbnailUrl = route.thumbnailUrl,
            isVideo = source == Source.YOUTUBE,
            durationSeconds = route.durationSeconds,
            sourceUrl = buildSourceUrl(route.id, source),
        )
    )
    val uiState: StateFlow<TrackInfoUiState> = _uiState.asStateFlow()

    init {
        loadTechnicalInfo()
        loadInnerTubeDetails()
        viewModelScope.launch {
            favoriteTracksStore.containsTrack(track).collect { favorite ->
                _uiState.update { it.copy(isFavorite = favorite) }
            }
        }
        viewModelScope.launch {
            localPlaylistsStore.items.collect { playlists ->
                _uiState.update { it.copy(localPlaylists = playlists) }
            }
        }
    }

    fun onFavoriteClick() {
        viewModelScope.launch { favoriteTracksStore.toggle(track) }
    }

    fun onAddToPlaylist(playlistId: String) {
        viewModelScope.launch { localPlaylistsStore.addTrack(playlistId, track) }
    }

    private fun loadTechnicalInfo() {
        viewModelScope.launch {
            val info = runCatching { musicRepository.getTechnicalInfo(track) }.getOrNull()
            if (info != null) {
                _uiState.update {
                    it.copy(
                        itag = info.itag ?: "Desconhecido",
                        mimeType = info.mimeType ?: "Desconhecido",
                        quality = info.quality ?: "Desconhecido",
                    )
                }
            } else {
                _uiState.update { it.copy(itag = "Desconhecido", mimeType = "Desconhecido", quality = "Desconhecido") }
            }
        }
    }

    private fun loadInnerTubeDetails() {
        viewModelScope.launch {
            val details = runCatching { ytMusicRepository.getVideoDetails(track.id) }.getOrNull()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    viewCount = details?.viewCount,
                    likeCount = details?.likeCount,
                    description = details?.description,
                )
            }
        }
    }

    private fun buildSourceUrl(id: String, s: Source): String {
        return if (s == Source.YOUTUBE) "https://www.youtube.com/watch?v=$id"
        else "https://music.youtube.com/watch?v=$id"
    }
}
