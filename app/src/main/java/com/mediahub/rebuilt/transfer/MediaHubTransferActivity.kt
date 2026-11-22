package com.mediahub.rebuilt.transfer

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** User-controlled file and APK sharing through Android's standard share intents. */
class MediaHubTransferActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(contentResolver, "MediaHub files", uris.first()).also { clip ->
                uris.drop(1).forEach { clip.addItem(android.content.ClipData.Item(it)) }
            }
        }
        startActivity(Intent.createChooser(send, "Share with Xender, Quick Share or another app"))
        status.text = "${uris.size} file(s) ready in Android's share chooser."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(36), dp(22), dp(30))
            setBackgroundColor(0xFF08080D.toInt())
        }
        root.addView(TextView(this).apply {
            text = "MediaHub Share"; textSize = 29f; setTextColor(0xFFFFFFFF.toInt())
        })
        root.addView(TextView(this).apply {
            text = "Share files through Android's standard sharing interface. Xender, Quick Share and other compatible apps can appear as destinations. Incoming shares from those apps are received here too."
            textSize = 14f; setTextColor(0xFFB9B6C3.toInt()); setPadding(0, dp(12), 0, dp(18))
        })
        root.addView(Button(this).apply {
            text = "Choose files to share"
            setOnClickListener { picker.launch(arrayOf("*/*")) }
        })
        root.addView(Button(this).apply {
            text = "Share an installed app (APK)"
            setOnClickListener { showInstalledApps() }
        })
        status = TextView(this).apply {
            text = "Choose files, share an installed app, or select MediaHub Pro from another app's Share menu."
            textSize = 13f; setTextColor(0xFFFF79B8.toInt()); setPadding(0, dp(18), 0, 0)
        }
        root.addView(status)
        setContentView(root)
        receiveSharedFiles(intent)
    }

    private fun showInstalledApps() {
        val apps = packageManager.getInstalledApplications(0)
            .filter { it.packageName != packageName && (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
            .sortedBy { packageManager.getApplicationLabel(it).toString().lowercase() }
        if (apps.isEmpty()) {
            Toast.makeText(this, "No shareable installed apps were found.", Toast.LENGTH_LONG).show()
            return
        }
        val labels = apps.map { packageManager.getApplicationLabel(it).toString() }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Choose an installed app")
            .setItems(labels) { _, which -> shareApk(apps[which], labels[which]) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun shareApk(app: android.content.pm.ApplicationInfo, label: String) {
        try {
            val source = File(app.sourceDir)
            if (!source.isFile || !source.canRead()) throw IllegalStateException("Android denied access to this APK")
            val directory = File(cacheDir, "apk-share").apply { mkdirs() }
            val safe = label.replace(Regex("[^A-Za-z0-9._-]"), "_").take(64)
            val destination = File(directory, "$safe.apk")
            source.inputStream().use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
            val uri = FileProvider.getUriForFile(this, "$packageName.files", destination)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Share $label APK"))
            status.text = "Sharing $label APK. The receiving device may need to allow app installation."
        } catch (error: Exception) {
            status.text = "Could not share this app: ${error.localizedMessage ?: "APK access denied"}"
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent); receiveSharedFiles(intent)
    }

    @Suppress("DEPRECATION")
    private fun receiveSharedFiles(incoming: Intent) {
        val uris = when (incoming.action) {
            Intent.ACTION_SEND -> listOfNotNull(incoming.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: incoming.data)
            Intent.ACTION_SEND_MULTIPLE -> incoming.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            else -> emptyList()
        }
        if (uris.isEmpty()) return
        status.text = "Receiving ${uris.size} file(s)…"
        CoroutineScope(Dispatchers.IO).launch {
            var saved = 0
            uris.forEach { uri -> try { saveIncoming(uri); saved++ } catch (_: Exception) { } }
            withContext(Dispatchers.Main) {
                status.text = "Received $saved of ${uris.size} file(s). Saved in Downloads/MediaHub."
                Toast.makeText(this@MediaHubTransferActivity, status.text, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveIncoming(source: Uri) {
        val originalName = contentResolver.query(source, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "Received-file"
        val safeName = originalName.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
        val mime = contentResolver.getType(source) ?: "application/octet-stream"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "${System.currentTimeMillis()}-$safeName")
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MediaHub")
            }
            val destination = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create Downloads file")
            try {
                val input = contentResolver.openInputStream(source) ?: throw IllegalStateException("Source unavailable")
                val output = contentResolver.openOutputStream(destination) ?: throw IllegalStateException("Destination unavailable")
                input.use { i -> output.use { o -> i.copyTo(o) } }
            } catch (error: Exception) {
                contentResolver.delete(destination, null, null); throw error
            }
        } else {
            val directory = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
            directory.mkdirs()
            val destination = File(directory, "${UUID.randomUUID()}-$safeName")
            contentResolver.openInputStream(source)?.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("Source unavailable")
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}