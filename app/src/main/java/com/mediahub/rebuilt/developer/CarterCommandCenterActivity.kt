package com.mediahub.rebuilt.developer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** CART3R.JAILBREAK offline developer command center. */
class CarterCommandCenterActivity : AppCompatActivity() {
    private val red = 0xFFFF2448.toInt()
    private val muted = 0xFFB69AA3.toInt()
    private lateinit var root: LinearLayout
    private val sections = linkedMapOf(
        "DASHBOARD" to "System overview, status, quick access and version 1.0.0.",
        "COMMAND DIRECTORY" to "Searchable, copyable commands: /help, /menu, /code, /debug, /explain, /optimize, /review, /build, /status and /reset.",
        "CODE LAB" to "Python · JavaScript · HTML/CSS · C# · Unity · Android development · API development · Project architecture.",
        "DEVELOPER TOOLS" to "Code generator · Bug analyzer · UI architect · File planner · Performance analyzer · Documentation writer.",
        "SECURITY CENTER" to "Secure coding · Permission reviews · Input validation · Dependency auditing · Privacy protection · Authorized security testing.",
        "TERMS OF SERVICE" to "Use only for lawful development. Test only systems you own or are explicitly authorized to assess. Respect privacy, licenses and accountability.",
        "SETTINGS" to "Crimson Night theme · local interface · reset selections.",
        "ABOUT" to "CART3R.JAILBREAK · MEDIAHUB-PRO Pro developer suite · Version 1.0.0."
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(0xFF080607.toInt()) }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(28), dp(16), dp(32)) }
        scroll.addView(root)
        setContentView(scroll)
        showSection("DASHBOARD")
    }
    private fun showSection(name: String) {
        root.removeAllViews()
        root.addView(label("CART3R.JAILBREAK", 24f, red, true))
        root.addView(label("DEVELOPER COMMAND CENTER  ·  v1.0.0  ·  ONLINE", 11f, muted))
        val nav = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(14), 0, dp(10)) }
        sections.keys.forEach { key ->
            nav.addView(card(key, if (key == name) "ACTIVE SECTION" else "OPEN SECTION") { showSection(key) })
        }
        root.addView(nav)
        root.addView(label(name, 21f, red, true))
        root.addView(label(sections[name] ?: "", 14f, muted))
        if (name == "DASHBOARD") {
            root.addView(card("SYSTEM STATUS", "ONLINE · Local interface ready") {})
            root.addView(card("VERSION", "1.0.0 · Embedded in MEDIAHUB-PRO") {})
            root.addView(card("QUICK ACCESS", "Open Code Lab, Command Directory or Security Center") {
                showSection("COMMAND DIRECTORY")
            })
        }
        if (name == "COMMAND DIRECTORY") {
            listOf("/help", "/menu", "/code", "/debug", "/explain", "/optimize", "/review", "/build", "/status", "/reset").forEach { command ->
                root.addView(card(command, "Tap to copy command") { copy(command) })
            }
        }
        if (name == "SETTINGS") root.addView(card("RESET SELECTIONS", "Restore dashboard defaults") { showSection("DASHBOARD") })
        root.addView(card("COPY SECTION DETAILS", "Copy the current section summary") { copy(sections[name] ?: "") })
    }
    private fun card(title: String, subtitle: String, action: () -> Unit): TextView =
        TextView(this).apply {
            text = "$title\n$subtitle"
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(15), dp(14), dp(15), dp(14))
            background = GradientDrawable().apply {
                setColor(0xFF160B10.toInt()); cornerRadius = dp(14).toFloat(); setStroke(dp(1), 0xFF642337.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) }
            isClickable = true; isFocusable = true
            setOnClickListener { action() }
        }
    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text; textSize = size; setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(5), 0, dp(5))
        }
    private fun copy(value: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("CART3R.JAILBREAK", value))
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
