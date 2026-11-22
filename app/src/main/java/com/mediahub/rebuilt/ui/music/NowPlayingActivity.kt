package com.mediahub.rebuilt.ui.music

import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
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
import com.mediahub.rebuilt.media.MediaItem
import com.mediahub.rebuilt.media.MediaRepository
import com.mediahub.rebuilt.service.PlaybackService
import com.mediahub.rebuilt.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Premium Now Playing — midnight glass frame, coral accents and real library queue.
 * When the current track ends, Media3 advances to the next item in the queue automatically.
 */
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
    private lateinit var trendingRow: LinearLayout
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

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(0xFF0A0A0A.toInt())
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(40), dp(20), dp(28))
        }
        scroll.addView(root)

        // Top bar
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(goldCircleBtn("‹") { finish() })
        top.addView(TextView(this).apply {
            text = "NOW PLAYING"
            setTextColor(0xFFFF6B9F.toInt())
            textSize = 14f
            letterSpacing = 0.18f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        top.addView(ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            setImageBitmap(DefaultAlbumArt.mountainBitmap(80))
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF2A2A2A.toInt())
            }
            clipToOutline = true
        })
        root.addView(top)

        // Premium glass frame around album art
        val frame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = GradientDrawable().apply {
                setColor(0xFF221522.toInt())
                cornerRadius = dp(18).toFloat()
                setStroke(dp(3), 0xFFFF6B9F.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(dp(300), dp(300)).apply {
                topMargin = dp(28)
            }
            elevation = dp(16).toFloat()
        }
        artView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(280), dp(280))
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                setColor(0xFF111111.toInt())
                cornerRadius = dp(12).toFloat()
            }
            clipToOutline = true
        }
        frame.addView(artView)
        root.addView(frame)
        artView.setImageBitmap(DefaultAlbumArt.mountainBitmap(700))

        titleView = TextView(this).apply {
            text = "Nothing playing"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(0, dp(22), 0, dp(4))
        }
        root.addView(titleView)
        artistView = TextView(this).apply {
            text = "Choose a song from your library"
            setTextColor(0xFFFF6B9F.toInt())
            textSize = 15f
            gravity = Gravity.CENTER
        }
        root.addView(artistView)

        // Progress
        seekBar = SeekBar(this).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(0xFFFF6B9F.toInt())
            thumbTintList = android.content.res.ColorStateList.valueOf(0xFFFF2D95.toInt())
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0xFF3A3A3A.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22) }
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
            text = "0:00"; setTextColor(0xFFB0B0B0.toInt()); textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        durationView = TextView(this).apply {
            text = "0:00"; setTextColor(0xFFB0B0B0.toInt()); textSize = 12f
            gravity = Gravity.END; layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        times.addView(positionView); times.addView(durationView); root.addView(times)

        // Glass control bar with coral play
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(0xEE1A1A1A.toInt())
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), 0x44FF6B9F.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) }
            elevation = dp(8).toFloat()
        }
        shuffleButton = controlIcon("⇄") { toggleShuffle() }
        controls.addView(shuffleButton)
        controls.addView(controlIcon("⏮") { controller?.seekToPreviousMediaItem() })
        playButton = TextView(this).apply {
            text = "▶"
            gravity = Gravity.CENTER
            textSize = 22f
            setTextColor(0xFF180A12.toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                colors = intArrayOf(0xFFFFA0BE.toInt(), 0xFFE83E87.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(dp(58), dp(58)).apply {
                marginStart = dp(10); marginEnd = dp(10)
            }
            elevation = dp(6).toFloat()
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
            text = ""; setTextColor(0xFFFF6B9F.toInt()); textSize = 12f
            gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0)
        }
        root.addView(sleepLabel)

        // From your library (real tracks)
        val trendHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(24), 0, dp(12))
        }
        trendHeader.addView(TextView(this).apply {
            text = "FROM YOUR LIBRARY"
            setTextColor(0xFFFF6B9F.toInt())
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.08f
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        trendHeader.addView(TextView(this).apply {
            text = "See all ›"
            setTextColor(0xFFFF2D95.toInt())
            textSize = 13f
            setOnClickListener { finish() }
        })
        root.addView(trendHeader)

        trendingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        root.addView(trendingRow)

        // Genre chips (labels only — library is local)
        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(16), 0, 0)
        }
        listOf("All music" to true, "Favourites" to false, "Recent" to false).forEach { (label, active) ->
            chips.addView(TextView(this).apply {
                text = label
                setTextColor(if (active) 0xFF180A12.toInt() else 0xFFFF2D95.toInt())
                textSize = 12f
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = GradientDrawable().apply {
                    setColor(if (active) 0xFFFF6B9F.toInt() else 0xFF1A1A1A.toInt())
                    cornerRadius = dp(20).toFloat()
                    setStroke(dp(1), 0xFFFF2D95.toInt())
                }
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) }
                setOnClickListener { finish() }
            })
        }
        root.addView(chips)

        // Bottom nav glass
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(12))
            background = GradientDrawable().apply {
                setColor(0xEE141414.toInt())
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), 0x44FF6B9F.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22) }
        }
        nav.addView(navItem("⌂", "Home") {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        })
        nav.addView(navItem("♫", "Library") { finish() })
        nav.addView(navItem("♥", "Fav") {
            if (currentTrackId > 0) toggleFavourite()
            else Toast.makeText(this, "Play a track first", Toast.LENGTH_SHORT).show()
        })
        nav.addView(navItem("⋯", "More") { showMore() })
        root.addView(nav)

        root.addView(TextView(this).apply {
            text = "© Grimy. All rights reserved."
            setTextColor(0xFF666666.toInt())
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        })

        setContentView(scroll)
        connectController()
        loadTrending()
    }

    private fun loadTrending() {
        lifecycleScope.launch {
            val songs = withContext(Dispatchers.IO) {
                MediaRepository.loadMusic(this@NowPlayingActivity).shuffled().take(3)
            }
            trendingRow.removeAllViews()
            if (songs.isEmpty()) {
                trendingRow.addView(TextView(this@NowPlayingActivity).apply {
                    text = "Your local tracks will appear here"
                    setTextColor(0xFF888888.toInt())
                    textSize = 13f
                })
                return@launch
            }
            songs.forEach { song ->
                trendingRow.addView(trendCard(song))
            }
        }
    }

    private fun trendCard(song: MediaItem): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                setColor(0xFF161616.toInt())
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), 0x33E8C872.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = dp(8)
            }
            setOnClickListener {
                // Jump to this track if it is in the current queue by title match via next/prev is limited;
                // open library for full control
                finish()
            }
        }
        val thumb = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(72))
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                setColor(0xFF222222.toInt())
                cornerRadius = dp(10).toFloat()
            }
            clipToOutline = true
        }
        card.addView(thumb)
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                DefaultAlbumArt.forTrack(this@NowPlayingActivity, song, 160)
            }
            thumb.setImageBitmap(bmp)
        }
        card.addView(TextView(this).apply {
            text = song.title
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 11f
            maxLines = 1
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(6), 0, 0)
        })
        card.addView(TextView(this).apply {
            text = song.artist.ifBlank { "Artist" }
            setTextColor(0xFFFF2D95.toInt())
            textSize = 10f
            maxLines = 1
        })
        return card
    }

    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                controller = future.get()
                controller?.addListener(playerListener)
                // Keep continuous queue play
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
        durationView.text = formatMs(duration)
        updatePlayButton()
    }

    private fun updatePlayButton() {
        playButton.text = if (controller?.isPlaying == true) "Ⅱ" else "▶"
    }

    private fun updateModes() {
        shuffleButton.alpha = if (controller?.shuffleModeEnabled == true) 1f else 0.45f
        repeatButton.alpha = if (controller?.repeatMode != Player.REPEAT_MODE_OFF) 1f else 0.45f
    }

    private fun updateFavourite() {
        val active = currentTrackId > 0 && Favourites.isFavourite(this, currentTrackId)
        favouriteButton.text = if (active) "♥ Favourited" else "♡ Favourite"
    }

    private fun toggleFavourite() {
        if (currentTrackId <= 0L) {
            Toast.makeText(this, "Select a track first", Toast.LENGTH_SHORT).show()
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
        AlertDialog.Builder(this).setTitle("Playback")
            .setItems(arrayOf("Sleep timer", "Equalizer", "Bass boost")) { _, which ->
                when (which) {
                    0 -> showSleepTimer()
                    1 -> openAudioEffects("Equalizer")
                    2 -> openAudioEffects("Bass boost")
                }
            }.show()
    }

    private fun openAudioEffects(label: String) {
        try {
            startActivity(Intent("android.media.action.DISPLAY_AUDIO_EFFECT_CONTROL_PANEL"))
        } catch (_: Exception) {
            Toast.makeText(this, "$label not available on this device", Toast.LENGTH_SHORT).show()
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

    private fun goldCircleBtn(label: String, click: () -> Unit) = TextView(this).apply {
        text = label; gravity = Gravity.CENTER; textSize = 22f; setTextColor(0xFFFF6B9F.toInt())
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFF1A1A1A.toInt())
            setStroke(dp(1), 0x66E8C872.toInt())
        }
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        setOnClickListener { click() }
    }

    private fun controlIcon(label: String, click: () -> Unit) = TextView(this).apply {
        text = label; gravity = Gravity.CENTER; textSize = 18f; setTextColor(0xFFFFFFFF.toInt())
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        setOnClickListener { click() }
    }

    private fun chip(label: String, click: () -> Unit) = TextView(this).apply {
        text = label; gravity = Gravity.CENTER; textSize = 12f
        setTextColor(0xFFFFFFFF.toInt()); setPadding(dp(14), dp(10), dp(14), dp(10))
        background = GradientDrawable().apply {
            setColor(0xFF1A1A1A.toInt()); cornerRadius = dp(18).toFloat()
            setStroke(dp(1), 0x44FF6B9F.toInt())
        }
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(4); marginEnd = dp(4) }
        setOnClickListener { click() }
    }

    private fun navItem(icon: String, label: String, click: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        addView(TextView(this@NowPlayingActivity).apply {
            text = icon; textSize = 18f; gravity = Gravity.CENTER; setTextColor(0xFFFF6B9F.toInt())
        })
        addView(TextView(this@NowPlayingActivity).apply {
            text = label; textSize = 10f; gravity = Gravity.CENTER; setTextColor(0xFFAAAAAA.toInt())
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
