package com.mediahub.rebuilt.ui.player

import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.mediahub.rebuilt.diagnostics.Diagnostics
import kotlin.math.abs

class VideoPlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private var mediaKey: String = ""
    private lateinit var overlayHint: TextView
    private lateinit var audioManager: AudioManager
    private var maxVolume = 15

    private val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

        val root = FrameLayout(this)
        val playerView = PlayerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            useController = true
            keepScreenOn = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            controllerShowTimeoutMs = 3500
            setShowNextButton(false)
            setShowPreviousButton(false)
        }
        root.addView(playerView)

        overlayHint = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
            setPadding(dp(18), dp(12), dp(18), dp(12))
            setBackgroundColor(0x99000000.toInt())
            visibility = android.view.View.GONE
            layoutParams = FrameLayout.LayoutParams(-2, -2).apply {
                gravity = android.view.Gravity.CENTER
            }
        }
        root.addView(overlayHint)
        setContentView(root)

        val mediaUri: Uri? = intent.data ?: intent.getStringExtra(EXTRA_MEDIA_URI)?.let(Uri::parse)
        if (mediaUri == null) {
            Toast.makeText(this, "This video cannot be opened.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        mediaKey = mediaUri.toString()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val prefs = getSharedPreferences("mediahub_playback", MODE_PRIVATE)
        val resumeAt = prefs.getLong("pos_$mediaKey", 0L)

        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Diagnostics.recordIssue(this@VideoPlayerActivity, "VideoPlayerActivity", error)
                    Toast.makeText(
                        this@VideoPlayerActivity,
                        "Unable to play this video: ${error.errorCodeName}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
            exo.setMediaItem(
                MediaItem.Builder()
                    .setUri(mediaUri)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                    .build()
            )
            exo.prepare()
            if (resumeAt > 0L) exo.seekTo(resumeAt)
            exo.playWhenReady = true
        }

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        setupGestures(playerView)

        // Long-press controller area alternative: speed menu via double-tap center is gesture; also menu on long click
        playerView.setOnLongClickListener {
            showSpeedMenu()
            true
        }
    }

    private fun setupGestures(playerView: PlayerView) {
        var startX = 0f
        var startY = 0f
        var startBrightness = 0.5f
        var startVolume = 0
        var mode = 0 // 0 none, 1 brightness, 2 volume, 3 seek

        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val p = player ?: return false
                val w = playerView.width.coerceAtLeast(1)
                if (e.x < w / 3f) {
                    p.seekTo((p.currentPosition - 10_000).coerceAtLeast(0))
                    flashHint("-10s")
                } else if (e.x > w * 2f / 3f) {
                    p.seekTo(p.currentPosition + 10_000)
                    flashHint("+10s")
                } else {
                    if (p.isPlaying) p.pause() else p.play()
                    flashHint(if (p.isPlaying) "Play" else "Pause")
                }
                return true
            }
        })

        playerView.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    startBrightness = window.attributes.screenBrightness.let {
                        if (it < 0f) 0.5f else it
                    }
                    startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    mode = 0
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - startX
                    val dy = event.y - startY
                    if (mode == 0 && abs(dx) + abs(dy) > 24) {
                        mode = when {
                            abs(dy) > abs(dx) && startX < playerView.width / 2f -> 1 // brightness left
                            abs(dy) > abs(dx) -> 2 // volume right
                            else -> 3 // seek horizontal
                        }
                    }
                    when (mode) {
                        1 -> {
                            val delta = -dy / playerView.height
                            val value = (startBrightness + delta).coerceIn(0.05f, 1f)
                            val lp = window.attributes
                            lp.screenBrightness = value
                            window.attributes = lp
                            flashHint("Brightness ${(value * 100).toInt()}%")
                        }
                        2 -> {
                            val delta = -dy / playerView.height
                            val value = (startVolume + delta * maxVolume).toInt().coerceIn(0, maxVolume)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
                            flashHint("Volume ${(value * 100) / maxVolume}%")
                        }
                        3 -> {
                            val p = player ?: return@setOnTouchListener true
                            val seekDelta = (dx / playerView.width) * 60_000L
                            val target = (p.currentPosition + seekDelta).coerceAtLeast(0)
                            flashHint(formatMs(target.toLong()))
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (mode == 3) {
                        val p = player
                        if (p != null) {
                            val seekDelta = ((event.x - startX) / playerView.width) * 60_000L
                            p.seekTo((p.currentPosition + seekDelta).toLong().coerceAtLeast(0))
                        }
                    }
                    mode = 0
                }
            }
            true
        }
    }

    private fun showSpeedMenu() {
        val labels = speeds.map { "${it}x" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Playback speed")
            .setItems(labels) { _, which ->
                player?.setPlaybackSpeed(speeds[which])
                flashHint("${speeds[which]}x")
            }
            .setNeutralButton("Subtitles") { _, _ -> showSubtitleTracks() }
            .show()
    }

    private fun showSubtitleTracks() {
        val p = player ?: return
        val groups = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        if (groups.isEmpty()) {
            Toast.makeText(this, "No subtitle tracks found", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = mutableListOf("Off")
        val actions = mutableListOf<() -> Unit>()
        actions += {
            p.trackSelectionParameters = p.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        }
        groups.forEach { group ->
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val name = format.label ?: format.language ?: "Track ${i + 1}"
                labels += name
                val mediaTrackGroup = group.mediaTrackGroup
                actions += {
                    p.trackSelectionParameters = p.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(TrackSelectionOverride(mediaTrackGroup, listOf(i)))
                        .build()
                }
            }
        }
        AlertDialog.Builder(this)
            .setTitle("Subtitles")
            .setItems(labels.toTypedArray()) { _, which -> actions[which].invoke() }
            .show()
    }

    private fun flashHint(text: String) {
        overlayHint.text = text
        overlayHint.visibility = android.view.View.VISIBLE
        overlayHint.removeCallbacks(hideHint)
        overlayHint.postDelayed(hideHint, 900)
    }

    private val hideHint = Runnable { overlayHint.visibility = android.view.View.GONE }

    private fun formatMs(ms: Long): String {
        val total = ms / 1000
        return "%d:%02d".format(total / 60, total % 60)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            try { enterPictureInPictureMode() } catch (_: Exception) {}
        }
    }

    override fun onStop() {
        player?.let { exo ->
            if (mediaKey.isNotEmpty()) {
                getSharedPreferences("mediahub_playback", MODE_PRIVATE)
                    .edit()
                    .putLong("pos_$mediaKey", exo.currentPosition)
                    .apply()
            }
            if (!isInPictureInPictureMode) {
                exo.release()
                player = null
            }
        }
        super.onStop()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MEDIA_URI = "com.mediahub.rebuilt.extra.MEDIA_URI"
        const val EXTRA_TITLE = "com.mediahub.rebuilt.extra.MEDIA_TITLE"
    }
}
