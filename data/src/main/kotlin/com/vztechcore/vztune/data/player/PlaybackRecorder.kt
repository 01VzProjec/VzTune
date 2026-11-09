package com.vztechcore.vztune.data.player

import com.vztechcore.vztune.core.domain.player.PlaybackState
import com.vztechcore.vztune.data.di.ApplicationScope
import com.vztechcore.vztune.data.prefs.store.MostPlayedStore
import com.vztechcore.vztune.data.prefs.store.PlayHistoryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records what plays in the app:
 *
 *   - "Recentemente tocadas" ([PlayHistoryStore])  → as soon as a track starts.
 *   - "Mais ouvidas" ([MostPlayedStore])            → only after 50% of the
 *     track OR 30 seconds played (whichever comes first). This matches the
 *     YouTube / Spotify convention and prevents skipped tracks from counting
 *     as played.
 *
 * Runs on [ApplicationScope] so it survives ViewModel destruction. Called
 * once from [com.vztechcore.vztune.VzTuneApplication.onCreate].
 */
@Singleton
class PlaybackRecorder @Inject constructor(
    private val playerController: PlayerController,
    private val historyStore: PlayHistoryStore,
    private val mostPlayedStore: MostPlayedStore,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private var started = false

    /** Last track key seen (source + id); used to detect track changes. */
    private var lastTrackKey: String? = null

    /** True after the current track crossed the "played" threshold. */
    private var counted = false

    /** Starts listening to the player; idempotent. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            playerController.state.collect { state ->
                val track = state.currentTrack
                val key = track?.let { "${it.source.name}:${it.id}" }

                if (key != lastTrackKey) {
                    lastTrackKey = key
                    counted = false
                    // History is immediate: "recently played" means "recently opened".
                    if (track != null) {
                        historyStore.add(track)
                    }
                }

                if (track != null && !counted && hasCrossedPlayThreshold(state)) {
                    counted = true
                    mostPlayedStore.record(track)
                }
            }
        }
    }

    /**
     * 50% of the track OR 30 seconds, whichever is shorter.
     * If duration is still unknown (0), falls back to 30 seconds.
     */
    private fun hasCrossedPlayThreshold(state: PlaybackState): Boolean {
        val duration = state.durationMs
        val position = state.positionMs
        if (duration <= 0L) return position >= MIN_PLAY_MS
        val half = duration / 2
        val threshold = minOf(MIN_PLAY_MS, half)
        return position >= threshold
    }

    private companion object {
        const val MIN_PLAY_MS = 30_000L
    }
}