package com.vztechcore.vztune.core.domain.player

import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track

/**
 * Global playback state. Read by any screen that shows "now playing"
 * indicators (e.g. the little bars over the thumbnail).
 */
data class PlaybackState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,   
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
) {
    fun isCurrent(trackId: String, trackSource: Source): Boolean =
        currentTrack?.id == trackId && currentTrack.source == trackSource

    val progress: Float   
        get() = if (durationMs > 0) {
            (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        } else 0f
}

enum class RepeatMode { OFF, ONE, ALL }