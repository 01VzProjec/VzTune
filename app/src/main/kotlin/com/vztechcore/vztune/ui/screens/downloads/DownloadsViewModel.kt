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
        // Sort highest progress first; tie-break by fixed creation timestamp so items never jump around
        val sortedActive = downloadsMap.values.sortedWith(
            compareByDescending<TrackDownloadItem> { it.percentage }
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

    fun onCancelDownload(track: Track) {
        trackDownloader.cancel(track)
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
}