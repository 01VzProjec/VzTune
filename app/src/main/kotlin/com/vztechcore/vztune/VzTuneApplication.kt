package com.vztechcore.vztune

import android.app.Application
import com.vztechcore.vztune.data.player.PlaybackRecorder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class VzTuneApplication : Application() {

    @Inject lateinit var playbackRecorder: PlaybackRecorder

    override fun onCreate() {
        super.onCreate()
        playbackRecorder.start()
    }
}