package com.mediahub.rebuilt.ui.music

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mediahub.rebuilt.service.AudioEqualizerController
import com.mediahub.rebuilt.service.SleepTimer

/** Equalizer presets + sleep timer controls. */
class EqualizerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF0D0C13.toInt()

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0D0C13.toInt())
            setPadding(dp(16), dp(20), dp(16), dp(24))
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(title("GRIMY EQUALIZER"))
        root.addView(hint("Presets use the system equalizer on the current playback session."))

        val current = getSharedPreferences("mediahub_audio", MODE_PRIVATE)
            .getString("eq_preset", "flat") ?: "flat"

        AudioEqualizerController.presets.forEach { preset ->
            root.addView(row(preset.name, preset.id == current) {
                AudioEqualizerController.applyPreset(this, preset.id)
                Toast.makeText(this, "${preset.name} applied", Toast.LENGTH_SHORT).show()
                recreate()
            })
        }

        root.addView(title("SLEEP TIMER"))
        root.addView(hint(
            if (SleepTimer.isActive())
                "Active — ${SleepTimer.remainingMs() / 60_000} min left"
            else "Pause music automatically after a delay"
        ))
        listOf(5, 15, 30, 45, 60, 90).forEach { m ->
            root.addView(row("$m minutes", false) {
                SleepTimer.start(this, m)
            })
        }
        root.addView(row("Cancel timer", false) {
            SleepTimer.cancel(this)
        })
    }

    private fun title(t: String) = TextView(this).apply {
        text = t
        textSize = 18f
        setTextColor(0xFFFF3789.toInt())
        setPadding(0, dp(16), 0, dp(8))
        setTypeface(null, android.graphics.Typeface.BOLD)
    }

    private fun hint(t: String) = TextView(this).apply {
        text = t
        textSize = 12f
        setTextColor(0xFFAAAAAA.toInt())
        setPadding(0, 0, 0, dp(10))
    }

    private fun row(label: String, selected: Boolean, onClick: () -> Unit) = TextView(this).apply {
        text = if (selected) "✓  $label" else label
        textSize = 15f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = GradientDrawable().apply {
            setColor(if (selected) 0xFF392033.toInt() else 0xFF1A1822.toInt())
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), if (selected) 0xFFFF3789.toInt() else 0x33FFFFFF)
        }
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.bottomMargin = dp(8)
        layoutParams = lp
        setOnClickListener { onClick() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
