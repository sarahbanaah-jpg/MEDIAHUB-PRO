package com.mediahub.rebuilt.service

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioManager
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.mediahub.rebuilt.ui.MainActivity

/**
 * Media3 MediaSessionService.
 * Lock-screen controls, media notification, background playback.
 * Default: play through the full queue (auto-next when a track ends).
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()

        val exo = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        val sessionId = (getSystemService(AUDIO_SERVICE) as AudioManager).generateAudioSessionId()
        if (sessionId > 0) {
            exo.setAudioSessionId(sessionId)
            getSharedPreferences("mediahub_audio", MODE_PRIVATE).edit()
                .putInt("session_id", sessionId).apply()
            AudioEqualizerController.attach(sessionId)
        }

        // Continuous play: when the current track ends, go to the next in the queue
        exo.repeatMode = Player.REPEAT_MODE_ALL
        exo.shuffleModeEnabled = false
        player = exo

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, exo)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            AudioEqualizerController.release()
            player?.release()
            release()
            mediaSession = null
        }
        player = null
        super.onDestroy()
    }
}
