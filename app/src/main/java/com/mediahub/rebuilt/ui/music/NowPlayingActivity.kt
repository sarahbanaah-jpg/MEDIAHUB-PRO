package com.mediahub.rebuilt.ui.music

import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.graphics.Outline
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.mediahub.rebuilt.media.DefaultAlbumArt
import com.mediahub.rebuilt.media.Favourites
import com.mediahub.rebuilt.media.MediaRepository
import com.mediahub.rebuilt.service.PlaybackService
import com.mediahub.rebuilt.service.AudioEqualizerController
import com.mediahub.rebuilt.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Aurora Now Playing — circular art, cyan glow, waveform (mockup match). */
class NowPlayingActivity : AppCompatActivity() {
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var sleepTimer: CountDownTimer? = null
    private var currentTrackId: Long = -1L
    private lateinit var artView: ImageView
    private lateinit var titleView: TextView
    private lateinit var artistView: TextView
    private lateinit var positionView: TextView
    private lateinit var durationView: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var playButton: TextView
    private lateinit var shuffleButton: TextView
    private lateinit var repeatButton: TextView
    private lateinit var favouriteButton: TextView
    private lateinit var sleepLabel: TextView
    private lateinit var waveView: AuroraWaveView
    private var seeking = false

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: PlayerMediaItem?, reason: Int) {
            refreshMetadata(loadArtwork = true)
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayButton()
            if (::waveView.isInitialized) waveView.setPlaying(isPlaying)
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            updateProgress()
            if (playbackState == Player.STATE_BUFFERING) {
                artistView.text = "Connecting to audio stream…"
            } else if (playbackState == Player.STATE_READY && controller?.isPlaying == true) {
                refreshMetadata(loadArtwork = false)
            }
        }
        override fun onPlayerError(error: PlaybackException) {
            val detail = error.cause?.localizedMessage ?: error.localizedMessage ?: "Audio stream could not be decoded."
            artistView.text = "Playback failed: $detail"
            Toast.makeText(this@NowPlayingActivity, "Epidemic Sound playback failed: $detail", Toast.LENGTH_LONG).show()
        }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { updateModes() }
        override fun onRepeatModeChanged(repeatMode: Int) { updateModes() }
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            window.decorView.postDelayed(this, 400)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = 0x00000000
        window.navigationBarColor = 0x00000000

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF1A0B3C.toInt(), 0xFF0B0520.toInt(), 0xFF14083A.toInt())
            )
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(44), dp(22), dp(28))
        }
        scroll.addView(root)

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(circleBtn("‹") { finish() })
        top.addView(TextView(this).apply {
            text = "Now Playing"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        top.addView(circleBtn("⋯") { showMore() })
        root.addView(top)

        val glowHost = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(300), dp(300)).apply { topMargin = dp(28) }
        }
        fun ring(size: Int, color: Int, stroke: Int): ImageView {
            return ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(dp(size), dp(size), Gravity.CENTER)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0x00000000)
                    setStroke(dp(stroke), color)
                }
            }
        }
        glowHost.addView(ring(300, 0x335CE1FF.toInt(), 2))
        glowHost.addView(ring(270, 0x555CE1FF.toInt(), 3))
        glowHost.addView(ring(242, 0x88A78BFF.toInt(), 2))
        artView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(210), dp(210), Gravity.CENTER)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF221247.toInt())
            }
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
        }
        glowHost.addView(artView)
        root.addView(glowHost)
        artView.setImageBitmap(DefaultAlbumArt.mountainBitmap(700))

        titleView = TextView(this).apply {
            text = "Nothing playing"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(0, dp(20), 0, dp(4))
        }
        root.addView(titleView)
        artistView = TextView(this).apply {
            text = "Choose a song from your library"
            setTextColor(0xFF5CE1FF.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
        }
        root.addView(artistView)

        waveView = AuroraWaveView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(16) }
        }
        root.addView(waveView)

        seekBar = SeekBar(this).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(0xFF5CE1FF.toInt())
            thumbTintList = android.content.res.ColorStateList.valueOf(0xFFA78BFF.toInt())
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0xFF3A2A6E.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {}
                override fun onStartTrackingTouch(sb: SeekBar?) { seeking = true }
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    val p = controller ?: return
                    val dur = p.duration
                    if (dur > 0) p.seekTo((dur * (sb?.progress ?: 0)) / 1000L)
                    seeking = false
                }
            })
        }
        root.addView(seekBar)

        val times = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        positionView = TextView(this).apply {
            text = "0:00"; setTextColor(0xFF9B8FBE.toInt()); textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        durationView = TextView(this).apply {
            text = "0:00"; setTextColor(0xFF9B8FBE.toInt()); textSize = 12f
            gravity = Gravity.END; layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        times.addView(positionView); times.addView(durationView); root.addView(times)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(12), dp(10), dp(12))
            background = GradientDrawable().apply {
                setColor(0xCC1A0F3A.toInt())
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), 0x555CE1FF.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) }
        }
        shuffleButton = controlIcon("⇄") { toggleShuffle() }
        controls.addView(shuffleButton)
        controls.addView(controlIcon("⏮") { controller?.seekToPreviousMediaItem() })
        playButton = TextView(this).apply {
            text = "▶"
            gravity = Gravity.CENTER
            textSize = 22f
            setTextColor(0xFF0B0520.toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                colors = intArrayOf(0xFF5CE1FF.toInt(), 0xFFA78BFF.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64)).apply {
                marginStart = dp(10); marginEnd = dp(10)
            }
            elevation = dp(10).toFloat()
            setOnClickListener { togglePlay() }
        }
        controls.addView(playButton)
        controls.addView(controlIcon("⏭") { controller?.seekToNextMediaItem() })
        repeatButton = controlIcon("↻") { cycleRepeat() }
        controls.addView(repeatButton)
        root.addView(controls)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) }
        }
        favouriteButton = chip("♡ Favourite") { toggleFavourite() }
        actions.addView(favouriteButton)
        actions.addView(chip("Sleep timer") { showSleepTimer() })
        root.addView(actions)
        sleepLabel = TextView(this).apply {
            text = ""; setTextColor(0xFF5CE1FF.toInt()); textSize = 12f
            gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0)
        }
        root.addView(sleepLabel)

        root.addView(TextView(this).apply {
            text = "© Grimy · MediaHub Pro"
            setTextColor(0xFF6B5F96.toInt())
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, 0)
        })

        setContentView(scroll)
        connectController()
    }

    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                controller = future.get()
                controller?.addListener(playerListener)
                controller?.repeatMode = Player.REPEAT_MODE_ALL
                refreshMetadata(loadArtwork = true)
                updateProgress()
                window.decorView.post(tickRunnable)
            } catch (_: Exception) {
                Toast.makeText(this, "Player unavailable", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun refreshMetadata(loadArtwork: Boolean) {
        val p = controller ?: return
        val item = p.currentMediaItem
        val meta = item?.mediaMetadata
        titleView.text = meta?.title?.toString()?.takeIf { it.isNotBlank() } ?: "Nothing playing"
        artistView.text = listOfNotNull(meta?.artist?.toString(), meta?.albumTitle?.toString())
            .filter { it.isNotBlank() }.distinct().joinToString(" · ")
            .ifBlank { "Local music library" }
        currentTrackId = item?.mediaId?.toLongOrNull() ?: -1L
        updatePlayButton()
        updateModes()
        updateFavourite()
        if (loadArtwork) loadCoverForCurrent(item?.localConfiguration?.uri?.toString(), titleView.text.toString())
    }

    private fun loadCoverForCurrent(uri: String?, title: String) {
        lifecycleScope.launch {
            val songs = withContext(Dispatchers.IO) { MediaRepository.loadMusic(this@NowPlayingActivity) }
            val match = songs.firstOrNull { it.uri.toString() == uri }
                ?: songs.firstOrNull { it.title.equals(title, true) }
            val bitmap = withContext(Dispatchers.IO) {
                if (match != null) DefaultAlbumArt.forTrack(this@NowPlayingActivity, match, 800)
                else DefaultAlbumArt.mountainBitmap(800)
            }
            if (!isFinishing) artView.setImageBitmap(bitmap)
        }
    }

    private fun updateProgress() {
        val p = controller ?: return
        val duration = p.duration
        val position = p.currentPosition.coerceAtLeast(0L)
        if (!seeking && duration > 0) seekBar.progress = ((position * 1000L) / duration).toInt().coerceIn(0, 1000)
        positionView.text = formatMs(position)
        durationView.text = if (duration > 0) formatMs(duration) else "0:00"
    }

    private fun updatePlayButton() {
        val playing = controller?.isPlaying == true
        playButton.text = if (playing) "❚❚" else "▶"
        if (::waveView.isInitialized) waveView.setPlaying(playing)
    }

    private fun updateModes() {
        val p = controller ?: return
        shuffleButton.alpha = if (p.shuffleModeEnabled) 1f else 0.45f
        repeatButton.alpha = if (p.repeatMode == Player.REPEAT_MODE_OFF) 0.45f else 1f
    }

    private fun updateFavourite() {
        val fav = currentTrackId > 0 && Favourites.isFavourite(this, currentTrackId)
        favouriteButton.text = if (fav) "♥ Favourited" else "♡ Favourite"
    }

    private fun togglePlay() {
        val p = controller ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    private fun toggleShuffle() {
        val p = controller ?: return
        p.shuffleModeEnabled = !p.shuffleModeEnabled
        updateModes()
    }

    private fun cycleRepeat() {
        val p = controller ?: return
        p.repeatMode = when (p.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        updateModes()
    }

    private fun toggleFavourite() {
        if (currentTrackId <= 0) {
            Toast.makeText(this, "Play a track first", Toast.LENGTH_SHORT).show()
            return
        }
        Favourites.toggle(this, currentTrackId)
        updateFavourite()
    }

    private fun showSleepTimer() {
        val options = arrayOf("Off", "15 min", "30 min", "45 min", "60 min")
        AlertDialog.Builder(this)
            .setTitle("Sleep timer")
            .setItems(options) { _, which ->
                sleepTimer?.cancel()
                if (which == 0) {
                    sleepLabel.text = ""
                    return@setItems
                }
                val mins = listOf(0, 15, 30, 45, 60)[which]
                sleepLabel.text = "Sleep in ${mins}m"
                sleepTimer = object : CountDownTimer(mins * 60_000L, 30_000L) {
                    override fun onTick(ms: Long) {
                        sleepLabel.text = "Sleep in ${(ms / 60_000L) + 1}m"
                    }
                    override fun onFinish() {
                        controller?.pause()
                        sleepLabel.text = "Sleep timer ended"
                    }
                }.start()
            }.show()
    }

    private fun showMore() {
        AlertDialog.Builder(this)
            .setTitle("Now Playing")
            .setItems(arrayOf("In-app equalizer", "Open library", "Home")) { _, which ->
                when (which) {
                    0 -> showEqualizer()
                    1 -> finish()
                    2 -> startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                }
            }.show()
    }

    private fun showEqualizer() {
        val eq = AudioEqualizerController.current()
        if (eq == null) {
            Toast.makeText(this, "Equalizer is not available for this audio output.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val range = eq.bandLevelRange
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(12), dp(20), dp(8))
            }
            for (i in 0 until eq.numberOfBands.toInt()) {
                val band = i.toShort()
                val frequency = eq.getCenterFreq(band) / 1000f
                panel.addView(TextView(this).apply {
                    text = "${frequency.toInt()} Hz"
                    setTextColor(0xFFFFFFFF.toInt()); textSize = 12f
                })
                panel.addView(SeekBar(this).apply {
                    max = (range[1] - range[0]).toInt()
                    progress = (eq.getBandLevel(band) - range[0]).toInt().coerceIn(0, max)
                    setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                            if (fromUser) try { eq.setBandLevel(band, (range[0] + progress).toShort()) } catch (_: Exception) { }
                        }
                        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                    })
                })
            }
            AlertDialog.Builder(this).setTitle("MEDIAHUB-PRO Equalizer")
                .setView(ScrollView(this).apply { addView(panel) })
                .setNeutralButton("Presets") { _, _ -> showEqualizerPresets() }
                .setPositiveButton("Done", null).show()
        } catch (e: Exception) {
            Toast.makeText(this, "This device does not expose equalizer controls.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showEqualizerPresets() {
        val eq = AudioEqualizerController.current() ?: return
        try {
            val names = (0 until eq.numberOfPresets.toInt()).map { eq.getPresetName(it.toShort()) }.toTypedArray()
            if (names.isEmpty()) return
            AlertDialog.Builder(this).setTitle("Equalizer presets")
                .setItems(names) { _, which ->
                    try { eq.usePreset(which.toShort()) } catch (_: Exception) {
                        Toast.makeText(this, "Preset unavailable on this device.", Toast.LENGTH_SHORT).show()
                    }
                }.show()
        } catch (_: Exception) {
            Toast.makeText(this, "Presets unavailable on this device.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatMs(ms: Long): String {
        val total = (ms / 1000L).toInt().coerceAtLeast(0)
        val m = total / 60
        val s = total % 60
        return "%d:%02d".format(m, s)
    }

    private fun circleBtn(t: String, click: () -> Unit) = TextView(this).apply {
        text = t; gravity = Gravity.CENTER; textSize = 20f; setTextColor(0xFFFFFFFF.toInt())
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x33221247.toInt())
            setStroke(dp(1), 0x555CE1FF.toInt())
        }
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        setOnClickListener { click() }
    }

    private fun controlIcon(t: String, click: () -> Unit) = TextView(this).apply {
        text = t; gravity = Gravity.CENTER; textSize = 18f; setTextColor(0xFFEDE7FF.toInt())
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        setOnClickListener { click() }
    }

    private fun chip(t: String, click: () -> Unit) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(0xFFFFFFFF.toInt())
        setPadding(dp(16), dp(10), dp(16), dp(10))
        background = GradientDrawable().apply {
            setColor(0xFF221247.toInt()); cornerRadius = dp(20).toFloat(); setStroke(dp(1), 0x555CE1FF.toInt())
        }
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) }
        setOnClickListener { click() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        sleepTimer?.cancel()
        window.decorView.removeCallbacks(tickRunnable)
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}
