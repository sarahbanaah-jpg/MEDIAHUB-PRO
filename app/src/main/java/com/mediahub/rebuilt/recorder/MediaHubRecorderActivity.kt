package com.mediahub.rebuilt.recorder

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File

/** Local voice recorder. Audio remains in the app's private files directory. */
class MediaHubRecorderActivity : AppCompatActivity() {
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var recording: File? = null
    private lateinit var status: TextView
    private lateinit var startButton: Button
    private lateinit var playButton: Button

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else status.text = "Microphone permission is required to record."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(42), dp(22), dp(28))
            setBackgroundColor(0xFF08080D.toInt())
        }
        root.addView(TextView(this).apply {
            text = "MEDIAHUB RECORDER"
            textSize = 23f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, dp(10))
        })
        root.addView(TextView(this).apply {
            text = "Record voice notes offline. Recordings are stored privately on this device."
            textSize = 14f
            setTextColor(0xFFB9B6C3.toInt())
            setPadding(0, 0, 0, dp(24))
        })
        status = TextView(this).apply {
            text = "Ready to record"
            textSize = 15f
            setTextColor(0xFFFF6B9F.toInt())
            setPadding(0, dp(18), 0, dp(18))
        }
        root.addView(status)
        startButton = Button(this).apply {
            text = "●  Start recording"
            setOnClickListener {
                if (recorder != null) stopRecording()
                else if (ContextCompat.checkSelfPermission(this@MediaHubRecorderActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
                else permission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        root.addView(startButton)
        playButton = Button(this).apply {
            text = "▶  Play latest recording"
            isEnabled = false
            setOnClickListener { playLatest() }
        }
        root.addView(playButton)
        root.addView(Button(this).apply { text = "Delete latest recording"; setOnClickListener {
            stopPlayback()
            if (recording?.delete() == true) {
                recording = null; playButton.isEnabled = false; status.text = "Latest recording deleted."
            } else status.text = "No saved recording to delete."
        } })
        root.addView(TextView(this).apply {
            text = "M4A · AAC audio\nNo upload · No account · Works offline"
            textSize = 12f
            setTextColor(0xFF898694.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(28), 0, 0)
        })
        setContentView(root)
        val dir = File(filesDir, "Recordings")
        recording = dir.listFiles()?.filter { it.extension == "m4a" }?.maxByOrNull { it.lastModified() }
        playButton.isEnabled = recording?.exists() == true
    }

    private fun startRecording() {
        try {
            val dir = File(filesDir, "Recordings").apply { mkdirs() }
            val output = File(dir, "VoiceNote_${System.currentTimeMillis()}.m4a")
            val active = MediaRecorder(this)
            active.setAudioSource(MediaRecorder.AudioSource.MIC)
            active.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            active.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            active.setAudioSamplingRate(44100)
            active.setAudioEncodingBitRate(128000)
            active.setOutputFile(output.absolutePath)
            active.prepare()
            active.start()
            recorder = active
            recording = output
            startButton.text = "■  Stop recording"
            playButton.isEnabled = false
            status.text = "Recording… tap Stop when finished."
        } catch (error: Exception) {
            recorder?.release(); recorder = null
            status.text = "Could not start recording: ${error.localizedMessage ?: "microphone unavailable"}"
        }
    }

    private fun stopRecording() {
        val active = recorder ?: return
        try {
            active.stop()
            status.text = "Saved: ${recording?.name ?: "voice note"}"
            playButton.isEnabled = recording?.length()?.let { it > 0L } == true
        } catch (_: RuntimeException) {
            recording?.delete()
            status.text = "Recording was too short. Try again."
            playButton.isEnabled = false
        } finally {
            active.release()
            recorder = null
            startButton.text = "●  Start recording"
        }
    }

    private fun playLatest() {
        val file = recording?.takeIf { it.exists() && it.length() > 0L } ?: return
        stopPlayback()
        try {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { stopPlayback(); status.text = "Playback finished." }
                prepare()
                start()
            }
            status.text = "Playing ${file.name}"
        } catch (error: Exception) {
            stopPlayback()
            status.text = "Unable to play recording: ${error.localizedMessage ?: "unsupported audio"}"
        }
    }

    private fun stopPlayback() {
        player?.let { try { if (it.isPlaying) it.stop() } catch (_: Exception) { }; it.release() }
        player = null
    }

    override fun onStop() {
        if (recorder != null) stopRecording()
        stopPlayback()
        super.onStop()
    }

    override fun onDestroy() {
        recorder?.let { try { it.stop() } catch (_: Exception) { }; it.release() }
        recorder = null
        stopPlayback()
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
