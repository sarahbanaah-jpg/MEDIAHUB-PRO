package com.mediahub.rebuilt.ui.music

import android.content.ComponentName
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

/**
 * Boomplay-inspired full-screen now playing screen with
 * shuffle / repeat, sleep timer and simple equalizer controls.
 */
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
            setBackgroundColor(0xFF05040A.toInt())
            setPadding(dp(24), dp(56), dp(24), dp(40))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        root.addView(TextView(this).apply {
            text = "NOW PLAYING"
            setTextColor(0xFFFF5C8A.toInt())
            textSize = 11f
            letterSpacing = 0.2f
            setTypeface(null, Typeface.BOLD)
        })

        // Artwork placeholder card
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(260), dp(260)).apply { topMargin = dp(28) }
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF5A2A45.toInt(), 0xFF2A1830.toInt(), 0xFF12101C.toInt())
            ).apply { cornerRadius = dp(28).toFloat() }
            addView(TextView(this@NowPlayingActivity).apply {
                text = "♫"
                setTextColor(0xFFFF8FB0.toInt())
                textSize = 64f
                gravity = Gravity.CENTER
            })
        })

        titleView = TextView(this).apply {
            text = "—"
            setTextColor(0xFFFFF8FC.toInt())
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(28), 0, dp(4))
        }
        root.addView(titleView)

        artistView = TextView(this).apply {
            text = "—"
            setTextColor(0xFFC9B8C8.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
        }
        root.addView(artistView)

        seekBar = SeekBar(this).apply {
            max = 1000
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28) }
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
            setTextColor(0xFF8E7F93.toInt())
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        durationView = TextView(this).apply {
            text = "0:00"
            setTextColor(0xFF8E7F93.toInt())
            textSize = 12f
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        timeRow.addView(positionView)
        timeRow.addView(durationView)
        root.addView(timeRow)

        // Transport controls
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28) }
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

        // Secondary actions
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) }
        }
        actions.addView(chip("Sleep timer") { showSleepTimer() })
        actions.addView(chip("Equalizer") { showEqualizer() })
        actions.addView(chip("Bass boost") { toggleBass() })
        root.addView(actions)

        sleepLabel = TextView(this).apply {
            text = ""
            setTextColor(0xFFFF8FB0.toInt())
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        }
        root.addView(sleepLabel)

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
        val label = when (p.repeatMode) {
            Player.REPEAT_MODE_ALL -> "Repeat all"
            Player.REPEAT_MODE_ONE -> "Repeat one"
            else -> "Repeat off"
        }
        Toast.makeText(this, label, Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this, "Sleep timer off", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(this@NowPlayingActivity, "Playback paused by sleep timer", Toast.LENGTH_LONG).show()
                    }
                }.start()
            }
            .show()
    }

    private fun showEqualizer() {
        try {
            val sessionId = controller?.audioSessionId ?: 0
            if (sessionId == 0) {
                Toast.makeText(this, "Start playback first", Toast.LENGTH_SHORT).show()
                return
            }
            equalizer?.release()
            equalizer = Equalizer(0, sessionId).apply { enabled = true }
            val eq = equalizer ?: return
            val bands = eq.numberOfBands.toInt()
            val presets = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
            AlertDialog.Builder(this)
                .setTitle("Equalizer preset")
                .setItems(presets.toTypedArray()) { _, which ->
                    try {
                        eq.usePreset(which.toShort())
                        Toast.makeText(this, presets[which], Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                        Toast.makeText(this, "Preset not available", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNeutralButton(if (eq.enabled) "Disable" else "Enable") { _, _ ->
                    eq.enabled = !eq.enabled
                }
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Equalizer unavailable on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleBass() {
        try {
            val sessionId = controller?.audioSessionId ?: 0
            if (sessionId == 0) {
                Toast.makeText(this, "Start playback first", Toast.LENGTH_SHORT).show()
                return
            }
            if (bassBoost == null) {
                bassBoost = BassBoost(0, sessionId).apply {
                    enabled = true
                    setStrength(700.toShort())
                }
                Toast.makeText(this, "Bass boost on", Toast.LENGTH_SHORT).show()
            } else {
                bassBoost?.enabled = !(bassBoost?.enabled ?: false)
                Toast.makeText(this, if (bassBoost?.enabled == true) "Bass boost on" else "Bass boost off", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Bass boost unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    private fun controlBtn(label: String, primary: Boolean = false, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(0xFFFFFFFF.toInt())
            textSize = if (primary) 22f else 18f
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = if (primary) GradientDrawable().apply {
                setColor(0xFFFF5C8A.toInt())
                cornerRadius = dp(28).toFloat()
            } else null
            layoutParams = LinearLayout.LayoutParams(if (primary) dp(64) else dp(52), if (primary) dp(64) else dp(52)).apply {
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
            setTextColor(0xFFFFF8FC.toInt())
            textSize = 12f
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                setColor(0xFF16121F.toInt())
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), 0x28FFFFFF)
            }
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun formatMs(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return "%d:%02d".format(m, s)
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
