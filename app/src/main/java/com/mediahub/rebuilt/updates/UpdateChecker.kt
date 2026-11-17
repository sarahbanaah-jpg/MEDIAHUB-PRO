package com.mediahub.rebuilt.updates

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.mediahub.rebuilt.BuildConfig
import com.mediahub.rebuilt.MediaHubApp
import com.mediahub.rebuilt.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val version: String,
    val releaseUrl: String,
    val apkUrl: String?
)

object UpdateChecker {

    fun check(context: Context, notify: Boolean = true, callback: ((UpdateInfo?) -> Unit)? = null) {
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            val info = withContext(Dispatchers.IO) { fetchLatest(app) }
            if (info != null && notify) postNotification(app, info)
            callback?.invoke(info)
        }
    }

    suspend fun checkInBackground(context: Context): UpdateInfo? =
        withContext(Dispatchers.IO) {
            val info = fetchLatest(context.applicationContext)
            if (info != null) postNotification(context.applicationContext, info)
            info
        }

    /** Download APK and open the system installer. Returns error message or null on success. */
    suspend fun downloadAndInstall(context: Context, apkUrl: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val out = File(dir, "MediaHubPro-update.apk")
                if (out.exists()) out.delete()

                val connection = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30_000
                    readTimeout = 120_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "MediaHub-Pro-Android")
                    setRequestProperty("Accept", "application/octet-stream")
                }
                try {
                    if (connection.responseCode !in 200..299) {
                        return@withContext "Download failed (HTTP ${connection.responseCode})"
                    }
                    connection.inputStream.use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                } finally {
                    connection.disconnect()
                }

                if (!out.exists() || out.length() < 1_000_000L) {
                    return@withContext "Downloaded file is incomplete"
                }

                withContext(Dispatchers.Main) {
                    installApk(context, out)
                }
                null
            } catch (e: Exception) {
                e.message ?: "Update download failed"
            }
        }

    fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.files",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun fetchLatest(context: Context): UpdateInfo? {
        return try {
            val connection = (URL("https://api.github.com/repos/sarahbanaah-jpg/MEDIAHUB-PRO/releases/latest")
                .openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "MediaHub-Pro-Android")
            }
            try {
                if (connection.responseCode !in 200..299) return null
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val tag = json.optString("tag_name").removePrefix("v")
                val html = json.optString("html_url")
                if (tag.isBlank() || !html.startsWith("https://github.com/")) return null
                if (!isNewer(tag, BuildConfig.VERSION_NAME)) return null

                var apkUrl: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name").lowercase()
                        val url = asset.optString("browser_download_url")
                        if (name.endsWith(".apk") && url.startsWith("https://")) {
                            apkUrl = url
                            break
                        }
                    }
                }
                UpdateInfo(version = tag, releaseUrl = html, apkUrl = apkUrl)
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun postNotification(context: Context, info: UpdateInfo) {
        val prefs = context.getSharedPreferences("mediahub_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("update_notifications", true)) return

        // Open app so user can update from inside MediaHub Pro
        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_UPDATE_AVAILABLE, true)
            putExtra(EXTRA_UPDATE_VERSION, info.version)
            putExtra(EXTRA_UPDATE_APK_URL, info.apkUrl)
            putExtra(EXTRA_UPDATE_RELEASE_URL, info.releaseUrl)
        }
        val pending = PendingIntent.getActivity(
            context, 501, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, MediaHubApp.CHANNEL_UPDATES)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("MediaHub Pro update available")
            .setContentText("Version ${info.version} is ready. Tap to update in the app.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "MediaHub Pro ${info.version} is available. Tap this notification to open the app and install the update."
                )
            )
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(501, notification)
        } catch (_: SecurityException) {
        }
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

    const val EXTRA_UPDATE_AVAILABLE = "mediahub_update_available"
    const val EXTRA_UPDATE_VERSION = "mediahub_update_version"
    const val EXTRA_UPDATE_APK_URL = "mediahub_update_apk_url"
    const val EXTRA_UPDATE_RELEASE_URL = "mediahub_update_release_url"
}
