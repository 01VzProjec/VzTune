package com.vztechcore.vztune.data.player

import android.content.Context
import android.content.Intent
import android.os.Build
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
import kotlinx.coroutines.delay
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
        val dataSourceFactory = CacheDataSource.Factory()
            .setCache(mediaCache)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
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
                        if (playbackState == Player.STATE_READY) {
                            val d = duration.coerceAtLeast(0L)
                            _state.update { it.copy(isLoading = false, durationMs = d) }
                        }
                        if (playbackState == Player.STATE_ENDED) {
                            playNext(auto = true)
                        }
                        Log.d(TAG, "state=$playbackState")
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "error=${error.errorCodeName}: ${error.message}", error)
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
    private var preloadJob: Job? = null

    private var queue: List<Track> = emptyList()
    private var currentIndex: Int = -1
    private var preloadedUrl: String? = null
    private var preloadedIndex: Int = -1

    private var consecutiveFailures = 0

    init {
        scope.launch {
            while (true) {
                delay(1000)
                if (player.isPlaying) {
                    _state.update {
                        it.copy(
                            positionMs = player.currentPosition,
                            durationMs = player.duration.coerceAtLeast(0L)
                        )
                    }
                    maybePreloadNext()
                }
            }
        }
    }

    fun playQueue(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        consecutiveFailures = 0
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        queue = tracks
        currentIndex = safeIndex
        preloadedUrl = null
        preloadedIndex = -1
        playAtIndex(safeIndex)
    }

    fun play(track: Track) {
        playQueue(listOf(track), 0)
    }

    fun addToQueueNext(track: Track) {
        if (queue.isEmpty()) {
            playQueue(listOf(track), 0)
            return
        }
        val mutableQueue = queue.toMutableList()
        val existingIndex = mutableQueue.indexOfFirst { it.id == track.id && it.source == track.source }
        if (existingIndex != -1) {
            mutableQueue.removeAt(existingIndex)
            if (existingIndex <= currentIndex && currentIndex > 0) {
                currentIndex--
            }
        }
        val insertIndex = (currentIndex + 1).coerceIn(0, mutableQueue.size)
        mutableQueue.add(insertIndex, track)
        queue = mutableQueue
        if (preloadedIndex == insertIndex) {
            preloadedIndex = -1
            preloadedUrl = null
        }
        if (currentIndex == -1 && queue.isNotEmpty()) {
            currentIndex = 0
            playAtIndex(0)
        }
    }

    fun addToQueueEnd(track: Track) {
        if (queue.isEmpty()) {
            playQueue(listOf(track), 0)
            return
        }
        val mutableQueue = queue.toMutableList()
        if (mutableQueue.none { it.id == track.id && it.source == track.source }) {
            mutableQueue.add(track)
            queue = mutableQueue
        }
    }

    fun playNext(auto: Boolean = false) {
        val nextIdx = currentIndex + 1
        if (nextIdx >= queue.size) {
            if (auto) {
                player.pause()
                _state.update { it.copy(isPlaying = false) }
            }
            return
        }
        playAtIndex(nextIdx)
    }

    fun playPrevious() {
        if (player.currentPosition > 3000L) {
            player.seekTo(0)
            return
        }
        val prevIdx = currentIndex - 1
        if (prevIdx < 0) {
            player.seekTo(0)
            return
        }
        playAtIndex(prevIdx)
    }

    fun pause() = player.pause()
    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    fun release() {
        player.release()
        mediaCache.release()
    }

    private fun playAtIndex(idx: Int) {
        if (idx !in queue.indices) {
            _state.update {
                it.copy(isLoading = false, isPlaying = false, error = "Fim da fila.")
            }
            return
        }

        val track = queue[idx]
        val cachedUrl = if (preloadedIndex == idx) preloadedUrl else null
        preloadedUrl = null
        preloadedIndex = -1
        currentIndex = idx

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
            val url = cachedUrl ?: musicRepository.getStreamUrl(track)
            if (url.isNullOrBlank()) {
                consecutiveFailures++
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    Log.e(TAG, "Too many consecutive failures (${consecutiveFailures}), stopping")
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isPlaying = false,
                            error = "Falha ao carregar áudio (${consecutiveFailures} tentativas).",
                        )
                    }
                    return@launch
                }
                Log.w(TAG, "Stream URL null for ${track.id} '${track.title}', auto-skipping")
                scope.launch { playAtIndex(idx + 1) }
                return@launch
            }

            consecutiveFailures = 0
            ensureForegroundService()
            try {
                player.stop()
                player.clearMediaItems()
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
                player.playWhenReady = true
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun ensureForegroundService() {
        runCatching {
            val intent = Intent(context, PlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }.onFailure { e ->
            Log.w(TAG, "Failed to start PlaybackService: ${e.message}")
        }
    }

    private fun maybePreloadNext() {
        val duration = player.duration
        if (duration <= 0) return
        val remaining = duration - player.currentPosition
        if (remaining <= 0 || remaining > PRELOAD_WINDOW_MS) return
        val nextIdx = currentIndex + 1
        if (nextIdx >= queue.size) return
        if (preloadedIndex == nextIdx) return

        preloadedIndex = nextIdx
        preloadedUrl = null
        preloadJob?.cancel()
        preloadJob = scope.launch {
            preloadedUrl = musicRepository.getStreamUrl(queue[nextIdx])
        }
    }

    private companion object {
        const val TAG = "VzTunePlayer"
        const val CACHE_SIZE_BYTES = 200L * 1024 * 1024
        const val PRELOAD_WINDOW_MS = 10_000L
        const val MAX_CONSECUTIVE_FAILURES = 3
    }
}