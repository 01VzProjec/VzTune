package com.vztechcore.vztune.ui.screens.library.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.player.PlayerController
import com.vztechcore.vztune.data.prefs.store.FavoriteTracksStore
import com.vztechcore.vztune.data.prefs.store.FollowedArtistsStore
import com.vztechcore.vztune.data.prefs.store.MostPlayedStore
import com.vztechcore.vztune.ui.navigation.Routes
import com.vztechcore.vztune.ui.screens.library.LibraryShortcut
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Backs the lists opened from the Library shortcut cards (favorites, most played, followed...). */
@HiltViewModel
class LibraryListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    favoriteTracksStore: FavoriteTracksStore,
    mostPlayedStore: MostPlayedStore,
    followedArtistsStore: FollowedArtistsStore,
    private val playerController: PlayerController,
) : ViewModel() {

    private val shortcut = runCatching {
        LibraryShortcut.valueOf(savedStateHandle.toRoute<Routes.LibraryList>().kind)
    }.getOrDefault(LibraryShortcut.FAVORITES)

    private val tracksFlow: Flow<List<Track>> = when (shortcut) {
        LibraryShortcut.FAVORITES -> favoriteTracksStore.tracks
        LibraryShortcut.MOST_PLAYED -> mostPlayedStore.tracks
        LibraryShortcut.FOLLOWED, LibraryShortcut.DOWNLOADS -> flowOf(emptyList())
    }

    val uiState: StateFlow<LibraryListUiState> = combine(
        tracksFlow,
        followedArtistsStore.items,
    ) { tracks, artists ->
        LibraryListUiState(shortcut = shortcut, tracks = tracks, artists = artists)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryListUiState(shortcut))

    fun onTrackClick(track: Track) {
        val tracks = uiState.value.tracks
        val index = tracks.indexOfFirst { it.id == track.id && it.source == track.source }
        if (index >= 0) playerController.playQueue(tracks, index)
        else playerController.playQueue(listOf(track), 0)
    }

    /** Shuffle icon — plays a random track from the list. */
    fun onShuffleClick() {
        val tracks = uiState.value.tracks
        if (tracks.isEmpty()) return
        playerController.playQueue(tracks.shuffled(), 0)
    }

    /** Blue Play pill — plays from the first track. */
    fun onPlayAllClick() {
        val tracks = uiState.value.tracks
        if (tracks.isNotEmpty()) playerController.playQueue(tracks, 0)
    }
}