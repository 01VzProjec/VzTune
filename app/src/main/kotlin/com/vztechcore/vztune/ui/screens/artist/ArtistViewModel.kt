package com.vztechcore.vztune.ui.screens.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.player.PlayerController
import com.vztechcore.vztune.data.prefs.models.FollowedArtist
import com.vztechcore.vztune.data.prefs.models.LocalPlaylist
import com.vztechcore.vztune.data.prefs.models.StoredTrack
import com.vztechcore.vztune.data.prefs.store.FollowedArtistsStore
import com.vztechcore.vztune.data.prefs.store.LocalPlaylistsStore
import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.data.ytmusic.YTMusicRepository
import com.vztechcore.vztune.data.music.MusicRepository
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
    private val musicRepository: MusicRepository,
    private val playerController: PlayerController,
    private val followedStore: FollowedArtistsStore,
    private val localPlaylistsStore: LocalPlaylistsStore,
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
    // FIX14 REBUILD - Regra: tem faixa de musica = ARTISTA (so InnerTube). Sem musica = CANAL (NewPipe).
    // Antes: juntava os dois mundos e o artista caia como canal. Agora nao mistura mais.
        // FIX19 - Separacao total: quem veio do YouTube fica no YouTube, quem veio do YT Music fica no YT Music.
    // Antes: carregava os dois mundos e se o YT Music tivesse QUALQUER faixa (mesmo de outro artista com nome parecido, ex: musica "TH" dentro do video do canal "th") mostrava o YT Music e descartava o canal. O YT Music tinha preferencia.
    // Agora: route.isYouTubeChannel == true -> SO NewPipe browseChannel, sem InnerTube. false -> SO InnerTube browseArtist, sem NewPipe. Sem fallback pro outro mundo.
    private fun loadContent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            if (route.isYouTubeChannel) {
                val channel = runCatching { musicRepository.browseChannel(route.id) }.getOrNull()
                if (channel != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false, errorMessage = null,
                            name = channel.channelName ?: it.name,
                            thumbnailUrl = channel.avatarUrl ?: it.thumbnailUrl,
                            subscribers = channel.subscriberCount ?: it.subscribers,
                            description = channel.description ?: it.description,
                            topSongs = emptyList(), songsMore = null,
                            albums = emptyList(), albumsMore = null,
                            singles = emptyList(), singlesMore = null,
                            videos = channel.videos, videosMore = null,
                            featured = emptyList(), featuredMore = null, relatedArtists = emptyList(),
                            shorts = emptyList(), // FIX20 - Canal sem Shorts, channelPlaylists = channel.playlists, bannerUrl = channel.bannerUrl ?: it.bannerUrl, isYouTubeChannel = true,
                            channelVideosHasMore = musicRepository.hasMoreChannelVideos(route.id), isLoadingMoreChannelVideos = false,
                            channelPlaylistsHasMore = musicRepository.hasMoreChannelPlaylists(route.id), isLoadingMoreChannelPlaylists = false,
                        )
                    }
                    return@launch
                }
                _uiState.update { it.copy(isLoading = false, errorMessage = "Não foi possível carregar este canal.") }
                return@launch
            }
            // YT Music: SO InnerTube, nunca NewPipe
            val music = runCatching { ytMusicRepository.browseArtist(route.id) }.getOrNull()
            if (music != null && (music.topSongs.isNotEmpty() || music.albums.isNotEmpty() || music.singles.isNotEmpty() || music.videos.isNotEmpty())) {
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
                        shorts = emptyList(), channelPlaylists = emptyList(), bannerUrl = null, isYouTubeChannel = false,
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(isLoading = false, errorMessage = "Não foi possível carregar este artista.") }
        }
    }

/** Tocar tudo: toca todas as musicas do artista em ordem (nao embaralhado). */
    fun onPlayAllClick() {
        val songs = _uiState.value.topSongs
        if (songs.isEmpty()) return
        playerController.playQueue(songs, 0)
    }

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

    // FIX18 - Buscar mais: busca proxima pagina do UU e soma na mesma lista. Mais recentes / Em alta ganham mais itens. Mais antigos se atualiza sozinho porque ele e a mesma lista invertida, sem botao proprio.
    fun loadMoreChannelVideos() {
        val state = _uiState.value
        if (state.isLoadingMoreChannelVideos) return
        if (!state.channelVideosHasMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMoreChannelVideos = true) }
            val more = runCatching { musicRepository.loadMoreChannelVideos(route.id) }.getOrDefault(emptyList())
            val current = _uiState.value.videos
            val merged = (current + more).distinctBy { it.id }
            _uiState.update {
                it.copy(
                    videos = merged,
                    channelVideosHasMore = musicRepository.hasMoreChannelVideos(route.id),
                    isLoadingMoreChannelVideos = false,
                )
            }
        }
    }

    // FIX20 - Buscar mais playlists: proxima pagina do /playlists, soma na mesma lista.
    fun loadMoreChannelPlaylists() {
        val state = _uiState.value
        if (state.isLoadingMoreChannelPlaylists) return
        if (!state.channelPlaylistsHasMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMoreChannelPlaylists = true) }
            val more = runCatching { musicRepository.loadMoreChannelPlaylists(route.id) }.getOrDefault(emptyList())
            val current = _uiState.value.channelPlaylists
            val merged = (current + more).distinctBy { it.id }
            _uiState.update {
                it.copy(
                    channelPlaylists = merged,
                    channelPlaylistsHasMore = musicRepository.hasMoreChannelPlaylists(route.id),
                    isLoadingMoreChannelPlaylists = false,
                )
            }
        }
    }

    fun onVideoClick(track: Track) {
        val videos = _uiState.value.videos
        val idx = videos.indexOfFirst { it.id == track.id }
        if (idx >= 0) playerController.playQueue(videos, idx) else playerController.playQueue(listOf(track), 0)
    }


        private suspend fun loadChannelPlaylistTracks(playlist: Playlist): List<Track> {
        // FIX19 - Playlist do canal: YouTube -> NewPipe, YT Music -> InnerTube. Nunca cruza.
        if (playlist.source == Source.YOUTUBE) {
            return runCatching { musicRepository.getYouTubePlaylistTracks(playlist.id) }.getOrDefault(emptyList())
        }
        if (_uiState.value.isYouTubeChannel) return runCatching { musicRepository.getYouTubePlaylistTracks(playlist.id) }.getOrDefault(emptyList())
        return runCatching { ytMusicRepository.browsePlaylist(playlist.id).tracks }.getOrDefault(emptyList())
    }

    fun onPlaylistPlayNext(playlist: Playlist) {
        viewModelScope.launch {
            val tracks = loadChannelPlaylistTracks(playlist)
            if (tracks.isEmpty()) return@launch
            for (track in tracks.asReversed()) playerController.addToQueueNext(track)
        }
    }

    fun onPlaylistAddToQueue(playlist: Playlist) {
        viewModelScope.launch {
            val tracks = loadChannelPlaylistTracks(playlist)
            for (track in tracks) playerController.addToQueueEnd(track)
        }
    }

    fun onSavePlaylistToLibrary(playlist: Playlist) {
        viewModelScope.launch {
            val tracks = loadChannelPlaylistTracks(playlist)
            val stored = tracks.map { StoredTrack.from(it) }
            localPlaylistsStore.addFirst(LocalPlaylist(id = playlist.id, name = playlist.title, coverPath = playlist.thumbnailUrl, tracks = stored))
        }
    }
}
