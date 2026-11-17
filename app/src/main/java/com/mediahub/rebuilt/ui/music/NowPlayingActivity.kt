package com.mediahub.rebuilt.ui.music

import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.mediahub.rebuilt.service.PlaybackService

/** Neon dream full-screen player matching the design mockup. */
class NowPlayingActivity : AppCompatActivity() {

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var sleepTimer: CountDownTimer? = null

    private lateinit var titleView: TextView
    private lateinit var artistView: TextView
    private lateinit var positionView: TextView
    private lateinit var durationView: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var playButton: TextView
    private lateinit var shuffleButton: TextView
    private lateinit var repeatButton: TextView
    private lateinit var sleepLabel: TextView
    private lateinit var waveView: TextView

    private val tickRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            window.decorView.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = 0x00000000
        window.navigationBarColor = 0x00000000

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF2A1050.toInt(), 0xFF0B0618.toInt(), 0xFF05030E.toInt())
            )
            setPadding(dp(24), dp(52), dp(24), dp(36))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Top bar
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        top.addView(TextView(this).apply {
            text = "‹"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 28f
            setPadding(dp(4), 0, dp(16), 0)
            setOnClickListener { finish() }
        })
        top.addView(TextView(this).apply {
            text = "Playing"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        top.addView(TextView(this).apply {
            text = "···"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 20f
            setPadding(dp(12), 0, 0, 0)
            setOnClickListener { showMore() }
        })
        root.addView(top)

        // Circular artwork
        root.addView(TextView(this).apply {
            text = "♪"
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 72f
            layoutParams = LinearLayout.LayoutParams(dp(260), dp(260)).apply { topMargin = dp(36) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                colors = intArrayOf(0xFF7B2FF7.toInt(), 0xFFFF2D95.toInt(), 0xFF5CE1FF.toInt())
                orientation = GradientDrawable.Orientation.TL_BR
            }
            elevation = 18f
        })

        titleView = TextView(this).apply {
            text = "—"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(28), 0, dp(4))
        }
        root.addView(titleView)

        artistView = TextView(this).apply {
            text = "—"
            setTextColor(0xFFD4C4E8.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
        }
        root.addView(artistView)

        // Waveform visual
        waveView = TextView(this).apply {
            text = "▁▂▃▅▇▅▃▂▁▂▃▅▇█▇▅▃▂▁▂▃▅▃▁"
            setTextColor(0xFFFF2D95.toInt())
            textSize = 16f
            gravity = Gravity.CENTER
            letterSpacing = 0.05f
            setPadding(0, dp(18), 0, dp(6))
        }
        root.addView(waveView)

        seekBar = SeekBar(this).apply {
            max = 1000
            layoutParams = LinearLayout.LayoutParams(-1, -2)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {}
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    val p = controller ?: return
                    val dur = p.duration.coerceAtLeast(1L)
                    p.seekTo((dur * (sb?.progress ?: 0)) / 1000L)
                }
            })
        }
        root.addView(seekBar)

        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        positionView = TextView(this).apply {
            text = "0:00"
            setTextColor(0xFF9A8BB0.toInt())
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        durationView = TextView(this).apply {
            text = "0:00"
            setTextColor(0xFF9A8BB0.toInt())
            textSize = 12f
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        timeRow.addView(positionView)
        timeRow.addView(durationView)
        root.addView(timeRow)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) }
        }
        shuffleButton = controlBtn("⇄") { toggleShuffle() }
        controls.addView(shuffleButton)
        controls.addView(controlBtn("⏮") { controller?.seekToPreviousMediaItem() })
        playButton = controlBtn("▶", primary = true) { togglePlay() }
        controls.addView(playButton)
        controls.addView(controlBtn("⏭") { controller?.seekToNextMediaItem() })
        repeatButton = controlBtn("🔁") { cycleRepeat() }
        controls.addView(repeatButton)
        root.addView(controls)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22) }
        }
        actions.addView(chip("Sleep") { showSleepTimer() })
        actions.addView(chip("EQ") { showEqualizer() })
        actions.addView(chip("Bass") { toggleBass() })
        root.addView(actions)

        sleepLabel = TextView(this).apply {
            text = ""
            setTextColor(0xFFFF6BB5.toInt())
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
        }
        root.addView(sleepLabel)

        root.addView(TextView(this).apply {
            text = "© Grimy. All rights reserved."
            setTextColor(0xFF6B5A80.toInt())
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, 0)
        })

        setContentView(root)
        connectController()
    }

    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                controller = future.get()
                refreshMetadata()
                updateProgress()
                window.decorView.post(tickRunnable)
            } catch (_: Exception) {
                Toast.makeText(this, "Player unavailable", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun refreshMetadata() {
        val meta = controller?.currentMediaItem?.mediaMetadata
        titleView.text = meta?.title?.toString() ?: "Nothing playing"
        artistView.text = meta?.artist?.toString() ?: meta?.albumTitle?.toString() ?: ""
        playButton.text = if (controller?.isPlaying == true) "⏸" else "▶"
        shuffleButton.alpha = if (controller?.shuffleModeEnabled == true) 1f else 0.45f
        repeatButton.alpha = when (controller?.repeatMode) {
            Player.REPEAT_MODE_ONE, Player.REPEAT_MODE_ALL -> 1f
            else -> 0.45f
        }
    }

    private fun updateProgress() {
        val p = controller ?: return
        val dur = p.duration.coerceAtLeast(0L)
        val pos = p.currentPosition.coerceAtLeast(0L)
        if (dur > 0) seekBar.progress = ((pos * 1000) / dur).toInt().coerceIn(0, 1000)
        positionView.text = formatMs(pos)
        durationView.text = formatMs(dur)
        playButton.text = if (p.isPlaying) "⏸" else "▶"
        val meta = p.currentMediaItem?.mediaMetadata
        if (meta?.title != null) titleView.text = meta.title
        if (meta?.artist != null) artistView.text = meta.artist
        // Simple animated wave feel
        if (p.isPlaying) {
            waveView.alpha = 0.7f + ((pos / 400L) % 3) * 0.1f
        }
    }

    private fun togglePlay() {
        val p = controller ?: return
        if (p.isPlaying) p.pause() else p.play()
        refreshMetadata()
    }

    private fun toggleShuffle() {
        val p = controller ?: return
        p.shuffleModeEnabled = !p.shuffleModeEnabled
        refreshMetadata()
        Toast.makeText(this, if (p.shuffleModeEnabled) "Shuffle on" else "Shuffle off", Toast.LENGTH_SHORT).show()
    }

    private fun cycleRepeat() {
        val p = controller ?: return
        p.repeatMode = when (p.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        refreshMetadata()
    }

    private fun showMore() {
        AlertDialog.Builder(this)
            .setTitle("Playback")
            .setItems(arrayOf("Sleep timer", "Equalizer", "Bass boost")) { _, which ->
                when (which) {
                    0 -> showSleepTimer()
                    1 -> showEqualizer()
                    2 -> toggleBass()
                }
            }
            .show()
    }

    private fun showSleepTimer() {
        val options = arrayOf("Off", "15 minutes", "30 minutes", "45 minutes", "60 minutes")
        AlertDialog.Builder(this)
            .setTitle("Sleep timer")
            .setItems(options) { _, which ->
                sleepTimer?.cancel()
                sleepTimer = null
                if (which == 0) {
                    sleepLabel.text = ""
                    return@setItems
                }
                val minutes = listOf(0, 15, 30, 45, 60)[which]
                sleepLabel.text = "Sleep in ${minutes} min"
                sleepTimer = object : CountDownTimer(minutes * 60_000L, 30_000L) {
                    override fun onTick(ms: Long) {
                        sleepLabel.text = "Sleep in ${(ms / 60_000L) + 1} min"
                    }
                    override fun onFinish() {
                        controller?.pause()
                        sleepLabel.text = "Sleep timer ended"
                    }
                }.start()
            }
            .show()
    }

    private fun showEqualizer() {
        try {
            startActivity(Intent("android.media.action.DISPLAY_AUDIO_EFFECT_CONTROL_PANEL"))
            return
        } catch (_: Exception) {
        }
        try {
            equalizer?.release()
            equalizer = Equalizer(0, 0).apply { enabled = true }
            val eq = equalizer ?: return
            val presets = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
            AlertDialog.Builder(this)
                .setTitle("Equalizer")
                .setItems(presets.toTypedArray()) { _, which ->
                    try { eq.usePreset(which.toShort()) } catch (_: Exception) {}
                }
                .show()
        } catch (_: Exception) {
            Toast.makeText(this, "Equalizer unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleBass() {
        try {
            if (bassBoost == null) {
                bassBoost = BassBoost(0, 0).apply { enabled = true; setStrength(700.toShort()) }
                Toast.makeText(this, "Bass boost on", Toast.LENGTH_SHORT).show()
            } else {
                val on = !(bassBoost?.enabled ?: false)
                bassBoost?.enabled = on
                Toast.makeText(this, if (on) "Bass boost on" else "Bass boost off", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Bass boost unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    private fun controlBtn(label: String, primary: Boolean = false, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(0xFFFFFFFF.toInt())
            textSize = if (primary) 24f else 18f
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = if (primary) GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                colors = intArrayOf(0xFFFF2D95.toInt(), 0xFF9B6DFF.toInt())
                orientation = GradientDrawable.Orientation.TL_BR
            } else null
            layoutParams = LinearLayout.LayoutParams(if (primary) dp(68) else dp(52), if (primary) dp(68) else dp(52)).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun chip(label: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                setColor(0x33FFFFFF)
                cornerRadius = dp(16).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
                marginStart = dp(6)
                marginEnd = dp(6)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun formatMs(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSec = ms / 1000
        return "%d:%02d".format(totalSec / 60, totalSec % 60)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        window.decorView.removeCallbacks(tickRunnable)
        sleepTimer?.cancel()
        try { equalizer?.release() } catch (_: Exception) {}
        try { bassBoost?.release() } catch (_: Exception) {}
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}
