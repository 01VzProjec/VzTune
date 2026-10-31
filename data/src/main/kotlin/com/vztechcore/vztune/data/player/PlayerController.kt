package com.vztechcore.vztune.data.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.player.PlaybackState
import com.vztechcore.vztune.data.music.MusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext context: Context,
    private val musicRepository: MusicRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val duration = duration.coerceAtLeast(0L)
                if (duration > 0) {
                    _state.update { it.copy(durationMs = duration) }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                _state.update {
                    it.copy(
                        positionMs = newPosition.positionMs,
                        durationMs = player.duration.coerceAtLeast(0L),
                    )
                }
            }
        })
    }

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun play(track: Track) {
        _state.update {
            it.copy(
                currentTrack = track,
                isPlaying = true,
                positionMs = 0L,
                durationMs = 0L,
            )
        }
        loadJob?.cancel()
        loadJob = scope.launch {
            val url = musicRepository.getStreamUrl(track)
            if (url != null) {
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
                player.playWhenReady = true
            } else {
                _state.update { it.copy(isPlaying = false) }
            }
        }
    }

    fun pause() = player.pause()

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    /**
     * TODO(player): when the queue is implemented, advance to the next track
     * in the current playback queue (e.g. next search result or artist radio).
     */
    fun next() {
        // Stub — queue not implemented yet.
    }

    /**
     * TODO(player): when the queue is implemented, go back to the previous track.
     */
    fun previous() {
        // Stub — queue not implemented yet.
    }

    fun release() = player.release()
}