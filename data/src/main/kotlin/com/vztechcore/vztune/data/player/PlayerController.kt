package com.vztechcore.vztune.data.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
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

    val player: ExoPlayer = run {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(
                mapOf("Referer" to "https://www.youtube.com/")
            )

        val cacheFactory = CacheDataSource.Factory()
            .setCache(mediaCache)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context, httpFactory))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context).setDataSourceFactory(cacheFactory)
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
                        val duration = duration.coerceAtLeast(0L)
                        if (duration > 0) {
                            _state.update { it.copy(durationMs = duration) }
                        }
                        if (playbackState == Player.STATE_READY) {
                            _state.update { it.copy(isLoading = false) }
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

                    override fun onPlayerError(error: PlaybackException) {
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
            if (url != null) {
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
                player.playWhenReady = true
            } else {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isPlaying = false,
                        error = "Não foi possível carregar o áudio.",
                    )
                }
            }
        }
    }

    fun pause() = player.pause()

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    fun next() { /* TODO: queue */ }
    fun previous() { /* TODO: queue */ }

    fun release() {
        player.release()
        mediaCache.release()
    }

    private companion object {
        const val CACHE_SIZE_BYTES = 200L * 1024 * 1024 // 200 MB
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}