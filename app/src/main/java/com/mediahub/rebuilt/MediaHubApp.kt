package com.mediahub.rebuilt

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class MediaHubApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playback = NotificationChannel(
                CHANNEL_PLAYBACK,
                "Media playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for currently playing music and video"
                setShowBadge(false)
            }

            val updates = NotificationChannel(
                CHANNEL_UPDATES,
                "Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "App update notifications"
            }

            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(playback)
            nm?.createNotificationChannel(updates)
        }
    }

    companion object {
        const val CHANNEL_PLAYBACK = "mediahub_playback"
        const val CHANNEL_UPDATES = "mediahub_updates"
    }
}
