package com.mediahub.rebuilt.content

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.util.zip.ZipInputStream

/** Optional, user-controlled offline media collections. These are separate from app updates. */
class MediaHubContentLibraryActivity : AppCompatActivity() {
    private data class Pack(val id: String, val title: String, val detail: String, val size: String, val file: String)
    private val packs = listOf(
        Pack("wallpapers", "Ultra HD Wallpaper Collection", "100 original 2160 × 3840 abstract wallpapers, phone-ready portrait artwork and lock-screen backgrounds.", "~291 MB", "mediahub-wallpapers.zip"),
        Pack("audio", "Original Audio & Game Sound Pack", "Original ambient loops, focus soundscapes and short game sound effects for offline use.", "~21 MB", "mediahub-audio.zip"),
        Pack("motion", "Motion Visuals & Game Art Pack", "Animated video backgrounds and original game artwork for creative projects.", "~39 MB", "mediahub-motion-games.zip")
    )
    private val bg = 0xFF08080D.toInt()
    private val panel = 0xFF171720.toInt()
    private val pink = 0xFFFF6B9F.toInt()
    private val prefs by lazy { getSharedPreferences("mediahub_content_packs", MODE_PRIVATE) }
    private val manager by lazy { getSystemService(DOWNLOAD_SERVICE) as DownloadManager }
    private val statusViews = mutableMapOf<String, TextView>()
    private lateinit var root: LinearLayout
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            val pack = packs.firstOrNull { prefs.getLong("download_${it.id}", -2L) == id } ?: return
            finishDownload(pack, id)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(receiver, filter)
        render()
        packs.forEach { pack ->
            val id = prefs.getLong("download_${pack.id}", -1L)
            if (id > 0L) checkExistingDownload(pack, id)
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun render() {
        val scroll = ScrollView(this).apply { setBackgroundColor(bg) }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(36), dp(18), dp(42))
        }
        scroll.addView(root)
        root.addView(TextView(this).apply {
            text = "‹  MEDIAHUB CONTENT LIBRARY"
            textSize = 21f; setTextColor(0xFFFFFFFF.toInt()); typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { finish() }
        })
        root.addView(TextView(this).apply {
            text = "Build your offline collection"
            textSize = 25f; setTextColor(0xFFFFFFFF.toInt()); typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(20), 0, dp(6))
        })
        root.addView(TextView(this).apply {
            text = "A premium collection of original wallpapers, audio, motion visuals and game artwork. Download only the packs you want; once installed, their contents work offline."
            textSize = 14f; setTextColor(0xFFB9B6C3.toInt()); setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(0, 0, 0, dp(14))
        })
        val total = TextView(this).apply {
            text = "COLLECTION SIZE  ·  APPROXIMATELY 351 MB"
            textSize = 12f; setTextColor(pink); typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(14), dp(14), dp(14), dp(14)); background = card()
        }
        root.addView(total)
        root.addView(TextView(this).apply {
            text = "Downloads are optional and use your device storage. Wi-Fi is selected by default; you can explicitly allow mobile data for an individual pack."
            textSize = 12f; setTextColor(0xFFAAA6B5.toInt()); setPadding(0, dp(12), 0, dp(18))
        })
        packs.forEach { pack -> addPackCard(pack) }
        setContentView(scroll)
    }

    private fun addPackCard(pack: Pack) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = card()
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        }
        box.addView(TextView(this).apply {
            text = pack.title; textSize = 17f; setTextColor(0xFFFFFFFF.toInt()); typeface = Typeface.DEFAULT_BOLD
        })
        box.addView(TextView(this).apply {
            text = "${pack.size}  ·  ${pack.detail}"
            textSize = 13f; setTextColor(0xFFB9B6C3.toInt()); setPadding(0, dp(7), 0, dp(10))
        })
        val status = TextView(this).apply {
            text = packStatus(pack); textSize = 12f; setTextColor(pink); setPadding(0, 0, 0, dp(10))
            tag = "status_${pack.id}"
        }
        statusViews[pack.id] = status
        box.addView(status)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply {
            text = "Download pack"
            setOnClickListener { chooseNetworkAndDownload(pack) }
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) }
        })
        actions.addView(Button(this).apply {
            text = "Browse"
            setOnClickListener { browsePack(pack) }
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
        })
        box.addView(actions)
        root.addView(box)
    }

    private fun chooseNetworkAndDownload(pack: Pack) {
        AlertDialog.Builder(this)
            .setTitle("Download ${pack.title}?")
            .setMessage("${pack.size} estimated. The pack is saved to MediaHub's app storage and remains available offline. Choose how to download it.")
            .setItems(arrayOf("Wi-Fi only (recommended)", "Allow Wi-Fi and mobile data")) { _, which ->
                startDownload(pack, allowMobile = which == 1)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startDownload(pack: Pack, allowMobile: Boolean) {
        val url = "https://github.com/sarahbanaah-jpg/MEDIAHUB-PRO/releases/download/content-packs-v1.0/${pack.file}"
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("MediaHub · ${pack.title}")
                .setDescription("Downloading optional offline content")
                .setMimeType("application/zip")
                .addRequestHeader("User-Agent", "MediaHubPro-Android")
                .addRequestHeader("Accept", "application/octet-stream")
                .setVisibleInDownloadsUi(true)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverRoaming(false)
                .setAllowedNetworkTypes(
                    if (allowMobile) DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
                    else DownloadManager.Request.NETWORK_WIFI
                )
                .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, pack.file)
            val id = manager.enqueue(request)
            prefs.edit().putLong("download_${pack.id}", id).apply()
            updateStatus(pack, "Download queued · progress appears in Android notifications")
            Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            updateStatus(pack, "Could not start download: ${e.localizedMessage ?: "check connection"}")
        }
    }

    private fun checkExistingDownload(pack: Pack, id: Long) {
        val cursor = manager.query(DownloadManager.Query().setFilterById(id)) ?: return
        cursor.use {
            if (!it.moveToFirst()) return
            when (it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_SUCCESSFUL -> finishDownload(pack, id)
                DownloadManager.STATUS_FAILED -> updateStatus(pack, "Download failed: ${downloadFailureReason(id)} · check connection and retry")
                DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> updateStatus(pack, "Download in progress · see Android notifications")
            }
        }
    }

    private fun downloadFailureReason(id: Long): String {
        val cursor = manager.query(DownloadManager.Query().setFilterById(id))
            ?: return "download service unavailable"
        return cursor.use {
            if (!it.moveToFirst()) return@use "download record missing"
            when (it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))) {
                DownloadManager.ERROR_CANNOT_RESUME -> "server could not resume the download"
                DownloadManager.ERROR_DEVICE_NOT_FOUND -> "storage device unavailable"
                DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "file already exists"
                DownloadManager.ERROR_FILE_ERROR -> "storage write failed"
                DownloadManager.ERROR_HTTP_DATA_ERROR -> "server response was interrupted"
                DownloadManager.ERROR_INSUFFICIENT_SPACE -> "not enough free storage"
                DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "too many download redirects"
                DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "server rejected the request"
                DownloadManager.ERROR_UNKNOWN -> "unknown network or server error"
                DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "waiting for Wi-Fi; allow mobile data to continue"
                DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "waiting for an internet connection"
                else -> "download service error ${it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))}"
            }
        }
    }

    private fun finishDownload(pack: Pack, id: Long) {
        val statusCursor = manager.query(DownloadManager.Query().setFilterById(id)) ?: return
        val successful = statusCursor.use {
            it.moveToFirst() && it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
        }
        if (!successful) {
            updateStatus(pack, "Download failed: ${downloadFailureReason(id)} · check connection and retry")
            return
        }
        try {
            val uri = manager.getUriForDownloadedFile(id) ?: throw IllegalStateException("Downloaded file is unavailable")
            val base = File(getExternalFilesDir(null), "content-packs")
            val destination = File(base, pack.id)
            destination.mkdirs()
            var extracted = 0L
            ZipInputStream(contentResolver.openInputStream(uri) ?: throw IllegalStateException("Cannot open downloaded pack")).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val target = File(destination, entry.name).canonicalFile
                    val safeRoot = destination.canonicalPath + File.separator
                    if (!target.canonicalPath.startsWith(safeRoot) && target.canonicalPath != destination.canonicalPath) {
                        throw SecurityException("Unsafe archive entry")
                    }
                    if (entry.isDirectory) target.mkdirs() else {
                        target.parentFile?.mkdirs()
                        target.outputStream().buffered().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                extracted += count
                                if (extracted > 600L * 1024L * 1024L) throw IllegalStateException("Pack exceeds the safe extraction limit")
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            manager.remove(id)
            prefs.edit().putBoolean("installed_${pack.id}", true).apply()
            updateStatus(pack, "Ready offline · ${countFiles(destination)} files installed")
            Toast.makeText(this, "${pack.title} is ready offline", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            updateStatus(pack, "Could not install pack: ${e.localizedMessage ?: "invalid archive"}")
        }
    }

    private fun browsePack(pack: Pack) {
        val dir = File(File(getExternalFilesDir(null), "content-packs"), pack.id)
        val files = dir.walkTopDown().filter { it.isFile }.toList().sortedBy { it.name.lowercase() }
        if (files.isEmpty()) {
            Toast.makeText(this, "Download this pack first", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("${pack.title} · ${files.size} files")
            .setItems(files.map { it.name }.toTypedArray()) { _, which -> openFile(files[which]) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openFile(file: File) {
        try {
            val ext = file.extension.lowercase()
            val mime = when (ext) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                "mp4" -> "video/mp4"
                else -> "*/*"
            }
            val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: Exception) {
            Toast.makeText(this, "No compatible viewer is installed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun packStatus(pack: Pack): String {
        val dir = File(File(getExternalFilesDir(null), "content-packs"), pack.id)
        return if (dir.exists() && countFiles(dir) > 0) "Installed · ${countFiles(dir)} files · available offline"
        else "Not downloaded"
    }

    private fun updateStatus(pack: Pack, value: String) {
        statusViews[pack.id]?.text = value
    }

    private fun countFiles(dir: File): Int = dir.walkTopDown().count { it.isFile }
    private fun card() = GradientDrawable().apply {
        setColor(panel); cornerRadius = dp(20).toFloat(); setStroke(dp(1), 0x33FFFFFF)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
