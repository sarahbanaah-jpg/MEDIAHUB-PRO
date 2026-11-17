package com.mediahub.rebuilt

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class MediaHubApp : Application() {
    override fun onCreate() {
        super.onCreate()
        installCrashRecorder()
        createNotificationChannels()
    }

    private fun installCrashRecorder() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                File(filesDir, "last-crash.txt").writeText(
                    "Thread: ${thread.name}\nTime: ${java.util.Date()}\n\n${sw}"
                )
            } catch (_: Exception) { }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playback = NotificationChannel(CHANNEL_PLAYBACK, "Media playback", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Controls for currently playing music and video"; setShowBadge(false)
            }
            val updates = NotificationChannel(CHANNEL_UPDATES, "Updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "App update notifications"
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannels(listOf(playback, updates))
        }
    }

    companion object {
        const val CHANNEL_PLAYBACK = "mediahub_playback"
        const val CHANNEL_UPDATES = "mediahub_updates"
    }
}
