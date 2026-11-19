package com.vztechcore.vztune.ui.screens.downloads

import com.vztechcore.vztune.data.download.DownloadStatus
import com.vztechcore.vztune.data.download.TrackDownloadItem
import com.vztechcore.vztune.data.prefs.models.DownloadedTrack

enum class DownloadsTab(val label: String) {
    IN_PROGRESS("Em andamento"),
    COMPLETED("Finalizados"),
}

data class DownloadsUiState(
    val selectedTab: DownloadsTab = DownloadsTab.IN_PROGRESS,
    val activeDownloads: List<TrackDownloadItem> = emptyList(),
    val completedDownloads: List<DownloadedTrack> = emptyList(),
) {
    val downloadingCount: Int
        get() = activeDownloads.count { it.status == DownloadStatus.DOWNLOADING }

    val queuedCount: Int
        get() = activeDownloads.count { it.status == DownloadStatus.QUEUED }

    val pausedCount: Int
        get() = activeDownloads.count { it.status == DownloadStatus.PAUSED }

    val hasRunningOrQueued: Boolean
        get() = downloadingCount > 0 || queuedCount > 0

    val hasPausedOrFailed: Boolean
        get() = activeDownloads.any {
            it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.FAILED
        }
}