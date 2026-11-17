package com.mediahub.rebuilt.updates

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mediahub.rebuilt.BuildConfig
import com.mediahub.rebuilt.MediaHubApp
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
    fun check(context: Context, notify: Boolean = true, callback: ((String?) -> Unit)? = null) {
        val app = context.applicationContext
        Thread {
            var updateTag: String? = null
            var releaseUrl: String? = null
            try {
                val connection = (URL("https://api.github.com/repos/sarahbanaah-jpg/MEDIAHUB-PRO/releases/latest").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "MediaHub-Pro-Android")
                }
                try {
                    if (connection.responseCode in 200..299) {
                        val body = connection.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(body)
                        val tag = json.optString("tag_name").removePrefix("v")
                        val html = json.optString("html_url")
                        if (tag.isNotBlank() && html.startsWith("https://github.com/") && isNewer(tag, BuildConfig.VERSION_NAME)) {
                            updateTag = tag
                            releaseUrl = html
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (_: Exception) { }

            if (updateTag != null && releaseUrl != null && notify &&
                app.getSharedPreferences("mediahub_settings", Context.MODE_PRIVATE).getBoolean("update_notifications", true)
            ) postNotification(app, updateTag!!, releaseUrl!!)
            val message = updateTag
            Handler(Looper.getMainLooper()).post { callback?.invoke(message) }
        }.start()
    }

    private fun postNotification(context: Context, version: String, url: String) {
        val intent = PendingIntent.getActivity(
            context, 501, Intent(Intent.ACTION_VIEW, Uri.parse(url)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, MediaHubApp.CHANNEL_UPDATES)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("MediaHub Pro update available")
            .setContentText("Version $version is ready to review.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("MediaHub Pro $version is available. Open the release page to review and install it."))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try { NotificationManagerCompat.from(context).notify(501, notification) } catch (_: SecurityException) { }
    }

    private fun isNewer(remote: String, current: String): Boolean {
        val a = remote.split('.', '-', '+').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.', '-', '+').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left > right
        }
        return false
    }
}
