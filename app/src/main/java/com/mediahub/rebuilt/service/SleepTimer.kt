package com.mediahub.rebuilt.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import android.content.ComponentName

/**
 * Simple sleep timer: pause playback after N minutes.
 */
object SleepTimer {
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var endAt = 0L
    private var stopRunnable: Runnable? = null

    fun isActive(): Boolean = endAt > System.currentTimeMillis()

    fun remainingMs(): Long = (endAt - System.currentTimeMillis()).coerceAtLeast(0L)

    fun cancel(context: Context) {
        stopRunnable?.let { handler.removeCallbacks(it) }
        stopRunnable = null
        endAt = 0L
        Toast.makeText(context, "Sleep timer cancelled", Toast.LENGTH_SHORT).show()
    }

    fun start(context: Context, minutes: Int) {
        cancelQuiet()
        if (minutes <= 0) return
        endAt = System.currentTimeMillis() + minutes * 60_000L
        val r = Runnable {
            pausePlayback(context)
            endAt = 0L
            Toast.makeText(context.applicationContext, "Sleep timer — playback paused", Toast.LENGTH_LONG).show()
        }
        stopRunnable = r
        handler.postDelayed(r, minutes * 60_000L)
        Toast.makeText(context, "Sleep timer: $minutes min", Toast.LENGTH_SHORT).show()
    }

    private fun cancelQuiet() {
        stopRunnable?.let { handler.removeCallbacks(it) }
        stopRunnable = null
        endAt = 0L
    }

    private fun pausePlayback(context: Context) {
        try {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            future.addListener({
                try {
                    val controller = future.get()
                    controller.pause()
                    controller.release()
                } catch (_: Exception) {
                }
            }, MoreExecutors.directExecutor())
        } catch (_: Exception) {
        }
    }
}
