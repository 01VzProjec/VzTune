package com.vztechcore.vztune.data.youtube

import android.util.Log
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.downloader.Downloader

/**
 * Guarantees NewPipe is initialized exactly once, regardless of who calls first.
 * Mirrors the VzMusic behavior but logs failures instead of swallowing them.
 *
 * Returns true when NewPipe is ready to be used.
 */
object NewPipeInitializer {

    private const val TAG = "VzTuneNewPipe"

    @Volatile
    private var initialized = false

    fun ensureInitialized(downloader: Downloader): Boolean {
        if (initialized) return true

        synchronized(this) {
            if (initialized) return true

            return runCatching {
                Log.d(TAG, "Initializing NewPipe with ${downloader::class.simpleName}")
                // FIX15: NewPipe sem locale usava en-US por padrao -> titulos do canal vinham em ingles mesmo o canal sendo BR.
                // InnerTube ja era pt-BR, por isso o YT Music estava em portugues e o canal em ingles.
                NewPipe.init(downloader, Localization("pt", "BR"), ContentCountry("BR"))
                initialized = true
                Log.d(TAG, "NewPipe initialized OK")
                true
            }.getOrElse { e ->
                Log.e(TAG, "NewPipe init FAILED", e)
                false
            }
        }
    }
}