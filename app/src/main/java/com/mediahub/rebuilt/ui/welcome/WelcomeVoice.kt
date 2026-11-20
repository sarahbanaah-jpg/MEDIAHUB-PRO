package com.mediahub.rebuilt.ui.welcome

import android.content.Context

/**
 * Welcome voice removed.
 * Phone/computer TTS sounded robotic. A real human voice requires a recorded
 * audio file from a person — not available in this build.
 * App opens silently for a premium feel.
 */
object WelcomeVoice {
    fun speakOnce(context: Context, message: String = "") {
        // Intentionally empty — no TTS, no computer voice.
    }

    fun shutdown() {}
}
