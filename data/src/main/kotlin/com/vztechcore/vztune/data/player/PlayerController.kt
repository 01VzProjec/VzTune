package com.vztechcore.vztune.data.player

import android.content.Context
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val mediaCache: SimpleCache by lazy {
        SimpleCache(
            File(context.cacheDir, "vztune_media"),
            LeastRecentlyUsedCacheEvictor(CACHE_SIZE_BYTES),
            StandaloneDatabaseProvider(context),
        )
    }

    /**
     * Exactly like VzMusic's AudioPlayerManager:
     *   - CacheDataSource with FLAG_IGNORE_CACHE_ON_ERROR
     *   - DefaultDataSource.Factory (NO custom User-Agent, NO Referer)
     *   - AudioAttributes USAGE_MEDIA / AUDIO_CONTENT_TYPE_MUSIC
     *   - Handle audio becoming noisy
     */
    val player: ExoPlayer = run {
        val dataSourceFactory = CacheDataSource.Factory()
            .setCache(mediaCache)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(dataSourceFactory)
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _state.update { it.copy(isPlaying = isPlaying) }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        Log.d("VzTunePlayer", "state=$playbackState")
                        if (playbackState == Player.STATE_READY) {
                            val d = duration.coerceAtLeast(0L)
                            _state.update {
                                it.copy(isLoading = false, durationMs = d)
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("VzTunePlayer", "error=${error.errorCodeName}: ${error.message}", error)
                        _state.update {
                            it.copy(
                                isPlaying = false,
                                isLoading = false,
                                error = "${error.errorCodeName}: ${error.message}",
                            )
                        }
                    }
                })
            }
    }

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun play(track: Track) {
        Log.d("VzTunePlayer", "play track=${track.id} source=${track.source}")
        _state.update {
            it.copy(
                currentTrack = track,
                isPlaying = false,
                isLoading = true,
                error = null,
                positionMs = 0L,
                durationMs = 0L,
            )
        }

        loadJob?.cancel()
        loadJob = scope.launch {
            val url = musicRepository.getStreamUrl(track)
            Log.d("VzTunePlayer", "resolved url=$url")
            if (url.isNullOrBlank()) {
                _state.update {
                    it.copy(isLoading = false, error = "URL de áudio vazia")
                }
                return@launch
            }
            try {
                player.stop()
                player.clearMediaItems()
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
                player.playWhenReady = true
            } catch (e: Exception) {
                Log.e("VzTunePlayer", "prepare error", e)
                _state.update {
                    it.copy(isLoading = false, error = e.message)
                }
            }
        }
    }

    fun pause() = player.pause()

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    fun next() { /* queue não implementada ainda */ }
    fun previous() { /* queue não implementada ainda */ }

    fun release() {
        player.release()
        mediaCache.release()
    }

    private companion object {
        const val CACHE_SIZE_BYTES = 200L * 1024 * 1024
    }
}