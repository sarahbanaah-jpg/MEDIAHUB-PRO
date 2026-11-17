package com.mediahub.rebuilt.ui.player

import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/** Full-screen local video playback activity. */
class VideoPlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val playerView = PlayerView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            useController = true
        }
        setContentView(playerView)

        val mediaUri: Uri? = intent.data ?: intent.getStringExtra(EXTRA_MEDIA_URI)?.let(Uri::parse)
        if (mediaUri == null) {
            finish()
            return
        }

        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            exo.setMediaItem(MediaItem.fromUri(mediaUri))
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
    }
}
