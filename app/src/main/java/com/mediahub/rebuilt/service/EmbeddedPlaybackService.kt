package com.mediahub.rebuilt.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import com.mediahub.rebuilt.R

/**
 * Keeps the app process at media-playback priority while an official MusicAPI
 * WebView embed is playing. The actual audio remains owned by the provider's player.
 */
class EmbeddedPlaybackService : Service() {
    companion object {
        const val ACTION_START = "com.mediahub.rebuilt.action.EMBEDDED_PLAYBACK_START"
        const val ACTION_STOP = "com.mediahub.rebuilt.action.EMBEDDED_PLAYBACK_STOP"
        private const val CHANNEL_ID = "embedded_music_playback"
        private const val NOTIFICATION_ID = 4206
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "In-app music playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the official in-app music player active in the background."
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val stopIntent = Intent(this, EmbeddedPlaybackService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this, 4207, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }.setSmallIcon(R.drawable.ic_mediahub)
            .setContentTitle("MEDIAHUB-PRO · In-app music")
            .setContentText("Music player is active in the background")
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                "Stop",
                stopPendingIntent
            ).build())
            .build()

        startForeground(NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
