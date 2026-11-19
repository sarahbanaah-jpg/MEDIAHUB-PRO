package com.mediahub.rebuilt.ui.music

import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.View
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
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.mediahub.rebuilt.media.DefaultAlbumArt
import com.mediahub.rebuilt.media.Favourites
import com.mediahub.rebuilt.media.MediaRepository
import com.mediahub.rebuilt.service.PlaybackService
import com.mediahub.rebuilt.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private var seeking = false

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: PlayerMediaItem?, reason: Int) {
            refreshMetadata(loadArtwork = true)
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) { updatePlayButton() }
        override fun onPlaybackStateChanged(playbackState: Int) { updateProgress() }
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
        window.decorView.systemUiVisibility = 0

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setBackground(GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFFFFA36D.toInt(), 0xFFE97891.toInt(), 0xFF8A4D91.toInt(), 0xFF21152F.toInt(), 0xFF100B20.toInt())
            ))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(44), dp(24), dp(22))
        }
        scroll.addView(root)

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(glassButton("‹", 46) { finish() })
        top.addView(TextView(this).apply {
            text = "NOW PLAYING"
            setTextColor(0xFFFFF5FA.toInt())
            textSize = 15f
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f)
        })
        top.addView(glassButton("•••", 46) { showMore() })
        root.addView(top)

        artView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(300), dp(300)).apply {
                topMargin = dp(24)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(0x33FFFFFF, 28, 0xAAFF8BCB.toInt(), 2)
            clipToOutline = true
            elevation = dp(12).toFloat()
        }
        root.addView(artView)
        artView.setImageBitmap(DefaultAlbumArt.mountainBitmap(700))

        titleView = TextView(this).apply {
            text = "Nothing playing"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 32f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(0, dp(18), 0, dp(3))
        }
        root.addView(titleView)
        artistView = TextView(this).apply {
            text = "Choose a song from your library"
            setTextColor(0xFFFFE4F1.toInt())
            textSize = 16f
            gravity = Gravity.CENTER
        }
        root.addView(artistView)

        // This panel displays real track metadata; no fabricated waveform or lyrics are shown.
        val infoPanel = glassPanel().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) }
        }
        infoPanel.addView(TextView(this).apply {
            text = "TRACK INFORMATION"
            setTextColor(0xFFFFD1E7.toInt())
            textSize = 11f
            letterSpacing = 0.1f
        })
        infoPanel.addView(TextView(this).apply {
            text = "Playing from your device library"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            setPadding(0, dp(7), 0, 0)
        })
        root.addView(infoPanel)

        seekBar = SeekBar(this).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(0xFFFF5DB5.toInt())
            thumbTintList = android.content.res.ColorStateList.valueOf(0xFFFFC4E1.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
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
            text = "0:00"; setTextColor(0xFFFFF0F7.toInt()); textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        durationView = TextView(this).apply {
            text = "0:00"; setTextColor(0xFFFFF0F7.toInt()); textSize = 13f
            gravity = Gravity.END; layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        times.addView(positionView); times.addView(durationView); root.addView(times)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) }
        }
        shuffleButton = controlButton("⇄", 48) { toggleShuffle() }
        controls.addView(shuffleButton)
        controls.addView(controlButton("|◀", 48) { controller?.seekToPreviousMediaItem() })
        playButton = controlButton("▶", 76) { togglePlay() }
        controls.addView(playButton)
        controls.addView(controlButton("▶|", 48) { controller?.seekToNextMediaItem() })
        repeatButton = controlButton("↻", 48) { cycleRepeat() }
        controls.addView(repeatButton)
        root.addView(controls)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        }
        favouriteButton = chip("♡ FAVOURITE") { toggleFavourite() }
        actions.addView(favouriteButton)
        actions.addView(chip("SLEEP TIMER") { showSleepTimer() })
        root.addView(actions)

        sleepLabel = TextView(this).apply {
            text = ""; setTextColor(0xFFFFD0E7.toInt()); textSize = 12f
            gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0)
        }
        root.addView(sleepLabel)

        val nav = glassPanel().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(10), dp(8), dp(10))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) }
        }
        nav.addView(navItem("⌂", "HOME") {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        })
        nav.addView(navItem("♫", "LIBRARY") { finish() })
        nav.addView(navItem("⋯", "MORE") { showMore() })
        root.addView(nav)

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
        durationView.text = formatMs(duration)
        updatePlayButton()
    }

    private fun updatePlayButton() {
        playButton.text = if (controller?.isPlaying == true) "Ⅱ" else "▶"
    }

    private fun updateModes() {
        shuffleButton.alpha = if (controller?.shuffleModeEnabled == true) 1f else 0.48f
        repeatButton.alpha = if (controller?.repeatMode != Player.REPEAT_MODE_OFF) 1f else 0.48f
    }

    private fun updateFavourite() {
        val active = currentTrackId > 0 && Favourites.isFavourite(this, currentTrackId)
        favouriteButton.text = if (active) "♥ FAVOURITED" else "♡ FAVOURITE"
        favouriteButton.alpha = if (active) 1f else 0.82f
    }

    private fun toggleFavourite() {
        if (currentTrackId <= 0L) {
            Toast.makeText(this, "Select a track from your library first", Toast.LENGTH_SHORT).show()
            return
        }
        Favourites.toggle(this, currentTrackId)
        updateFavourite()
    }

    private fun togglePlay() {
        val p = controller ?: return
        if (p.isPlaying) p.pause() else p.play()
        updatePlayButton()
    }

    private fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled; updateModes() }
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

    private fun showMore() {
        AlertDialog.Builder(this).setTitle("Playback options")
            .setItems(arrayOf("Sleep timer", "Equalizer", "Bass boost")) { _, which ->
                when (which) { 0 -> showSleepTimer(); 1 -> openAudioEffects("Equalizer"); 2 -> openAudioEffects("Bass boost") }
            }.show()
    }

    private fun openAudioEffects(label: String) {
        try {
            startActivity(Intent("android.media.action.DISPLAY_AUDIO_EFFECT_CONTROL_PANEL"))
        } catch (_: Exception) {
            Toast.makeText(this, "$label is not available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSleepTimer() {
        val options = arrayOf("Off", "15 minutes", "30 minutes", "45 minutes", "60 minutes")
        AlertDialog.Builder(this).setTitle("Sleep timer").setItems(options) { _, which ->
            sleepTimer?.cancel(); sleepTimer = null
            if (which == 0) { sleepLabel.text = ""; return@setItems }
            val minutes = listOf(0, 15, 30, 45, 60)[which]
            sleepLabel.text = "Sleep in $minutes min"
            sleepTimer = object : CountDownTimer(minutes * 60_000L, 30_000L) {
                override fun onTick(ms: Long) { sleepLabel.text = "Sleep in ${(ms / 60_000L) + 1} min" }
                override fun onFinish() { controller?.pause(); sleepLabel.text = "Sleep timer ended" }
            }.start()
        }.show()
    }

    private fun glassPanel() = LinearLayout(this).apply {
        background = rounded(0x55FFFFFF, 22, 0x66FFFFFF, 1)
        elevation = dp(5).toFloat()
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int = 0, strokeWidth: Int = 0) =
        GradientDrawable().apply {
            setColor(fill); cornerRadius = dp(radius).toFloat()
            if (strokeWidth > 0) setStroke(dp(strokeWidth), stroke)
        }

    private fun glassButton(label: String, size: Int, click: () -> Unit) = TextView(this).apply {
        text = label; gravity = Gravity.CENTER; textSize = 23f; setTextColor(0xFFFFFFFF.toInt())
        background = rounded(0x55FFFFFF, 18, 0x55FFFFFF, 1)
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        setOnClickListener { click() }
    }

    private fun controlButton(label: String, size: Int, click: () -> Unit) = TextView(this).apply {
        text = label; gravity = Gravity.CENTER; textSize = if (size > 60) 26f else 19f
        setTextColor(0xFFFFFFFF.toInt())
        background = if (size > 60) GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFFFF3F9F.toInt(), 0xFFFF956F.toInt())).apply { shape = GradientDrawable.OVAL }
            else rounded(0x55FFFFFF, 50, 0x55FFFFFF, 1)
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)).apply {
            marginStart = dp(3); marginEnd = dp(3)
        }
        setOnClickListener { click() }
    }

    private fun chip(label: String, click: () -> Unit) = TextView(this).apply {
        text = label; gravity = Gravity.CENTER; textSize = 11f
        setTextColor(0xFFFFFFFF.toInt()); setPadding(dp(14), dp(12), dp(14), dp(12))
        background = rounded(0x55FFFFFF, 20, 0x66FFFFFF, 1)
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(5); marginEnd = dp(5) }
        setOnClickListener { click() }
    }

    private fun navItem(icon: String, label: String, click: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        addView(TextView(this@NowPlayingActivity).apply {
            text = icon; textSize = 22f; gravity = Gravity.CENTER; setTextColor(0xFFFFFFFF.toInt())
        })
        addView(TextView(this@NowPlayingActivity).apply {
            text = label; textSize = 10f; gravity = Gravity.CENTER; setTextColor(0xFFFFF0F7.toInt())
        })
        setOnClickListener { click() }
    }

    private fun formatMs(ms: Long): String {
        if (ms < 0L || ms == androidx.media3.common.C.TIME_UNSET) return "0:00"
        val seconds = ms / 1000L
        return "%d:%02d".format(seconds / 60L, seconds % 60L)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        window.decorView.removeCallbacks(tickRunnable)
        sleepTimer?.cancel()
        controller?.removeListener(playerListener)
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}
