package com.mediahub.rebuilt.updates

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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

object UpdateChecker {

    const val EXTRA_UPDATE_AVAILABLE = "mediahub_update_available"
    const val EXTRA_UPDATE_VERSION = "mediahub_update_version"
    const val EXTRA_UPDATE_APK_URL = "mediahub_update_apk_url"
    const val EXTRA_UPDATE_RELEASE_URL = "mediahub_update_release_url"
    const val EXTRA_UPDATE_NOTES = "mediahub_update_notes"
    const val EXTRA_UPDATE_SIZE = "mediahub_update_size"
    const val EXTRA_UPDATE_MANDATORY = "mediahub_update_mandatory"

    fun check(context: Context, notify: Boolean = true, callback: ((UpdateInfo?) -> Unit)? = null) {
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            val info = withContext(Dispatchers.IO) { fetchLatest() }
            if (info != null && notify) postNotification(app, info)
            callback?.invoke(info)
        }
    }

    suspend fun checkInBackground(context: Context): UpdateInfo? =
        withContext(Dispatchers.IO) {
            val info = fetchLatest()
            if (info != null) postNotification(context.applicationContext, info)
            info
        }

    suspend fun downloadWithProgress(
        context: Context,
        apkUrl: String,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val out = File(dir, "MediaHubPro-update.apk")
            if (out.exists()) out.delete()

            val connection = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 180_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "MediaHub-Pro-Android/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Accept", "application/octet-stream")
            }
            try {
                if (connection.responseCode !in 200..299) {
                    return@withContext Result.failure(Exception("Download failed (HTTP ${connection.responseCode})"))
                }
                val total = connection.contentLengthLong.coerceAtLeast(0L)
                var downloaded = 0L
                connection.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
                    }
                }
                if (!out.exists() || out.length() < 500_000L) {
                    return@withContext Result.failure(Exception("Downloaded file is incomplete"))
                }
                Result.success(out)
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Result.failure(e)
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

    fun openUpdateScreen(context: Context, info: UpdateInfo) {
        val intent = Intent(context, UpdateActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_UPDATE_VERSION, info.version)
            putExtra(EXTRA_UPDATE_APK_URL, info.apkUrl)
            putExtra(EXTRA_UPDATE_RELEASE_URL, info.releaseUrl)
            putExtra(EXTRA_UPDATE_NOTES, info.releaseNotes)
            putExtra(EXTRA_UPDATE_SIZE, info.apkSizeBytes)
            putExtra(EXTRA_UPDATE_MANDATORY, info.mandatory)
        }
        context.startActivity(intent)
    }

    private fun fetchLatest(): UpdateInfo? {
        return try {
            val connection = (URL("https://api.github.com/repos/sarahbanaah-jpg/MEDIAHUB-PRO/releases/latest")
                .openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 12_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "MediaHub-Pro-Android/${BuildConfig.VERSION_NAME}")
            }
            try {
                if (connection.responseCode !in 200..299) return null
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val tag = json.optString("tag_name").removePrefix("v")
                val html = json.optString("html_url")
                if (tag.isBlank() || !html.startsWith("https://github.com/")) return null
                if (!isNewer(tag, BuildConfig.VERSION_NAME)) return null

                var apkUrl: String? = null
                var apkSize = 0L
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name").lowercase()
                        val url = asset.optString("browser_download_url")
                        if (name.endsWith(".apk") && url.startsWith("https://")) {
                            apkUrl = url
                            apkSize = asset.optLong("size", 0L)
                            break
                        }
                    }
                }

                val body = json.optString("body").orEmpty()
                val mandatory = body.contains("[mandatory]", ignoreCase = true) ||
                    body.contains("critical security", ignoreCase = true)

                UpdateInfo(
                    version = tag,
                    releaseUrl = html,
                    apkUrl = apkUrl,
                    releaseNotes = body.take(1200),
                    apkSizeBytes = apkSize,
                    publishedAt = json.optString("published_at"),
                    mandatory = mandatory
                )
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

        val open = Intent(context, UpdateActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_UPDATE_VERSION, info.version)
            putExtra(EXTRA_UPDATE_APK_URL, info.apkUrl)
            putExtra(EXTRA_UPDATE_RELEASE_URL, info.releaseUrl)
            putExtra(EXTRA_UPDATE_NOTES, info.releaseNotes)
            putExtra(EXTRA_UPDATE_SIZE, info.apkSizeBytes)
            putExtra(EXTRA_UPDATE_MANDATORY, info.mandatory)
        }
        val pending = PendingIntent.getActivity(
            context, 501, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, MediaHubApp.CHANNEL_UPDATES)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("MediaHub Pro ${info.version} is available")
            .setContentText("Tap to update inside the app · ${info.sizeLabel}")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "A new version is ready. Open MediaHub Pro and tap Update now to download and install without leaving the app."
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

    fun isNewer(remote: String, current: String): Boolean {
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
