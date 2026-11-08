package com.vztechcore.vztune.data.player

import com.vztechcore.vztune.data.prefs.MostPlayedStore
import com.vztechcore.vztune.data.prefs.PlayHistoryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes every track that starts playing into the play history ("Recentemente tocadas")
 * and the play counter ("Mais ouvidas"), whatever screen started it.
 */
@Singleton
class PlaybackRecorder @Inject constructor(
    private val playerController: PlayerController,
    private val historyStore: PlayHistoryStore,
    private val mostPlayedStore: MostPlayedStore,
) {
    private var started = false

    /** Starts listening to the player; calling it more than once has no effect. */
    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        scope.launch {
            playerController.state
                .map { it.currentTrack }
                .filterNotNull()
                .distinctUntilChangedBy { "${it.source.name}:${it.id}" }
                .collect { track ->
                    historyStore.add(track)
                    mostPlayedStore.record(track)
                }
        }
    }
}
