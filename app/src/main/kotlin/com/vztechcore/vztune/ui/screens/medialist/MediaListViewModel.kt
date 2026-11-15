package com.vztechcore.vztune.ui.screens.medialist

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.prefs.store.LocalPlaylistsStore
import com.vztechcore.vztune.data.prefs.store.PlaylistCoverStorage
import com.vztechcore.vztune.data.repository.MediaListRepository
import com.vztechcore.vztune.domain.playback.NowPlayingController
import com.vztechcore.vztune.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the playlist / album detail screen.
 *
 * It loads the tracks, handles sorting, reordering, favorites and local
 * playlist management, including cover changes.
 *
 * Cover rules for local playlists:
 * - A photo cover is stored as a file path.
 * - A Vz color cover is stored as a "vz_color:" token.
 * - Changing the color replaces any photo, and picking a photo replaces any color.
 */
@HiltViewModel
class MediaListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MediaListRepository,
    private val localStore: LocalPlaylistsStore,
    private val coverStorage: PlaylistCoverStorage,
    private val nowPlaying: NowPlayingController,
) : ViewModel() {

    private val route: Routes.MediaList = savedStateHandle.toRoute()

    private val _uiState = MutableStateFlow(MediaListUiState.fromRoute(route))
    val uiState: StateFlow<MediaListUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    init {
        load()
        observeLocalPlaylist()
    }

    /** Loads the playlist / album tracks from the repository. */
    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = if (route.isAlbum) {
                repository.getAlbum(route.id)
            } else {
                repository.getPlaylist(route.id)
            }
            result.onSuccess { data ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        allTracks = data.tracks,
                        displayedTracks = data.tracks,
                        title = data.title,
                        subtitle = data.subtitle,
                        thumbnailUrl = data.thumbnailUrl ?: it.thumbnailUrl,
                        owner = data.owner,
                        description = data.description,
                        moreAlbumsByArtist = data.moreAlbumsByArtist,
                        isAlbum = route.isAlbum,
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * Observes the local playlist so cover, title and track order stay in sync.
     * If a local playlist has no cover at all, a random Vz color token is
     * assigned once, so the Library card and the detail screen match.
     */
    private fun observeLocalPlaylist() {
        viewModelScope.launch {
            localStore.playlists.collect { playlists ->
                val local = playlists.firstOrNull { it.id == route.id } ?: return@collect
                if (local.coverPath.isNullOrBlank()) {
                    localStore.ensureRandomDefaultCover(local.id)
                }
                _uiState.update {
                    it.copy(
                        title = local.name,
                        thumbnailUrl = local.coverPath,
                        isLocal = true,
                    )
                }
            }
        }
    }

    /** Switches the selected tab. */
    fun onTabSelected(tab: MediaListTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    /** Plays a track. */
    fun onTrackClick(track: Track) {
        nowPlaying.play(track)
    }

    /** Plays all tracks from the start. */
    fun onPlayAll() {
        val tracks = _uiState.value.displayedTracks
        if (tracks.isNotEmpty()) nowPlaying.playAll(tracks, 0)
    }

    /** Plays all tracks in shuffled order. */
    fun onShuffle() {
        val tracks = _uiState.value.displayedTracks
        if (tracks.isNotEmpty()) nowPlaying.playShuffled(tracks)
    }

    /** Applies a sort order to the displayed tracks. */
    fun onSortOrderSelected(order: MediaListSortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
    }

    /** Moves a track inside a local playlist. */
    fun onReorder(from: Int, to: Int) {
        viewModelScope.launch {
            localStore.reorderTrack(route.id, from, to)
        }
    }

    /** Starts shuffle mode. */
    fun startShuffle() {
        onShuffle()
    }

    /** Shuffles once. */
    fun shuffleOnce() {
        onShuffle()
    }

    /** Stops shuffle mode. */
    fun stopShuffle() {
        // Shuffle is a one-shot action in this screen, nothing to stop.
    }

    /** Queues a track to play next. */
    fun onPlayNextClick(track: Track) {
        nowPlaying.playNext(track)
    }

    /** Toggles the favorite state of the playlist / album. */
    fun onFavoriteClick() {
        _uiState.update { it.copy(isFavorite = !it.isFavorite) }
    }

    /** Queues all tracks to play next. */
    fun onPlayNextAll() {
        nowPlaying.playNextAll(_uiState.value.displayedTracks)
    }

    /** Adds all tracks to the queue. */
    fun onAddToQueueAll() {
        nowPlaying.addToQueue(_uiState.value.displayedTracks)
    }

    /** Downloads all tracks. */
    fun onDownloadAll() {
        viewModelScope.launch {
            repository.downloadAll(_uiState.value.displayedTracks)
        }
    }

    /** Navigates to the artist of the current album. */
    fun onOpenArtist() {
        viewModelScope.launch {
            val artist = repository.resolveArtistForAlbum(route.id, _uiState.value.owner)
            if (artist != null) {
                _navigationEvent.emit(NavigationEvent.OpenArtist(artist))
            }
        }
    }

    /** Navigates to an artist by name. */
    fun onOpenArtistByName(name: String) {
        viewModelScope.launch {
            val artist = repository.resolveArtistByName(name)
            if (artist != null) {
                _navigationEvent.emit(NavigationEvent.OpenArtist(artist))
            }
        }
    }

    /** Deletes the local playlist and closes the screen. */
    fun onDeleteLocalPlaylist() {
        viewModelScope.launch {
            localStore.delete(route.id)
            _navigationEvent.emit(NavigationEvent.CloseScreen)
        }
    }

    /** Renames the local playlist. */
    fun onRenameLocalPlaylist(newTitle: String) {
        viewModelScope.launch {
            localStore.rename(route.id, newTitle)
        }
    }

    /**
     * Changes the local playlist cover to a photo.
     * The photo path replaces any previous Vz color token.
     */
    fun onChangeLocalPlaylistCover(uri: Uri) {
        viewModelScope.launch {
            val saved = coverStorage.save(route.id, uri) ?: return@launch
            localStore.updateCover(route.id, saved)
        }
    }

    /**
     * Changes the local playlist cover to a Vz color.
     * The color token replaces any previous photo, so the Vz logo with the
     * chosen color becomes the official cover in the Library and here.
     */
    fun onChangeCoverColor(colorToken: String) {
        viewModelScope.launch {
            if (!_uiState.value.isLocal) return@launch
            localStore.updateCover(route.id, colorToken)
        }
    }

    /** Navigation events emitted to the navigation host. */
    sealed interface NavigationEvent {
        data class OpenArtist(val artist: Artist) : NavigationEvent
        data object CloseScreen : NavigationEvent
    }
}
