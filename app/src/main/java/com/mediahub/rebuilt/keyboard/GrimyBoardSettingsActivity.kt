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
        text("Live preview · themes · typing controls", 14f, Color.LTGRAY)

        text("THEMES", 12f, 0xFFFF3789.toInt(), true)
        listOf(
            "pink" to "Pink Glow",
            "amoled" to "AMOLED Black",
            "neon" to "Neon Mint",
            "glass" to "Purple Glass"
        ).forEach { (id, name) ->
            choice("Theme · $name", prefs.getString("theme", "pink") == id) {
                prefs.edit().putString("theme", id).apply()
                draw()
            }
        }

        text("TYPING", 12f, 0xFFFF3789.toInt(), true)
        choice("Show live typing preview", prefs.getBoolean("preview", true)) {
            flip("preview", true); draw()
        }
        choice("Number row", prefs.getBoolean("numbers", true)) {
            flip("numbers", true); draw()
        }
        choice("Key vibration", prefs.getBoolean("haptics", true)) {
            flip("haptics", true); draw()
        }
        choice("Key sound", prefs.getBoolean("sound", false)) {
            flip("sound", false); draw()
        }
        choice("Double-space for period", prefs.getBoolean("double_space", true)) {
            flip("double_space", true); draw()
        }

        text("KEY HEIGHT", 12f, 0xFFFF3789.toInt(), true)
        listOf(42 to "Compact", 48 to "Standard", 54 to "Tall").forEach { (n, label) ->
            choice("$label keys", prefs.getInt("height", 48) == n) {
                prefs.edit().putInt("height", n).apply()
                draw()
            }
        }

        text("HOW TO USE", 12f, 0xFFFF3789.toInt(), true)
        text(
            "• Live strip shows text around the cursor\n" +
                "• Double-tap ⇧ for caps lock\n" +
                "• Hold ⌫ to delete quickly\n" +
                "• Long-press letters for accents\n" +
                "• Double-tap space for . ",
            13f,
            Color.LTGRAY
        )

        text("PRIVACY", 12f, 0xFFFF3789.toInt(), true)
        text(
            "Typed text is not sent to a server. Clipboard entries are temporary in-memory items and are cleared when the keyboard process closes.",
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
