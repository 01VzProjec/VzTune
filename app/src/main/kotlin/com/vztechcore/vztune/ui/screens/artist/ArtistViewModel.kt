package com.vztechcore.vztune.ui.screens.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.player.PlayerController
import com.vztechcore.vztune.data.prefs.models.FollowedArtist
import com.vztechcore.vztune.data.prefs.store.FollowedArtistsStore
import com.vztechcore.vztune.data.youtube.YouTubeRepository
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
    private val youTubeRepository: YouTubeRepository,
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
            val result = runCatching { ytMusicRepository.browseArtist(route.id) }.getOrNull()
            if (result != null) {
                val isEmpty = result.topSongs.isEmpty() && result.albums.isEmpty() && result.singles.isEmpty() &&
                    result.videos.isEmpty() && result.featured.isEmpty() && result.relatedArtists.isEmpty()
                if (!isEmpty) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            // Keep the name/photo from the card when the page could not provide them
                            name = result.name.takeUnless { n -> n == "Artista" || n == "Unknown Artist" } ?: it.name,
                            thumbnailUrl = result.thumbnailUrl ?: it.thumbnailUrl,
                            subscribers = result.subscribers,
                            views = result.views,
                            description = result.description,
                            topSongs = result.topSongs,
                            songsMore = result.songsMore,
                            albums = result.albums,
                            albumsMore = result.albumsMore,
                            singles = result.singles,
                            singlesMore = result.singlesMore,
                            videos = result.videos,
                            videosMore = result.videosMore,
                            featured = result.featured,
                            featuredMore = result.featuredMore,
                            relatedArtists = result.relatedArtists,
                        )
                    }
                    return@launch
                }
            }
            // FIX 6: o YT Music NÃO tem página de artista pra esse UC...
            // É um canal puro de vídeo (podcast, gamer, vlogger...), não um artista.
            // NUNCA busca por nome (era isso que abria artista parecido).
            // Abre o CANAL REAL pelo UC... via NewPipe e mostra OS VÍDEOS DELE.
            // O nome/foto vêm da rota (que já veio do canal real do vídeo), não de busca.
            val channel = runCatching { youTubeRepository.browseChannel(route.id) }.getOrNull()
            if (channel != null && channel.videos.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = null,
                        thumbnailUrl = channel.avatarUrl ?: it.thumbnailUrl,
                        subscribers = channel.subscriberCount ?: it.subscribers,
                        videos = channel.videos,
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Não foi possível carregar este canal.") }
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
