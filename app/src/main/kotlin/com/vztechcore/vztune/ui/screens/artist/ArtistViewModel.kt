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
            isYouTubeChannel = route.isYouTubeChannel,
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

    // FIX8_ARTIST - Se UC tem canal real, o canal e a verdade. Se YT Music devolver nome totalmente diferente, ignora YT Music.
    // Antes: se YT Music devolvesse qualquer artista nao-vazio (ex: Moldrin para JazzGhost), mostrava o errado e nunca tentava o canal.
    private fun loadContent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val isChannelId = route.id.startsWith("UC")
            val channel = if (isChannelId) runCatching { youTubeRepository.browseChannel(route.id) }.getOrNull() else null
            val music = runCatching { ytMusicRepository.browseArtist(route.id) }.getOrNull()
            val musicEmpty = music == null || (music.topSongs.isEmpty() && music.albums.isEmpty() && music.singles.isEmpty() && music.videos.isEmpty() && music.featured.isEmpty() && music.relatedArtists.isEmpty())
            if (channel != null && channel.channelName != null) {
                val channelNameNorm = channel.channelName.lowercase().trim()
                val musicNameNorm = (music?.name ?: "").lowercase().trim()
                val namesMatch = !musicEmpty && (musicNameNorm.isNotBlank() && (channelNameNorm.contains(musicNameNorm) || musicNameNorm.contains(channelNameNorm) || channelNameNorm.split(" ").firstOrNull() == musicNameNorm.split(" ").firstOrNull()))
                if (!musicEmpty && namesMatch) {
                    // Mesmo artista nos dois mundos: mostra YT Music + videos do canal
                    _uiState.update {
                        it.copy(
                            isLoading = false, errorMessage = null,
                            name = music.name.takeUnless { n -> n == "Artista" || n == "Unknown Artist" } ?: it.name,
                            thumbnailUrl = music.thumbnailUrl ?: channel.avatarUrl ?: it.thumbnailUrl,
                            subscribers = music.subscribers ?: channel.subscriberCount ?: it.subscribers,
                            views = music.views, description = music.description,
                            topSongs = music.topSongs, songsMore = music.songsMore,
                            albums = music.albums, albumsMore = music.albumsMore,
                            singles = music.singles, singlesMore = music.singlesMore,
                            videos = if (channel.videos.isNotEmpty()) channel.videos else music.videos,
                            videosMore = music.videosMore, featured = music.featured, featuredMore = music.featuredMore,
                            relatedArtists = music.relatedArtists,
                        )
                    }
                    return@launch
                }
                if (channel.videos.isNotEmpty() || channel.shorts.isNotEmpty() || channel.channelName != null) {
                    // Canal real existe: mostra ele, mesmo que YT Music tenha devolvido artista diferente ou vazio.
                    _uiState.update {
                        it.copy(
                            isLoading = false, errorMessage = null,
                            name = channel.channelName ?: it.name,
                            thumbnailUrl = channel.avatarUrl ?: it.thumbnailUrl,
                            subscribers = channel.subscriberCount ?: it.subscribers,
                            description = channel.description ?: it.description,
                            videos = channel.videos,
                        )
                    }
                    return@launch
                }
            }
            if (!musicEmpty && music != null) {
                _uiState.update {
                    it.copy(
                        isLoading = false, errorMessage = null,
                        name = music.name.takeUnless { n -> n == "Artista" || n == "Unknown Artist" } ?: it.name,
                        thumbnailUrl = music.thumbnailUrl ?: it.thumbnailUrl,
                        subscribers = music.subscribers, views = music.views, description = music.description,
                        topSongs = music.topSongs, songsMore = music.songsMore,
                        albums = music.albums, albumsMore = music.albumsMore,
                        singles = music.singles, singlesMore = music.singlesMore,
                        videos = music.videos, videosMore = music.videosMore,
                        featured = music.featured, featuredMore = music.featuredMore,
                        relatedArtists = music.relatedArtists,
                    )
                }
                return@launch
            }
            if (channel != null) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = null, name = channel.channelName ?: it.name, thumbnailUrl = channel.avatarUrl ?: it.thumbnailUrl, subscribers = channel.subscriberCount ?: it.subscribers, videos = channel.videos)
                }
                return@launch
            }
            _uiState.update { it.copy(isLoading = false, errorMessage = "Não foi possível carregar este canal.") }
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
            followedStore.toggle(FollowedArtist(state.artistId, state.name, state.thumbnailUrl, isYouTubeChannel = state.isYouTubeChannel))
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
