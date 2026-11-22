package com.mediahub.rebuilt.whatsapp

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Dashboard for the real MediaHub WhatsApp bot backend (Baileys).
 * The bot process runs on your server — not on this phone.
 */
class WhatsAppBotActivity : AppCompatActivity() {
    private lateinit var statusView: TextView
    private lateinit var accountView: TextView
    private lateinit var statsView: TextView
    private lateinit var logsView: TextView
    private lateinit var qrView: ImageView
    private lateinit var qrHint: TextView
    private lateinit var urlInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var botToggle: TextView
    private var pollJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = 0x00000000

        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF08080D.toInt())
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(44), dp(16), dp(28))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "‹ Back"
            setTextColor(0xFFFF2D95.toInt())
            textSize = 14f
            setOnClickListener { finish() }
        })
        root.addView(TextView(this).apply {
            text = "WHATSAPP BOT"
            setTextColor(0xFFFF2D95.toInt())
            textSize = 11f
            letterSpacing = 0.16f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(16), 0, dp(4))
        })
        root.addView(TextView(this).apply {
            text = "Real Baileys engine"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Connect your personal WhatsApp via Linked Devices. The bot runs on your always-on server — not inside this phone."
            setTextColor(0xFFB0AAB8.toInt())
            textSize = 13f
            setPadding(0, dp(8), 0, dp(16))
        })

        // Server settings
        root.addView(section("Backend connection"))
        urlInput = field("https://your-bot-host.example.com")
        tokenInput = field("API token from server .env")
        urlInput.setText(WhatsAppBotApi.getBaseUrl(this))
        tokenInput.setText(WhatsAppBotApi.getToken(this))
        root.addView(urlInput)
        root.addView(tokenInput)
        root.addView(primaryBtn("Save connection") {
            WhatsAppBotApi.saveConnection(this, urlInput.text.toString(), tokenInput.text.toString())
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            refresh()
        })

        // Status card
        root.addView(section("Live status"))
        val statusCard = glassCard()
        statusView = TextView(this).apply {
            text = "Status: —"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        }
        accountView = TextView(this).apply {
            text = "Account: not linked"
            setTextColor(0xFFC8C2D0.toInt())
            textSize = 13f
            setPadding(0, dp(6), 0, 0)
        }
        statsView = TextView(this).apply {
            text = "Received 0 · Sent 0 · Chats 0"
            setTextColor(0xFFA89FB0.toInt())
            textSize = 12f
            setPadding(0, dp(6), 0, 0)
        }
        statusCard.addView(statusView)
        statusCard.addView(accountView)
        statusCard.addView(statsView)
        root.addView(statusCard)

        // Controls
        root.addView(rowBtns(
            "Start / QR" to { action { WhatsAppBotApi.start(this) } },
            "Reconnect" to { action { WhatsAppBotApi.reconnect(this) } }
        ))
        root.addView(rowBtns(
            "Logout" to { action { WhatsAppBotApi.logout(this) } },
            "Refresh" to { refresh() }
        ))

        botToggle = primaryBtn("Bot: —") {
            lifecycleScope.launch {
                try {
                    val cur = WhatsAppBotApi.status(this@WhatsAppBotActivity)
                    val next = WhatsAppBotApi.setEnabled(this@WhatsAppBotActivity, !cur.botEnabled)
                    applyStatus(next)
                } catch (e: Exception) {
                    toast(e.message ?: "Failed")
                }
            }
        }
        root.addView(botToggle)

        // QR
        root.addView(section("QR pairing"))
        qrHint = TextView(this).apply {
            text = "When status is QR, scan with WhatsApp → Linked Devices."
            setTextColor(0xFFA89FB0.toInt())
            textSize = 12f
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(qrHint)
        qrView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(260), dp(260)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFFFFFFFF.toInt())
            visibility = android.view.View.GONE
        }
        root.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER
            addView(qrView)
        })

        root.addView(section("Recent activity"))
        logsView = TextView(this).apply {
            text = "No logs yet"
            setTextColor(0xFFC8C2D0.toInt())
            textSize = 12f
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply {
                setColor(0xCC14141C.toInt())
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), 0x33FFFFFF)
            }
        }
        root.addView(logsView)

        root.addView(TextView(this).apply {
            text = "Host the backend from /whatsapp-bot on Railway, Render, Fly.io or a VPS. See whatsapp-bot/README.md"
            setTextColor(0xFF777381.toInt())
            textSize = 11f
            setPadding(0, dp(20), 0, 0)
        })

        setContentView(scroll)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            while (isActive) {
                refreshSilent()
                delay(4000)
            }
        }
    }

    override fun onPause() {
        pollJob?.cancel()
        super.onPause()
    }

    private fun refresh() {
        lifecycleScope.launch {
            try {
                applyStatus(WhatsAppBotApi.status(this@WhatsAppBotActivity))
            } catch (e: Exception) {
                statusView.text = "Status: offline / not configured"
                accountView.text = e.message ?: "Error"
                qrView.visibility = android.view.View.GONE
            }
        }
    }

    private suspend fun refreshSilent() {
        try {
            applyStatus(WhatsAppBotApi.status(this@WhatsAppBotActivity))
        } catch (_: Exception) {
        }
    }

    private fun action(block: suspend () -> BotStatus) {
        lifecycleScope.launch {
            try {
                applyStatus(block())
            } catch (e: Exception) {
                toast(e.message ?: "Request failed")
            }
        }
    }

    private fun applyStatus(s: BotStatus) {
        statusView.text = "Status: ${s.status.uppercase()}"
        accountView.text = when {
            !s.accountName.isNullOrBlank() -> "Account: ${s.accountName}"
            !s.accountId.isNullOrBlank() -> "Account: ${s.accountId}"
            else -> "Account: not linked"
        }
        statsView.text = "Received ${s.received} · Sent ${s.sent} · Chats ${s.conversations}"
        botToggle.text = if (s.botEnabled) "Bot: ON (tap to disable)" else "Bot: OFF (tap to enable)"
        val bmp = WhatsAppBotApi.qrToBitmap(s.qrDataUrl)
        if (bmp != null && (s.status == "qr" || s.status == "connecting")) {
            qrView.setImageBitmap(bmp)
            qrView.visibility = android.view.View.VISIBLE
            qrHint.text = "Scan this QR in WhatsApp → Settings → Linked Devices → Link a device"
        } else {
            qrView.visibility = android.view.View.GONE
            qrHint.text = "QR appears here when the server is waiting for pairing."
        }
        logsView.text = if (s.logs.isEmpty()) "No logs yet" else s.logs.take(12).joinToString("\n")
    }

    private fun section(title: String) = TextView(this).apply {
        text = title
        setTextColor(0xFFFFFFFF.toInt())
        textSize = 15f
        setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(18), 0, dp(8))
    }

    private fun field(hint: String) = EditText(this).apply {
        this.hint = hint
        setHintTextColor(0xFF777381.toInt())
        setTextColor(0xFFFFFFFF.toInt())
        textSize = 14f
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = GradientDrawable().apply {
            setColor(0xCC14141C.toInt())
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), 0x33FFFFFF)
        }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
    }

    private fun glassCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = GradientDrawable().apply {
            setColor(0xCC14141C.toInt())
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), 0x33FFFFFF)
        }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
    }

    private fun primaryBtn(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(0xFFFFFFFF.toInt())
        textSize = 14f
        setTypeface(null, Typeface.BOLD)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply {
            setColor(0xFFFF2D95.toInt())
            cornerRadius = dp(14).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        setOnClickListener { onClick() }
    }

    private fun rowBtns(a: Pair<String, () -> Unit>, b: Pair<String, () -> Unit>): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(smallBtn(a.first, a.second))
            addView(smallBtn(b.first, b.second))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        }
    }

    private fun smallBtn(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(0xFFFFFFFF.toInt())
        textSize = 13f
        setPadding(dp(10), dp(12), dp(10), dp(12))
        background = GradientDrawable().apply {
            setColor(0xFF1C1C26.toInt())
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), 0x33FFFFFF)
        }
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginEnd = dp(4)
            marginStart = dp(4)
        }
        setOnClickListener { onClick() }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
