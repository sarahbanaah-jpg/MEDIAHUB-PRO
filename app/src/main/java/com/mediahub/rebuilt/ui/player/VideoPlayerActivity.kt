package com.mediahub.rebuilt.ui.player

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.mediahub.rebuilt.diagnostics.Diagnostics

class VideoPlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private var mediaKey: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK

        val playerView = PlayerView(this).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            useController = true
            keepScreenOn = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            controllerShowTimeoutMs = 3500
            setShowNextButton(false)
            setShowPreviousButton(false)
        }
        setContentView(playerView)

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
            if (resumeAt > 0L) {
                exo.seekTo(resumeAt)
            }
            exo.playWhenReady = true
        }

        // Allow orientation changes freely for landscape/portrait video
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Picture-in-Picture when user leaves (Android 8+)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            try {
                enterPictureInPictureMode()
            } catch (_: Exception) {
            }
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
            exo.release()
        }
        player = null
        super.onStop()
    }

    companion object {
        const val EXTRA_MEDIA_URI = "com.mediahub.rebuilt.extra.MEDIA_URI"
        const val EXTRA_TITLE = "com.mediahub.rebuilt.extra.MEDIA_TITLE"
    }
}
