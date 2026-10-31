package com.vztechcore.vztune.data.youtube

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader

object NewPipeInitializer {

    @Volatile private var initialized = false

    fun ensureInitialized(downloader: Downloader) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            runCatching {
                NewPipe.init(downloader)
                initialized = true
            }
        }
    }
}