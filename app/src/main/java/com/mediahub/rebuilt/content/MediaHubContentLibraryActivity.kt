package com.mediahub.rebuilt.content

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.mediahub.rebuilt.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

/** Optional offline packs, downloaded with redirect-aware HTTPS and verified byte counts. */
class MediaHubContentLibraryActivity : AppCompatActivity() {
    private data class Pack(
        val id: String, val title: String, val detail: String, val size: String,
        val file: String, val expectedBytes: Long
    )
    private val packs = listOf(
        Pack("wallpapers", "Ultra HD Wallpaper Collection", "100 original 2160 × 3840 wallpapers and lock-screen backgrounds.", "~291 MB", "mediahub-wallpapers.zip", 290728225L),
        Pack("audio", "Original Audio & Game Sound Pack", "Ambient loops, focus soundscapes and game sound effects.", "~21 MB", "mediahub-audio.zip", 20998113L),
        Pack("motion", "Motion Visuals & Game Art Pack", "Motion visuals and original game artwork.", "~39 MB", "mediahub-motion-games.zip", 39034855L)
    )
    private val bg = 0xFF08080D.toInt()
    private val panel = 0xFF171720.toInt()
    private val pink = 0xFFFF6B9F.toInt()
    private val prefs by lazy { getSharedPreferences("mediahub_content_packs", MODE_PRIVATE) }
    private val statusViews = mutableMapOf<String, TextView>()
    private val installButtons = mutableMapOf<String, Button>()
    private val downloadJobs = mutableMapOf<String, Job>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onDestroy() {
        downloadJobs.values.forEach { it.cancel() }
        downloadJobs.clear()
        super.onDestroy()
    }

    private fun render() {
        val scroll = ScrollView(this).apply { setBackgroundColor(bg) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(36), dp(18), dp(42))
        }
        scroll.addView(root)
        root.addView(TextView(this).apply {
            text = "‹  MEDIAHUB CONTENT LIBRARY"
            textSize = 20f; setTextColor(0xFFFFFFFF.toInt()); typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { finish() }
        })
        root.addView(TextView(this).apply {
            text = "Your offline collection"
            textSize = 26f; setTextColor(0xFFFFFFFF.toInt()); typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(20), 0, dp(6))
        })
        root.addView(TextView(this).apply {
            text = "Download original wallpapers, audio, motion visuals and game art. Failed downloads can be retried; each archive is checked against its published byte size before extraction."
            textSize = 14f; setTextColor(0xFFB9B6C3.toInt()); setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(0, 0, 0, dp(14))
        })
        root.addView(TextView(this).apply {
            text = "COLLECTION SIZE  ·  350.76 MB"
            textSize = 12f; setTextColor(pink); typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(14), dp(14), dp(14), dp(14)); background = card()
        })
        root.addView(TextView(this).apply {
            text = "Wi-Fi is recommended. You can explicitly allow mobile data for an individual download. Keep this screen open until the pack is installed."
            textSize = 12f; setTextColor(0xFFAAA6B5.toInt()); setPadding(0, dp(12), 0, dp(18))
        })
        packs.forEach { addPackCard(root, it) }
        setContentView(scroll)
    }

    private fun addPackCard(parent: LinearLayout, pack: Pack) {
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
        }
        statusViews[pack.id] = status
        box.addView(status)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply {
            text = if (archiveFile(pack).exists()) "DOWNLOAD AGAIN" else "DOWNLOAD PACK"
            setOnClickListener { chooseNetworkAndDownload(pack) }
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) }
        })
        actions.addView(Button(this).apply {
            text = "BROWSE"
            setOnClickListener { browsePack(pack) }
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
        })
        box.addView(actions)
        val install = Button(this).apply {
            text = if (packDirectory(pack).exists() && countFiles(packDirectory(pack)) > 0) "REINSTALL / APPLY" else "INSTALL"
            isEnabled = archiveFile(pack).exists()
            setOnClickListener { installDownloadedPack(pack) }
            layoutParams = LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) }
        }
        installButtons[pack.id] = install
        box.addView(install)
        parent.addView(box)
    }

    private fun chooseNetworkAndDownload(pack: Pack) {
        if (downloadJobs[pack.id]?.isActive == true) {
            Toast.makeText(this, "This pack is already downloading.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Download ${pack.title}?")
            .setMessage("${pack.size} · saved for offline use. Choose your connection.")
            .setItems(arrayOf("Wi-Fi only (recommended)", "Allow Wi-Fi and mobile data")) { _, which ->
                startDownload(pack, allowMobile = which == 1)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startDownload(pack: Pack, allowMobile: Boolean) {
        if (!hasAllowedNetwork(allowMobile)) {
            updateStatus(pack, if (allowMobile) "No internet connection. Connect and retry." else "Connect to Wi-Fi or choose mobile data.")
            return
        }
        val job = lifecycleScope.launch {
            updateStatus(pack, "Connecting securely…")
            try {
                val archive = withContext(Dispatchers.IO) { downloadArchive(pack) { received, total ->
                    runOnUiThread {
                        val percent = if (total > 0L) (received * 100 / total).toInt() else 0
                        updateStatus(pack, "Downloading · ${formatBytes(received)} / ${formatBytes(total)} · $percent%")
                    }
                } }
                updateStatus(pack, "DOWNLOAD COMPLETE · verified " + formatBytes(archive.length()) + " · tap INSTALL")
                installButtons[pack.id]?.isEnabled = true
                Toast.makeText(this@MediaHubContentLibraryActivity, pack.title + " downloaded. Tap INSTALL to apply it.", Toast.LENGTH_LONG).show()
            } catch (error: kotlinx.coroutines.CancellationException) {
                updateStatus(pack, "Download cancelled. Tap Download pack to retry.")
                throw error
            } catch (error: Exception) {
                updateStatus(pack, "Download/install failed: ${error.message ?: "check connection and storage"}. Tap to retry.")
            }
        }
        downloadJobs[pack.id] = job
        job.invokeOnCompletion { downloadJobs.remove(pack.id) }
    }

    private fun archiveFile(pack: Pack): File {
        val directory = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir, "mediahub-packs")
        return File(directory, pack.file)
    }

    private fun installDownloadedPack(pack: Pack) {
        val archive = archiveFile(pack)
        if (!archive.isFile || archive.length() != pack.expectedBytes) {
            updateStatus(pack, "Verified download not found. Download this pack again.")
            installButtons[pack.id]?.isEnabled = false
            return
        }
        if (downloadJobs[pack.id]?.isActive == true) return
        val job = lifecycleScope.launch {
            installButtons[pack.id]?.isEnabled = false
            updateStatus(pack, "Installing pack…")
            try {
                withContext(Dispatchers.IO) { installArchive(pack, archive) }
                prefs.edit().putBoolean("installed_${pack.id}", true).apply()
                updateStatus(pack, "READY OFFLINE · ${countFiles(packDirectory(pack))} files installed")
                installButtons[pack.id]?.apply { text = "REINSTALL / APPLY"; isEnabled = true }
                Toast.makeText(this@MediaHubContentLibraryActivity, pack.title + " installed and ready offline.", Toast.LENGTH_LONG).show()
            } catch (error: Exception) {
                updateStatus(pack, "Install failed: " + (error.message ?: "check storage"))
                installButtons[pack.id]?.isEnabled = archive.isFile
            }
        }
        downloadJobs[pack.id] = job
        job.invokeOnCompletion { downloadJobs.remove(pack.id) }
    }

    private fun hasAllowedNetwork(allowMobile: Boolean): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return allowMobile || caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private suspend fun downloadArchive(pack: Pack, onProgress: (Long, Long) -> Unit): File {
        val directory = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir, "mediahub-packs")
        if (!directory.exists() && !directory.mkdirs()) throw IllegalStateException("Cannot create download folder")
        val partial = File(directory, "${pack.file}.part")
        val complete = File(directory, pack.file)
        partial.delete()
        val url = URL("https://github.com/sarahbanaah-jpg/MEDIAHUB-PRO/releases/download/content-packs-v1.0/${pack.file}")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 25000
            readTimeout = 60000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "MediaHubPro-Android/4.2")
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("Release server returned HTTP $code")
            val advertised = connection.contentLengthLong
            val expected = pack.expectedBytes
            if (advertised > 0 && advertised != expected) {
                throw IllegalStateException("Server file size mismatch (expected ${formatBytes(expected)}, received ${formatBytes(advertised)})")
            }
            var received = 0L
            connection.inputStream.buffered().use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        received += count
                        if (received > expected) throw IllegalStateException("Downloaded file is larger than the published archive")
                        onProgress(received, expected)
                    }
                    output.flush()
                }
            }
            if (received != expected) throw IllegalStateException("Incomplete download: received ${formatBytes(received)} of ${formatBytes(expected)}")
            if (complete.exists()) complete.delete()
            if (!partial.renameTo(complete)) throw IllegalStateException("Could not save the completed archive")
            return complete
        } catch (error: Exception) {
            partial.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun installArchive(pack: Pack, archive: File) {
        val destination = packDirectory(pack)
        val staging = File(destination.parentFile, "${pack.id}.staging")
        staging.deleteRecursively()
        if (!staging.mkdirs()) throw IllegalStateException("Cannot create extraction folder")
        try {
            var extracted = 0L
            ZipInputStream(archive.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val target = File(staging, entry.name).canonicalFile
                    val safeRoot = staging.canonicalPath + File.separator
                    if (!target.canonicalPath.startsWith(safeRoot) && target.canonicalPath != staging.canonicalPath) {
                        throw SecurityException("Archive contains an unsafe path")
                    }
                    if (entry.isDirectory) target.mkdirs() else {
                        target.parentFile?.mkdirs()
                        target.outputStream().buffered().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                extracted += count
                                if (extracted > 600L * 1024L * 1024L) throw IllegalStateException("Archive exceeds the safe extraction limit")
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            if (countFiles(staging) == 0) throw IllegalStateException("Archive contained no files")
            destination.deleteRecursively()
            if (!staging.renameTo(destination)) throw IllegalStateException("Could not install the extracted collection")
        } catch (error: Exception) {
            staging.deleteRecursively()
            throw error
        }
    }

    private fun browsePack(pack: Pack) {
        val files = packDirectory(pack).walkTopDown().filter { it.isFile }.toList().sortedBy { it.name.lowercase() }
        if (files.isEmpty()) {
            Toast.makeText(this, "Download this pack first.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this).setTitle("${pack.title} · ${files.size} files")
            .setItems(files.map { it.name }.toTypedArray()) { _, which -> openFile(files[which]) }
            .setNegativeButton("Close", null).show()
    }

    private fun openFile(file: File) {
        try {
            val mime = when (file.extension.lowercase()) {
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
            Toast.makeText(this, "No compatible viewer is installed.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun packDirectory(pack: Pack) = File(File(getExternalFilesDir(null) ?: filesDir, "content-packs"), pack.id)

    private fun packStatus(pack: Pack): String {
        val dir = packDirectory(pack)
        return if (dir.exists() && countFiles(dir) > 0) "Installed · ${countFiles(dir)} files · available offline"
        else "Not downloaded"
    }

    private fun updateStatus(pack: Pack, value: String) { statusViews[pack.id]?.text = value }
    private fun countFiles(dir: File): Int = dir.walkTopDown().count { it.isFile }
    private fun formatBytes(bytes: Long): String = if (bytes >= 1_000_000L) "${"%.1f".format(bytes / 1_000_000.0)} MB" else "${bytes / 1000} KB"
    private fun card() = GradientDrawable().apply {
        setColor(panel); cornerRadius = dp(20).toFloat(); setStroke(dp(1), 0x33FFFFFF)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
