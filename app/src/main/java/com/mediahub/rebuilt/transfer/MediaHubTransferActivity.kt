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
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Offline nearby sharing via Android Sharesheet/Quick Share, plus inbound share receiving. */
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
        startActivity(Intent.createChooser(send, "Send with Xender, Quick Share or another app"))
        status.text = "${uris.size} file(s) ready to share. Choose Quick Share for nearby offline transfer."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(36), dp(22), dp(30))
            setBackgroundColor(0xFF0B0618.toInt())
        }
        root.addView(TextView(this).apply {
            text = "MEDIAHUB SHARE"
            textSize = 27f
            setTextColor(0xFFFFFFFF.toInt())
        })
        root.addView(TextView(this).apply {
            text = "Send any file type — photos, videos, music, documents, ZIP archives and APK app installers. Share directly with another app using Android Sharesheet (including Xender when installed), or receive files shared from Xender and other apps. Nearby sharing works offline when your chosen sharing app supports local transfer. MediaHub saves received files in Downloads/MediaHub."
            textSize = 14f
            setTextColor(0xFFB9A9C9.toInt())
            setPadding(0, dp(12), 0, dp(18))
        })
        root.addView(Button(this).apply {
            text = "Choose files, including APK apps"
            setOnClickListener { picker.launch(arrayOf("*/*")) }
        })
        status = TextView(this).apply {
            text = "Choose files (including APKs) to share, or use Share in Xender to send files into MediaHub."
            textSize = 13f
            setTextColor(0xFFFF79B8.toInt())
            setPadding(0, dp(18), 0, 0)
        }
        root.addView(status)
        setContentView(root)
        receiveSharedFiles(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveSharedFiles(intent)
    }

    private fun receiveSharedFiles(incoming: Intent) {
        val uris = when (incoming.action) {
            Intent.ACTION_SEND -> listOfNotNull(incoming.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                ?: incoming.data)
            Intent.ACTION_SEND_MULTIPLE -> incoming.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            else -> emptyList()
        }
        if (uris.isEmpty()) return
        status.text = "Receiving ${uris.size} file(s)…"
        CoroutineScope(Dispatchers.IO).launch {
            var saved = 0
            val failures = mutableListOf<String>()
            uris.forEach { uri ->
                try {
                    saveIncoming(uri)
                    saved++
                } catch (error: Exception) {
                    failures += error.localizedMessage ?: "unknown error"
                }
            }
            withContext(Dispatchers.Main) {
                status.text = "Received $saved of ${uris.size} file(s). Saved in Downloads/MediaHub."
                Toast.makeText(this@MediaHubTransferActivity, status.text, Toast.LENGTH_LONG).show()
                if (failures.isNotEmpty()) Toast.makeText(this@MediaHubTransferActivity, "Some files could not be received.", Toast.LENGTH_LONG).show()
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
                contentResolver.delete(destination, null, null)
                throw error
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
