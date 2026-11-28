package com.mediahub.rebuilt.service

import android.content.Context
import android.media.audiofx.Equalizer

/**
 * System Equalizer attached to Media3 session.
 * Presets are applied as relative band levels (millibels).
 */
object AudioEqualizerController {
    @Volatile private var effect: Equalizer? = null

    data class Preset(val id: String, val name: String, val levelsDb: FloatArray)

    val presets = listOf(
        Preset("flat", "Flat", floatArrayOf(0f, 0f, 0f, 0f, 0f)),
        Preset("bass", "Bass Boost", floatArrayOf(8f, 5f, 1f, 0f, 0f)),
        Preset("treble", "Treble Boost", floatArrayOf(0f, 0f, 1f, 5f, 8f)),
        Preset("vocal", "Vocal", floatArrayOf(-2f, 1f, 5f, 4f, 1f)),
        Preset("rock", "Rock", floatArrayOf(5f, 3f, -1f, 2f, 5f)),
        Preset("pop", "Pop", floatArrayOf(-1f, 2f, 4f, 3f, 0f)),
        Preset("classical", "Classical", floatArrayOf(0f, 2f, 0f, 2f, 4f)),
        Preset("electronic", "Electronic", floatArrayOf(6f, 4f, 0f, 3f, 5f)),
        Preset("grimy", "Grimy", floatArrayOf(6f, 3f, 0f, 2f, 6f))
    )

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

    fun applyPreset(context: Context, presetId: String) {
        val eq = effect ?: return
        val preset = presets.find { it.id == presetId } ?: presets.first()
        try {
            val bands = eq.numberOfBands.toInt().coerceAtLeast(1)
            val range = eq.bandLevelRange
            for (i in 0 until bands) {
                val src = if (i < preset.levelsDb.size) preset.levelsDb[i]
                else preset.levelsDb.last()
                val mb = (src * 100f).toInt().coerceIn(range[0].toInt(), range[1].toInt())
                eq.setBandLevel(i.toShort(), mb.toShort())
            }
            eq.enabled = true
            context.getSharedPreferences("mediahub_audio", Context.MODE_PRIVATE)
                .edit().putString("eq_preset", preset.id).apply()
        } catch (_: Exception) {
        }
    }

    fun restore(context: Context) {
        val id = context.getSharedPreferences("mediahub_audio", Context.MODE_PRIVATE)
            .getString("eq_preset", "flat") ?: "flat"
        applyPreset(context, id)
    }

    @Synchronized fun release() {
        try { effect?.release() } catch (_: Exception) { }
        effect = null
    }
}
