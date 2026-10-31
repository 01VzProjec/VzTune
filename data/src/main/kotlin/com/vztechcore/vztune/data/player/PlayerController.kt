package com.vztechcore.vztune.data.player

import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.player.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Global player controller. Owns the single source of truth for playback state.
 *
 * TODO(media3): wire to ExoPlayer + MediaSessionService.
 *   - Hold an ExoPlayer instance internally.
 *   - Mirror player callbacks (isPlaying, currentPosition) into _state.
 *   - Expose a MediaSession for notification, bluetooth, lockscreen.
 * For now this is an in-memory stub so the UI can react to play/pause.
 */
@Singleton
class PlayerController @Inject constructor() {

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    fun play(track: Track) {
        _state.update { it.copy(currentTrack = track, isPlaying = true, positionMs = 0L) }
    }

    fun pause() {
        _state.update { it.copy(isPlaying = false) }
    }

    fun togglePlayPause() {
        _state.update { it.copy(isPlaying = !it.isPlaying) }
    }

    fun seekTo(positionMs: Long) {
        _state.update { it.copy(positionMs = positionMs) }
    }
}