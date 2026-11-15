package com.vztechcore.vztune.ui.screens.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.prefs.store.LocalPlaylistsStore
import com.vztechcore.vztune.data.prefs.store.PlaylistCoverStorage
import com.vztechcore.vztune.data.repository.LibraryRepository
import com.vztechcore.vztune.domain.playback.NowPlayingController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Library screen.
 *
 * Cover rule for local playlists:
 * - If the user picked a photo, the saved photo path is stored.
 * - If the user did not pick a photo, the chosen Vz color token is stored.
 * - The same coverPath is exposed as imageUrl, so the Library card and the
 *   playlist detail screen always show the same cover.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val localStore: LocalPlaylistsStore,
    private val coverStorage: PlaylistCoverStorage,
    private val nowPlaying: NowPlayingController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.favoriteTracks,
                repository.recentTracks,
                localStore.playlists,
            ) { favorites, recents, locals ->
                Triple(favorites, recents, locals)
            }.collect { (favorites, recents, locals) ->
                val localItems = locals.map { it.toItem() }
                _uiState.update { current ->
                    current.copy(
                        localPlaylists = localItems,
                        favoriteCount = favorites.size,
                        recentTracks = recents.take(10),
                    )
                }
            }
        }
    }

    /** Switches the selected tab in the Library. */
    fun onTabSelected(tab: LibraryTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    /** Plays a track from the recent list. */
    fun onTrackClick(track: Track) {
        nowPlaying.play(track)
    }

    /**
     * Creates a local playlist.
     *
     * @param name playlist name.
     * @param coverUri optional photo picked by the user. When present it wins.
     * @param colorToken optional Vz color token like "vz_color:RED".
     *   Used only when [coverUri] is null. When both are null a random token is used.
     */
    fun createPlaylist(name: String, coverUri: Uri?, colorToken: String?) {
        viewModelScope.launch {
            val id = newLocalPlaylistId()
            val coverPath = if (coverUri != null) {
                coverStorage.save(id, coverUri)
            } else {
                colorToken ?: LocalPlaylistsStore.randomDefaultCoverToken()
            }
            localStore.addFirst(
                LocalPlaylist(
                    id = id,
                    name = name.trim(),
                    coverPath = coverPath,
                )
            )
        }
    }

    /** Maps a stored local playlist to the Library card item. */
    private fun LocalPlaylist.toItem(): LibraryPlaylistItem {
        val mosaic = repository.mosaicForPlaylist(id)
        return LibraryPlaylistItem(
            id = id,
            title = name,
            subtitle = "Sua playlist",
            // When there is an explicit cover (photo or Vz color token) it is the cover.
            // The mosaic is only used when there is no cover at all.
            imageUrl = coverPath ?: mosaic.firstOrNull(),
            mosaic = if (coverPath == null) mosaic else emptyList(),
        )
    }
}
