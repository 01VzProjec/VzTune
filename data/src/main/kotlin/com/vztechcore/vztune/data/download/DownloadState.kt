package com.vztechcore.vztune.data.download

import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.prefs.models.StoredTrack

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
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
    val updatedAtMs: Long = System.currentTimeMillis(),
) {
    val key: String
        get() = StoredTrack.from(track).key

    val progressPercent: Int
        get() = (progress.coerceIn(0f, 1f) * 100).toInt()
}