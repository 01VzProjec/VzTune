package com.vztechcore.vztune.ui.screens.downloads

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
)