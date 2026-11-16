package com.vztechcore.vztune.ui.screens.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.player.PlayerController
import com.vztechcore.vztune.data.prefs.models.DownloadedPlaylist
import com.vztechcore.vztune.data.prefs.store.DownloadedPlaylistsStore
import com.vztechcore.vztune.data.prefs.store.DownloadedTracksStore
import com.vztechcore.vztune.data.prefs.store.FavoritePlaylistsStore
import com.vztechcore.vztune.data.prefs.models.LocalPlaylist
import com.vztechcore.vztune.data.prefs.store.LocalPlaylistsStore
import com.vztechcore.vztune.data.prefs.store.PlayHistoryStore
import com.vztechcore.vztune.data.prefs.store.PlaylistCoverStorage
import com.vztechcore.vztune.data.prefs.models.StoredPlaylist
import com.vztechcore.vztune.data.prefs.models.newLocalPlaylistId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    historyStore: PlayHistoryStore,
    private val localStore: LocalPlaylistsStore,
    favoritesStore: FavoritePlaylistsStore,
    downloadedPlaylistsStore: DownloadedPlaylistsStore,
    downloadedTracksStore: DownloadedTracksStore,
    private val coverStorage: PlaylistCoverStorage,
    private val playerController: PlayerController,
) : ViewModel() {

    private val selectedTab = MutableStateFlow(LibraryTab.OVERVIEW)

    private var recentTracks: List<Track> = emptyList()

    val uiState: StateFlow<LibraryUiState> = combine(
        selectedTab,
        historyStore.tracks,
        localStore.items,
        favoritesStore.items,
        downloadedPlaylistsStore.items,
        downloadedTracksStore.downloadedKeys,
    ) { tab, recent, mine, favorites, downloaded, downloadedKeys ->
        recentTracks = recent
        // Só mostra a playlist baixada enquanto pelo menos 1 faixa dela ainda está baixada.
        // trackKeys vazio = registro antigo sem keys -> mostra mesmo assim.
        val visibleDownloaded = downloaded.filter { item ->
            item.trackKeys.isEmpty() || item.trackKeys.any { it in downloadedKeys }
        }
        LibraryUiState(
            selectedTab = tab,
            recentTracks = recent,
            myPlaylists = mine.map { it.toItem() },
            favoritePlaylists = favorites.map { it.toItem() },
            downloadedPlaylists = visibleDownloaded.map { it.toItem() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun onTabSelected(tab: LibraryTab) {
        selectedTab.value = tab
    }

    fun onTrackClick(track: Track) {
        val index = recentTracks.indexOfFirst { it.id == track.id && it.source == track.source }
        if (index >= 0) playerController.playQueue(recentTracks, index)
        else playerController.playQueue(listOf(track), 0)
    }

    /** Creates a local playlist; without a cover it shows the thumbnails of its tracks. */
    fun createPlaylist(name: String, coverUri: Uri?) {
        viewModelScope.launch {
            val id = newLocalPlaylistId()
            val coverPath = coverUri?.let { coverStorage.save(id, it) }
            localStore.addFirst(LocalPlaylist(id = id, name = name.trim(), coverPath = coverPath))
        }
    }

    private fun LocalPlaylist.toItem() = LibraryPlaylistItem(
        id = id,
        title = name,
        subtitle = "Sua playlist",
        imageUrl = coverPath ?: mosaic.firstOrNull(),
        mosaic = if (coverPath == null) mosaic else emptyList(),
    )

    private fun StoredPlaylist.toItem() = LibraryPlaylistItem(
        id = id,
        title = title,
        subtitle = owner,
        imageUrl = thumbnailUrl,
        mosaic = mosaic,
        isAlbum = isAlbum,
    )

    private fun DownloadedPlaylist.toItem() = LibraryPlaylistItem(
        id = id,
        title = title,
        subtitle = owner ?: if (isAlbum) "Álbum baixado" else "Playlist baixada",
        imageUrl = thumbnailUrl ?: mosaic.firstOrNull(),
        mosaic = mosaic,
        isAlbum = isAlbum,
    )
}
