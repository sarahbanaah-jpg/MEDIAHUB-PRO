package com.mediahub.rebuilt.ambient

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.media.audiofx.Visualizer
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.mediahub.rebuilt.service.PlaybackService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Low-distraction in-app charging clock with current-session artwork and metadata. */
class AmbientDisplayActivity : AppCompatActivity() {
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var battery: TextView
    private lateinit var trackInfo: TextView
    private lateinit var visualizerStatus: TextView
    private var visualizer: Visualizer? = null
    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVisualizer() else visualizerStatus.text = "Visualizer disabled: audio permission was not granted."
    }
    private lateinit var artwork: ImageView
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val now = Date()
            clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
            date.text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now)
            handler.postDelayed(this, 1000L)
        }
    }
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            battery.text = (if (charging) "⚡ Charging" else "Battery") + if (level >= 0 && scale > 0) " · ${level * 100 / scale}%" else ""
        }
    }
    private val playerListener = object : Player.Listener {
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = renderMetadata(mediaMetadata)
        override fun onPlaybackStateChanged(playbackState: Int) {
            controller?.currentMediaItem?.mediaMetadata?.let { renderMetadata(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(0xFF030208.toInt())
        }
        artwork = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(150), dp(150)).apply { bottomMargin = dp(20) }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0xFF191229.toInt())
            clipToOutline = true
        }
        clock = TextView(this).apply {
            text = "00:00"
            textSize = 76f
            letterSpacing = 0.04f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
        }
        date = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(0xFFB9A9C9.toInt())
        }
        battery = TextView(this).apply {
            text = "Battery status"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFFFF79B8.toInt())
            setPadding(0, dp(18), 0, 0)
        }
        trackInfo = TextView(this).apply {
            text = "No media playing"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFFD9CDE6.toInt())
            setPadding(0, dp(18), 0, 0)
        }
        root.addView(artwork)
        root.addView(clock)
        root.addView(date)
        root.addView(battery)
        root.addView(trackInfo)
        visualizerStatus = TextView(this).apply {
            text = "Audio visualizer is off"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(0xFF9A8BB0.toInt())
            setPadding(0, dp(20), 0, dp(8))
        }
        root.addView(visualizerStatus)
        root.addView(TextView(this).apply {
            text = "Enable audio visualizer"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFFFF79B8.toInt())
            setPadding(dp(18), dp(10), dp(18), dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVisualizer()
                else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        })
        root.addView(TextView(this).apply {
            text = "MEDIAHUB PRO  ·  Tap anywhere to exit"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(0xFF6F627B.toInt())
            setPadding(0, dp(48), 0, 0)
        })
        root.setOnClickListener { finish() }
        setContentView(root)
        tick.run()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, powerReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                controller = future.get()
                controller?.addListener(playerListener)
                controller?.currentMediaItem?.mediaMetadata?.let { renderMetadata(it) }
            } catch (_: Exception) {
                trackInfo.text = "No active media session"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun renderMetadata(metadata: MediaMetadata) {
        val title = metadata.title?.toString().orEmpty()
        val artist = metadata.artist?.toString().orEmpty()
        trackInfo.text = if (title.isBlank()) "No media playing" else listOf(title, artist).filter { it.isNotBlank() }.joinToString(" · ")
        val data = metadata.artworkData
        if (data != null && data.isNotEmpty()) {
            BitmapFactory.decodeByteArray(data, 0, data.size)?.let { artwork.setImageBitmap(it) }
        } else {
            artwork.setImageDrawable(null)
        }
    }

    private fun startVisualizer() {
        if (visualizer != null) return
        try {
            val effect = Visualizer(0)
            effect.captureSize = Visualizer.getCaptureSizeRange()[1]
            effect.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(effect: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                    if (waveform == null || waveform.isEmpty()) return
                    val glyphs = "▁▂▃▄▅▆▇█"
                    val bars = (0 until 16).map { index ->
                        val sample = waveform[index * waveform.size / 16].toInt() and 0xFF
                        glyphs[(kotlin.math.abs(sample - 128) * (glyphs.length - 1) / 128).coerceIn(0, glyphs.length - 1)]
                    }.joinToString(" ")
                    runOnUiThread { if (!isFinishing) { this@AmbientDisplayActivity.bars.text = bars; visualizerStatus.text = "Live playback waveform · processed on this phone" } }
                }
                override fun onFftDataCapture(effect: Visualizer?, fft: ByteArray?, samplingRate: Int) {}
            }, Visualizer.getMaxCaptureRate() / 2, true, false)
            effect.enabled = true
            visualizer = effect
            visualizerStatus.text = "Visualizer active · local audio waveform only"
        } catch (error: Exception) {
            visualizerStatus.text = "Visualizer unavailable: ${error.localizedMessage ?: "audio session not supported"}"
        }
    }

    override fun onStop() {
        try { unregisterReceiver(powerReceiver) } catch (_: Exception) { }
        visualizer?.run { enabled = false; release() }
        visualizer = null
        controller?.removeListener(playerListener)
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
