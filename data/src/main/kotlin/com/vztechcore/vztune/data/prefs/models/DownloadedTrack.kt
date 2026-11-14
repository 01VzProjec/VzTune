package com.vztechcore.vztune.data.prefs.models

import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import kotlinx.serialization.Serializable

@Serializable
data class DownloadedTrack(
    val key: String,
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationSeconds: Long = 0L,
    val thumbnailUrl: String? = null,
    val sourceName: String = Source.YOUTUBE_MUSIC.name,
    val isExplicit: Boolean = false,
    val filePath: String,
    val mimeType: String = "audio/mp4",
    val fileSizeBytes: Long = 0L,
    val downloadedAtMs: Long = System.currentTimeMillis(),
) {
    fun toTrack(): Track {
        val source = runCatching { Source.valueOf(sourceName) }.getOrDefault(Source.YOUTUBE_MUSIC)
        return Track(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationSeconds = durationSeconds,
            thumbnailUrl = thumbnailUrl,
            source = source,
            isExplicit = isExplicit,
        )
    }

    /** True durante os primeiros 7 dias após a conclusão do download. */
    fun isWithinSevenDays(nowMs: Long = System.currentTimeMillis()): Boolean {
        val sevenDaysMs = 7L * 24L * 60L * 60L * 1000L
        return (nowMs - downloadedAtMs) in 0..sevenDaysMs
    }

    companion object {
        fun from(
            track: Track,
            filePath: String,
            mimeType: String,
            fileSizeBytes: Long,
            downloadedAtMs: Long = System.currentTimeMillis(),
        ): DownloadedTrack {
            val stored = StoredTrack.from(track)
            return DownloadedTrack(
                key = stored.key,
                id = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationSeconds = track.durationSeconds,
                thumbnailUrl = track.thumbnailUrl,
                sourceName = track.source.name,
                isExplicit = track.isExplicit,
                filePath = filePath,
                mimeType = mimeType,
                fileSizeBytes = fileSizeBytes,
                downloadedAtMs = downloadedAtMs,
            )
        }
    }
}