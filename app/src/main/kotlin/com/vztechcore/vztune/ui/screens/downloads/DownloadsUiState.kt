package com.vztechcore.vztune.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.download.TrackDownloadItem
import com.vztechcore.vztune.data.download.TrackDownloader
import com.vztechcore.vztune.data.player.PlayerController
import com.vztechcore.vztune.data.prefs.models.DownloadedTrack
import com.vztechcore.vztune.data.prefs.store.DownloadedTracksStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DownloadsUiState(
    val activeDownloads: List<TrackDownloadItem> = emptyList(),
    val recentCompleted7Days: List<DownloadedTrack> = emptyList(),
    val allDownloaded: List<DownloadedTrack> = emptyList(),
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val trackDownloader: TrackDownloader,
    downloadedTracksStore: DownloadedTracksStore,
    private val playerController: PlayerController,
) : ViewModel() {

    val uiState: StateFlow<DownloadsUiState> = combine(
        trackDownloader.downloads,
        downloadedTracksStore.recentCompleted7Days,
        downloadedTracksStore.items,
    ) { activeMap, recent7Days, allItems ->
        DownloadsUiState(
            activeDownloads = activeMap.values.sortedByDescending { it.updatedAtMs },
            recentCompleted7Days = recent7Days,
            allDownloaded = allItems.sortedByDescending { it.downloadedAtMs },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DownloadsUiState(),
    )

    fun onPlayTrack(track: Track) {
        val allTracks = uiState.value.allDownloaded.map { it.toTrack() }
        val idx = allTracks.indexOfFirst { it.id == track.id && it.source == track.source }
        if (idx >= 0) {
            playerController.playQueue(allTracks, idx)
        } else {
            playerController.play(track)
        }
    }

    fun onCancelDownload(track: Track) {
        trackDownloader.cancel(track)
    }

    fun onRetryDownload(track: Track) {
        trackDownloader.download(track)
    }

    fun onDismissFailed(track: Track) {
        trackDownloader.dismissFailed(track)
    }

    fun onDeleteDownloaded(track: Track) {
        trackDownloader.deleteDownload(track)
    }
}