package com.mediahub.rebuilt.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class GrimyBoardSettingsActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("grimy_board", Context.MODE_PRIVATE) }
    private lateinit var box: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(0xFF0D0C13.toInt()) }
        box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 24, 22, 24)
            setBackgroundColor(0xFF0D0C13.toInt())
        }
        scroll.addView(box)
        setContentView(scroll)
        draw()
    }

    private fun draw() {
        box.removeAllViews()
        text("GRIMY BOARD", 26f, 0xFFFF3789.toInt(), true)
        text("Suggestions · one-hand · numpad · voice · private", 13f, Color.LTGRAY)

        text("THEMES", 12f, 0xFFFF3789.toInt(), true)
        listOf(
            "pink" to "Pink Glow",
            "amoled" to "AMOLED Black",
            "neon" to "Neon Mint",
            "glass" to "Purple Glass",
            "grimy" to "Grimy Mode"
        ).forEach { (id, name) ->
            choice("Theme · $name", prefs.getString("theme", "pink") == id) {
                prefs.edit().putString("theme", id).apply()
                draw()
            }
        }

        text("SMART TYPING", 12f, 0xFFFF3789.toInt(), true)
        choice("Word suggestions bar", prefs.getBoolean("suggestions", true)) {
            flip("suggestions", true); draw()
        }
        choice("Key popup bubble (Gboard-style)", prefs.getBoolean("key_popup", true)) {
            flip("key_popup", true); draw()
        }
        choice("Number row", prefs.getBoolean("numbers", true)) {
            flip("numbers", true); draw()
        }
        choice("Double-space for period", prefs.getBoolean("double_space", true)) {
            flip("double_space", true); draw()
        }

        text("FEEDBACK", 12f, 0xFFFF3789.toInt(), true)
        choice("Key vibration", prefs.getBoolean("haptics", true)) {
            flip("haptics", true); draw()
        }
        listOf(0 to "Haptic · Light", 1 to "Haptic · Medium", 2 to "Haptic · Strong").forEach { (n, label) ->
            choice(label, prefs.getInt("haptic_level", 1) == n) {
                prefs.edit().putInt("haptic_level", n).apply()
                draw()
            }
        }
        choice("Key sound", prefs.getBoolean("sound", false)) {
            flip("sound", false); draw()
        }

        text("KEY HEIGHT", 12f, 0xFFFF3789.toInt(), true)
        listOf(42 to "Compact", 48 to "Standard", 54 to "Tall").forEach { (n, label) ->
            choice("$label keys", prefs.getInt("height", 48) == n) {
                prefs.edit().putInt("height", n).apply()
                draw()
            }
        }

        text("GRIMY FONT STUDIO · RECIPIENT-VISIBLE", 12f, 0xFFFF3789.toInt(), true)
        text("Choose a style that is inserted as Unicode text. People can see it in WhatsApp and other apps without installing GRIMY BOARD. Some devices may render certain symbols differently.", 12f, Color.LTGRAY)
        val activeStyle = prefs.getString("text_style", "grimy") ?: "grimy"
        text("Preview: " + GrimyTextStyles.apply("Hi, welcome to GRIMY!", activeStyle), 20f, Color.WHITE, true)
        GrimyTextStyles.styles.forEach { style ->
            choice("${style.name}  ·  ${style.sample}", activeStyle == style.id) {
                prefs.edit().putString("text_style", style.id).apply()
                draw()
            }
        }

        text("PRIVACY", 12f, 0xFFFF3789.toInt(), true)
        choice("Private mode (no recent / no clipboard save)", prefs.getBoolean("incognito", false)) {
            flip("incognito", false); draw()
        }
        text(
            "Typed text is not sent to a server. Private mode stops local history.",
            13f,
            Color.LTGRAY
        )

        text("ON KEYBOARD", 12f, 0xFFFF3789.toInt(), true)
        text(
            "• ☺ Grimy emoji (Mine pack)\n" +
                "• 123 Number pad\n" +
                "• ⊏ One-handed mode\n" +
                "• 🎙 Voice typing\n" +
                "• ↶ Undo\n" +
                "• EN/SW language\n" +
                "• ▤ Clipboard + pin",
            13f,
            Color.LTGRAY
        )

        choice("Open Android keyboard settings", false) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        choice("Choose GRIMY BOARD", false) {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        choice("Done", false) { finish() }
    }

    private fun flip(k: String, d: Boolean) {
        prefs.edit().putBoolean(k, !prefs.getBoolean(k, d)).apply()
    }

    private fun text(v: String, size: Float, color: Int, bold: Boolean = false) {
        box.addView(TextView(this).apply {
            text = v
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, 1)
            setPadding(4, 12, 4, 8)
        })
    }

    private fun choice(v: String, on: Boolean, action: () -> Unit) {
        box.addView(
            TextView(this).apply {
                text = (if (on) "✓  " else "    ") + v
                textSize = 15f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(16, 14, 16, 14)
                setBackgroundColor(if (on) 0xFF30202B.toInt() else 0xFF211F28.toInt())
                setOnClickListener { action() }
            },
            LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 5, 0, 5) }
        )
    }
}
