package com.mediahub.rebuilt.ui.player

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

class VideoPlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        val playerView = PlayerView(this).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            useController = true
            keepScreenOn = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        }
        setContentView(playerView)
        val mediaUri: Uri? = intent.data ?: intent.getStringExtra(EXTRA_MEDIA_URI)?.let(Uri::parse)
        if (mediaUri == null) {
            Toast.makeText(this, "This video cannot be opened.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Toast.makeText(this@VideoPlayerActivity, "Unable to play this video format: ${error.errorCodeName}", Toast.LENGTH_LONG).show()
                }
            })
            exo.setMediaItem(MediaItem.Builder().setUri(mediaUri).setMediaMetadata(
                MediaMetadata.Builder().setTitle(title).build()
            ).build())
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    override fun onStop() {
        player?.release()
        player = null
        super.onStop()
    }

    companion object {
        const val EXTRA_MEDIA_URI = "com.mediahub.rebuilt.extra.MEDIA_URI"
        const val EXTRA_TITLE = "com.mediahub.rebuilt.extra.MEDIA_TITLE"
    }
}
