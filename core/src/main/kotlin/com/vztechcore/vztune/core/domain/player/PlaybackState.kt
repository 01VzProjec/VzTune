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
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
) {
    fun isCurrent(trackId: String, trackSource: Source): Boolean =
        currentTrack?.id == trackId && currentTrack.source == trackSource
}

enum class RepeatMode { OFF, ONE, ALL }