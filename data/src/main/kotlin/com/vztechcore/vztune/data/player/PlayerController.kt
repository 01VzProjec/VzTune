package com.vztechcore.vztune.data.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.player.PlaybackState
import com.vztechcore.vztune.data.youtube.YouTubeRepository
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

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val youTubeRepository: YouTubeRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val player: ExoPlayer by lazy {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : androidx.media3.common.Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.update { it.copy(isPlaying = isPlaying) }
                }
                override fun onPositionDiscontinuity(
                    oldPosition: androidx.media3.common.Player.PositionInfo,
                    newPosition: androidx.media3.common.Player.PositionInfo,
                    reason: Int,
                ) {
                    _state.update { it.copy(positionMs = newPosition.positionMs) }
                }
            })
        }
    }

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun play(track: Track) {
        _state.update { it.copy(currentTrack = track, isPlaying = true) }
        loadJob?.cancel()
        loadJob = scope.launch {
            val url = when (track.source) {
                com.vztechcore.vztune.core.domain.music.Source.YOUTUBE ->
                    youTubeRepository.getStreamUrl(track.id)
                else -> null // YT Music stream resolution comes in next step
            }
            if (url != null) {
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
                player.playWhenReady = true
            }
        }
    }

    fun pause() = player.pause()
    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    fun release() = player.release()
}