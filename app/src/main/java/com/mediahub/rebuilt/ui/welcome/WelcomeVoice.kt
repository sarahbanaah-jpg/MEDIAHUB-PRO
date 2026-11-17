package com.mediahub.rebuilt.ui.welcome

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/**
 * Male welcome voice on app open. Prefers a male English voice when available.
 */
object WelcomeVoice {
    private var tts: TextToSpeech? = null
    private var ready = false

    fun speakOnce(context: Context, message: String = "Welcome to MediaHub Pro. All rights reserved. Grimy.") {
        val prefs = context.getSharedPreferences("mediahub_settings", Context.MODE_PRIVATE)
        // Speak every cold start of the process; throttle to once per 30 minutes so rapid relaunches are not noisy
        val last = prefs.getLong("welcome_voice_at", 0L)
        if (System.currentTimeMillis() - last < 30 * 60 * 1000L) return
        prefs.edit().putLong("welcome_voice_at", System.currentTimeMillis()).apply()

        if (tts == null) {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ready = true
                    pickMaleVoice()
                    tts?.language = Locale.US
                    tts?.setSpeechRate(0.95f)
                    tts?.setPitch(0.85f) // lower pitch = more masculine
                    tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "welcome")
                }
            }
        } else if (ready) {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "welcome")
        }
    }

    private fun pickMaleVoice() {
        val engine = tts ?: return
        try {
            val voices = engine.voices ?: return
            val male = voices.firstOrNull { v ->
                v.locale.language == "en" &&
                    (v.name.contains("male", true) ||
                        v.name.contains("man", true) ||
                        v.name.contains("david", true) ||
                        v.name.contains("mark", true) ||
                        v.name.contains("daniel", true) ||
                        v.features?.contains("male") == true)
            } ?: voices.firstOrNull { it.locale.language == "en" && !it.name.contains("female", true) }
            if (male != null) engine.voice = male
        } catch (_: Exception) {
        }
    }

    fun shutdown() {
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        tts = null
        ready = false
    }
}
