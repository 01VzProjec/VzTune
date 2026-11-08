package com.vztechcore.vztune.ui.screens.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.player.PlayerController
import com.vztechcore.vztune.data.prefs.FollowedArtist
import com.vztechcore.vztune.data.prefs.FollowedArtistsStore
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
class ArtistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val ytMusicRepository: YTMusicRepository,
    private val playerController: PlayerController,
    private val followedStore: FollowedArtistsStore,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Routes.Artist>()

    private val _uiState = MutableStateFlow(
        ArtistUiState(
            artistId = route.id,
            name = route.name,
            thumbnailUrl = route.thumbnailUrl,
        )
    )
    val uiState: StateFlow<ArtistUiState> = _uiState.asStateFlow()

    init {
        loadContent()
        viewModelScope.launch {
            followedStore.isFollowing(route.id).collect { following ->
                _uiState.update { it.copy(isFollowing = following) }
            }
        }
    }

    private fun loadContent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { ytMusicRepository.browseArtist(route.id) }
                .onSuccess { r ->
                    val isEmpty = r.topSongs.isEmpty() && r.albums.isEmpty() && r.singles.isEmpty() &&
                        r.videos.isEmpty() && r.featured.isEmpty() && r.relatedArtists.isEmpty()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = if (isEmpty) "Não foi possível carregar este artista." else null,
                            // Keep the name/photo from the card when the page could not provide them
                            name = r.name.takeUnless { n -> n == "Artista" || n == "Unknown Artist" } ?: it.name,
                            thumbnailUrl = r.thumbnailUrl ?: it.thumbnailUrl,
                            subscribers = r.subscribers,
                            views = r.views,
                            description = r.description,
                            topSongs = r.topSongs,
                            songsMore = r.songsMore,
                            albums = r.albums,
                            albumsMore = r.albumsMore,
                            singles = r.singles,
                            singlesMore = r.singlesMore,
                            videos = r.videos,
                            videosMore = r.videosMore,
                            featured = r.featured,
                            featuredMore = r.featuredMore,
                            relatedArtists = r.relatedArtists,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Não foi possível carregar este artista.") }
                }
        }
    }

    /** Plays a random track of the artist (popular songs first, videos as fallback). */
    fun onRandomClick() {
        val state = _uiState.value
        val pool = state.topSongs.ifEmpty { state.videos }
        if (pool.isEmpty()) return
        playerController.playQueue(pool.shuffled(), 0)
    }

    fun onFollowClick() {
        val state = _uiState.value
        viewModelScope.launch {
            followedStore.toggle(FollowedArtist(state.artistId, state.name, state.thumbnailUrl))
        }
    }

    fun onSongClick(track: Track) {
        val songs = _uiState.value.topSongs
        val idx = songs.indexOfFirst { it.id == track.id }
        if (idx >= 0) playerController.playQueue(songs, idx) else playerController.playQueue(listOf(track), 0)
    }

    fun onVideoClick(track: Track) {
        val videos = _uiState.value.videos
        val idx = videos.indexOfFirst { it.id == track.id }
        if (idx >= 0) playerController.playQueue(videos, idx) else playerController.playQueue(listOf(track), 0)
    }
}
