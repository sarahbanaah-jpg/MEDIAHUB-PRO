package com.mediahub.rebuilt.service

import android.media.audiofx.Equalizer

/** App-process equalizer attached to the Media3 playback audio session. */
object AudioEqualizerController {
    @Volatile private var effect: Equalizer? = null

    @Synchronized fun attach(sessionId: Int) {
        if (sessionId <= 0) return
        if (effect != null) return
        try {
            effect = Equalizer(0, sessionId).apply { enabled = true }
        } catch (_: Exception) {
            effect = null
        }
    }

    fun current(): Equalizer? = effect

    @Synchronized fun release() {
        try { effect?.release() } catch (_: Exception) { }
        effect = null
    }
}
