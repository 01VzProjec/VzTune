package com.vztechcore.vztune.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.download.DownloadStatus
import com.vztechcore.vztune.data.download.TrackDownloadItem
import com.vztechcore.vztune.data.download.TrackDownloader
import com.vztechcore.vztune.data.player.PlayerController
import com.vztechcore.vztune.data.prefs.store.DownloadedTracksStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val trackDownloader: TrackDownloader,
    downloadedTracksStore: DownloadedTracksStore,
    private val playerController: PlayerController,
) : ViewModel() {

    private val selectedTab = MutableStateFlow(DownloadsTab.IN_PROGRESS)

    val uiState: StateFlow<DownloadsUiState> = combine(
        selectedTab,
        trackDownloader.downloads,
        downloadedTracksStore.items,
    ) { tab, downloadsMap, storedList ->
        // Order: 1) Active downloading (highest % first), 2) Paused, 3) Queued (FIFO), 4) Failed
        val sortedActive = downloadsMap.values.sortedWith(
            compareBy<TrackDownloadItem> { statusPriority(it.status) }
                .thenByDescending { if (it.status == DownloadStatus.DOWNLOADING) it.percentage else 0 }
                .thenBy { it.createdAtMs }
                .thenBy { "${it.track.source.name}_${it.track.id}" }
        )

        DownloadsUiState(
            selectedTab = tab,
            activeDownloads = sortedActive,
            completedDownloads = storedList,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())

    fun onTabSelected(tab: DownloadsTab) {
        selectedTab.value = tab
    }

    fun onTogglePauseResume(track: Track) {
        trackDownloader.togglePauseResume(track)
    }

    fun onPauseAll() {
        trackDownloader.pauseAll()
    }

    fun onResumeAll() {
        trackDownloader.resumeAll()
    }

    fun onCancelDownload(track: Track) {
        trackDownloader.cancel(track)
    }

    fun onCancelAll() {
        trackDownloader.cancelAll()
    }

    fun onDeleteDownload(track: Track) {
        trackDownloader.deleteDownload(track)
    }

    fun onPlayTrack(track: Track) {
        val allCompleted = uiState.value.completedDownloads.map { it.toTrack() }
        val idx = allCompleted.indexOfFirst { it.id == track.id && it.source == track.source }
        if (idx >= 0) {
            playerController.playQueue(allCompleted, idx)
        } else {
            playerController.play(track)
        }
    }

    private fun statusPriority(status: DownloadStatus): Int = when (status) {
        DownloadStatus.DOWNLOADING -> 0
        DownloadStatus.PAUSED -> 1
        DownloadStatus.QUEUED -> 2
        DownloadStatus.FAILED -> 3
        DownloadStatus.COMPLETED -> 4
    }
}