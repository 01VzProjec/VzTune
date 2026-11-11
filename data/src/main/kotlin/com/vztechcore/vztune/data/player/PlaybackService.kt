package com.vztechcore.vztune.data.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var playerController: PlayerController
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // startForegroundService() exige startForeground() em até 5s.
        // O ExoPlayer pode demorar mais que isso para resolver o URL do áudio
        // (rede lenta / buffering), então entramos em foreground imediatamente
        // com uma notificação placeholder. O MediaSessionService substitui ela
        // pela notificação real de mídia quando o player começar a tocar.
        goForegroundWithPlaceholder()

        mediaSession = MediaSession.Builder(this, playerController.player).build()
    }

    private fun goForegroundWithPlaceholder() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PLACEHOLDER_CHANNEL_ID,
                "Reprodução",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Preparando a reprodução"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, PLACEHOLDER_CHANNEL_ID)
            .setContentTitle("VzTune")
            .setContentText("Preparando reprodução…")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()

        startForeground(PLACEHOLDER_NOTIFICATION_ID, notification)
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo,
    ): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (playerController.player.isPlaying) return
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private companion object {
        const val PLACEHOLDER_CHANNEL_ID = "vztune_playback_preparing"
        const val PLACEHOLDER_NOTIFICATION_ID = 1
    }
}