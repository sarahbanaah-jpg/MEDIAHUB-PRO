package com.mediahub.rebuilt.ui.welcome

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Male welcome voice on app open.
 */
object WelcomeVoice {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingMessage: String? = null

    fun speakOnce(context: Context, message: String = "Welcome to MediaHub Pro. All rights reserved. Grimy.") {
        pendingMessage = message
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ready = true
                    pickMaleVoice()
                    tts?.language = Locale.US
                    tts?.setSpeechRate(0.92f)
                    tts?.setPitch(0.8f) // lower = more masculine
                    pendingMessage?.let {
                        tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "welcome")
                    }
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
                    !v.name.contains("female", true) &&
                    !v.name.contains("woman", true) &&
                    (v.name.contains("male", true) ||
                        v.name.contains("man", true) ||
                        v.name.contains("david", true) ||
                        v.name.contains("mark", true) ||
                        v.name.contains("daniel", true) ||
                        v.name.contains("english", true))
            } ?: voices.firstOrNull {
                it.locale.language == "en" &&
                    !it.name.contains("female", true) &&
                    !it.name.contains("woman", true)
            }
            if (male != null) engine.voice = male
        } catch (_: Exception) {
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        ready = false
    }
}
