package com.mediahub.rebuilt.studio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.mediahub.rebuilt.BuildConfig
import java.io.File

/** Offline video trimming/export studio. Source files are never modified. */
class MediaHubStudioActivity : AppCompatActivity() {
    private var source: Uri? = null
    private lateinit var sourceLabel: TextView
    private lateinit var startInput: EditText
    private lateinit var endInput: EditText
    private lateinit var exportButton: Button
    private lateinit var status: TextView
    private var transformer: Transformer? = null

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            source = uri
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            sourceLabel.text = "Selected: ${uri.lastPathSegment ?: "video file"}"
            status.text = "Set trim start and end in seconds, then export an MP4 copy."
            exportButton.isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(36), dp(20), dp(30))
            setBackgroundColor(0xFF0B0618.toInt())
        }
        root.addView(TextView(this).apply {
            text = "MediaHub Studio"
            textSize = 27f
            setTextColor(0xFFFFFFFF.toInt())
        })
        root.addView(TextView(this).apply {
            text = "Offline video trim and MP4 export. Your original media is never changed or uploaded."
            textSize = 13f
            setTextColor(0xFFB9A9C9.toInt())
            setPadding(0, dp(8), 0, dp(18))
        })
        root.addView(Button(this).apply {
            text = "Choose a video"
            setOnClickListener { picker.launch(arrayOf("video/*")) }
        })
        sourceLabel = TextView(this).apply {
            text = "No video selected"
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, dp(14), 0, dp(14))
        }
        root.addView(sourceLabel)
        startInput = numberInput("Start time (seconds)", "0")
        endInput = numberInput("End time (seconds)", "30")
        root.addView(startInput)
        root.addView(endInput)
        exportButton = Button(this).apply {
            text = "Trim and export MP4"
            isEnabled = false
            setOnClickListener { exportTrimmedVideo() }
        }
        root.addView(exportButton)
        status = TextView(this).apply {
            text = "Select a video to begin."
            setTextColor(0xFFFF79B8.toInt())
            setPadding(0, dp(16), 0, 0)
        }
        root.addView(status)
        setContentView(root)
    }

    private fun numberInput(hintText: String, value: String) = EditText(this).apply {
        hint = hintText
        setText(value)
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        setSingleLine(true)
        setTextColor(0xFFFFFFFF.toInt())
        setHintTextColor(0xFF9A8BB0.toInt())
        setPadding(dp(12), dp(12), dp(12), dp(12))
    }

    private fun exportTrimmedVideo() {
        val uri = source ?: return
        val startSeconds = startInput.text.toString().toDoubleOrNull()
        val endSeconds = endInput.text.toString().toDoubleOrNull()
        if (startSeconds == null || endSeconds == null || startSeconds < 0 || endSeconds <= startSeconds) {
            Toast.makeText(this, "Enter a valid start and end time.", Toast.LENGTH_LONG).show()
            return
        }
        val startMs = (startSeconds * 1000).toLong()
        val endMs = (endSeconds * 1000).toLong()
        val outputDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
        if (!outputDir.exists()) outputDir.mkdirs()
        val output = File(outputDir, "MediaHub-Studio-${System.currentTimeMillis()}.mp4")
        exportButton.isEnabled = false
        status.text = "Exporting… keep MediaHub Studio open until it finishes."
        try {
            val clipped = Media3Item.Builder().setUri(uri)
                .setClippingConfiguration(
                    Media3Item.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build()
                ).build()
            val edited = EditedMediaItem.Builder(clipped).build()
            transformer = Transformer.Builder(this)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        runOnUiThread {
                            exportButton.isEnabled = true
                            status.text = "Export complete: ${output.name}"
                            val shareUri = FileProvider.getUriForFile(this@MediaHubStudioActivity, "${BuildConfig.APPLICATION_ID}.files", output)
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "video/mp4"
                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            startActivity(Intent.createChooser(share, "Share exported video"))
                        }
                    }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        runOnUiThread {
                            exportButton.isEnabled = true
                            status.text = "Export failed: ${exportException.localizedMessage ?: "unsupported format"}"
                            output.delete()
                        }
                    }
                }).build().also { it.start(edited, output.absolutePath) }
        } catch (error: Exception) {
            exportButton.isEnabled = true
            status.text = "Unable to start export: ${error.localizedMessage ?: "unknown error"}"
            output.delete()
        }
    }

    override fun onDestroy() {
        // Transformer work is allowed to finish only while this activity is active.
        transformer?.cancel()
        transformer = null
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
