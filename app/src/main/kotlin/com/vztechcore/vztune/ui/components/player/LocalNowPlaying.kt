package com.vztechcore.vztune.ui.components.player

import androidx.compose.runtime.compositionLocalOf
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.player.PlaybackState

/**
 * Provides the current playback state to every composable that needs it.
 * Screens read it via `LocalNowPlaying.current`.
 * Value is provided once at the top of MainActivity from MainViewModel.
 */
val LocalNowPlaying = compositionLocalOf { PlaybackState() }

/**
 * Lightweight snapshot of "how this specific track is doing right now",
 * used by track rows/cards to decide between the equalizer, the pause icon
 * and the plain (idle) thumbnail.
 */
data class TrackPlayback(
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
)

/**
 * Returns the [TrackPlayback] for [track] against the current [PlaybackState].
 *
 * Any track that is not the one currently loaded returns the default
 * "not playing / not paused" value, so callers can read `isPlaying` and
 * `isPaused` directly without an extra `isCurrent` check.
 */
fun PlaybackState.playbackOf(track: Track): TrackPlayback {
    val isCurrent = isCurrent(track.id, track.source)
    return TrackPlayback(
        isPlaying = isCurrent && this.isPlaying,
        isPaused = isCurrent && !this.isPlaying,
    )
}