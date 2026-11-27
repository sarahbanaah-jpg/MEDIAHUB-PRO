package com.mediahub.rebuilt.whatsapp

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.net.Uri
import android.widget.EditText
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Bounded local text compatibility bench. Never automates WhatsApp messaging. */
class GrimyLabActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("grimy_lab", MODE_PRIVATE) }
    private lateinit var status: TextView
    private lateinit var sample: TextView
    private lateinit var timerLabel: TextView
    private lateinit var recipientInput: EditText
    private var timer: CountDownTimer? = null
    private var sessionEndsAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF08090B.toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(38), dp(20), dp(28))
            setBackgroundColor(0xFF08090B.toInt())
        }
        val scroll = ScrollView(this).apply { addView(root) }
        root.addView(label("‹ BACK", 0xFFB7FF3C.toInt(), 13f).apply { setOnClickListener { finish() } })
        root.addView(label("GRIMY LAB", 0xFFB7FF3C.toInt(), 12f))
        root.addView(label("Message Engineering", 0xFFFFFFFF.toInt(), 27f, true))
        root.addView(label("Controlled compatibility bench • local-only diagnostics", 0xFFAAAEB8.toInt(), 13f))
        root.addView(card().apply {
            addView(label("SAFETY BOUNDARY", 0xFFFF5757.toInt(), 12f, true))
            addView(label("This lab does not crash, freeze, flood, or disable WhatsApp. It creates bounded samples for manual review and optional sharing. You choose whether to send a sample.", 0xFFE1E3E8.toInt(), 14f))
        })
        root.addView(label("TEST PROFILE", 0xFFB7FF3C.toInt(), 12f, true))
        root.addView(label("WhatsApp test messages are manually opened for one recipient at a time. Maximum message length: 2,000 characters. No automated or repeated sending.", 0xFFAAAEB8.toInt(), 13f))
        status = label("READY • No active session", 0xFFFFFFFF.toInt(), 15f, true)
        timerLabel = label("Observation timer: not running", 0xFFB7FF3C.toInt(), 14f)
        root.addView(card().apply { addView(status); addView(timerLabel) })
        root.addView(button("START 30-MINUTE OBSERVATION") { startSession() })
        root.addView(button("GENERATE SAFE SAMPLE (1 KB)") { generate(1024) })
        root.addView(button("GENERATE SAFE SAMPLE (4 KB)") { generate(4096) })
        root.addView(button("GENERATE LONG TEST (2,000 CHARACTERS)") { generate(2000) })
        recipientInput = EditText(this).apply {
            hint = "Recipient number with country code, e.g. 2547XXXXXXXX"
            setHintTextColor(0xFF858B96.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply { setColor(0xFF151820.toInt()); cornerRadius = dp(12).toFloat() }
        }
        root.addView(recipientInput)
        root.addView(button("OPEN WHATSAPP CHAT WITH TEST MESSAGE") { openWhatsAppChat() })
        sample = label("Your sample will appear here. Samples are generated locally and are not sent automatically.", 0xFFE1E3E8.toInt(), 13f)
        root.addView(card().apply { addView(sample) })
        root.addView(button("SHARE SAMPLE…") { shareSample() })
        root.addView(button("END SESSION") { endSession("Stopped by user") })
        root.addView(label("WhatsApp opens a chat with the message prefilled. You must review it and tap Send yourself. MEDIAHUB-PRO cannot verify delivery or measure WhatsApp performance.", 0xFF858B96.toInt(), 12f))
        setContentView(scroll)
        restoreSession()
    }

    private fun startSession() {
        val now = System.currentTimeMillis()
        sessionEndsAt = now + 30L * 60L * 1000L
        prefs.edit().putLong("session_end", sessionEndsAt).apply()
        status.text = "SESSION ACTIVE • Local observation only"
        runTimer()
    }

    private fun restoreSession() {
        sessionEndsAt = prefs.getLong("session_end", 0L)
        if (sessionEndsAt > System.currentTimeMillis()) {
            status.text = "SESSION ACTIVE • Restored"
            runTimer()
        }
    }

    private fun runTimer() {
        timer?.cancel()
        val remaining = (sessionEndsAt - System.currentTimeMillis()).coerceAtLeast(0L)
        timer = object : CountDownTimer(remaining, 1000L) {
            override fun onTick(ms: Long) {
                timerLabel.text = "Observation: " + (ms / 60000).toString().padStart(2, '0') + ":" + ((ms % 60000) / 1000).toString().padStart(2, '0') + " remaining"
            }
            override fun onFinish() { endSession("30-minute observation complete") }
        }.start()
    }

    private fun endSession(reason: String) {
        timer?.cancel()
        timer = null
        sessionEndsAt = 0L
        prefs.edit().remove("session_end").apply()
        status.text = "SESSION ENDED • " + reason
        timerLabel.text = "Observation timer: stopped"
    }

    private fun generate(size: Int) {
        val safeSize = size.coerceIn(1, 2000)
        val unit = "GRIMY LAB · Compatibility sample · 0123456789 · AaZz · 🙂\n"
        val text = buildString(safeSize) {
            while (length < safeSize) append(unit)
            if (length > safeSize) setLength(safeSize)
        }
        sample.text = text
        status.text = "SAMPLE READY • " + text.length + " characters"
    }

    private fun openWhatsAppChat() {
        val number = recipientInput.text.toString().filter { it.isDigit() }
        if (number.length !in 8..15) {
            Toast.makeText(this, "Enter a valid international number with country code", Toast.LENGTH_LONG).show()
            return
        }
        val text = sample.text.toString()
        if (text.isBlank() || text.startsWith("Your sample")) {
            Toast.makeText(this, "Generate a test message first", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = Uri.parse("https://wa.me/$number?text=${Uri.encode(text)}")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            Toast.makeText(this, "WhatsApp could not be opened", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareSample() {
        val text = sample.text.toString()
        if (text.isBlank() || text.startsWith("Your sample")) {
            Toast.makeText(this, "Generate a sample first", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Review and choose where to share"))
    }

    private fun label(value: String, color: Int, size: Float, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            setTextColor(color)
            textSize = size
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(8))
        }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12))
        background = GradientDrawable().apply {
            setColor(0xFF151820.toInt())
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), 0x335CEB42)
        }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10); bottomMargin = dp(10) }
    }

    private fun button(title: String, action: () -> Unit) = TextView(this).apply {
        text = title
        gravity = Gravity.CENTER
        setTextColor(0xFF08090B.toInt())
        textSize = 13f
        setTypeface(null, Typeface.BOLD)
        setPadding(dp(14), dp(15), dp(14), dp(15))
        background = GradientDrawable().apply { setColor(0xFFB7FF3C.toInt()); cornerRadius = dp(13).toFloat() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(6) }
        setOnClickListener { action() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }
}
