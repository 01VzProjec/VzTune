package com.vztechcore.vztune.data.download

import com.vztechcore.vztune.core.domain.music.Track

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
}

data class TrackDownloadItem(
    val track: Track,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
    val errorMessage: String? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
) {
    val percentage: Int
        get() = (progress * 100f).toInt().coerceIn(0, 100)

    val isPaused: Boolean
        get() = status == DownloadStatus.PAUSED
}