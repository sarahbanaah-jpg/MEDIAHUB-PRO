package com.mediahub.rebuilt.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * In-app Dynamic Island surface. It intentionally stays inside the app window;
 * Android does not permit an ordinary app to draw over other apps without
 * special overlay access.
 */
class DynamicIslandOverlay(private val context: Context, private val host: FrameLayout) {
    private val prefs = context.getSharedPreferences("mediahub_settings", Context.MODE_PRIVATE)
    private val label = TextView(context)
    private var receiverRegistered = false
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (prefs.getString("island_mode", "smart") == "charging") {
                val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
                label.text = if (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL || plugged != 0) "⚡  Charging" else "MEDIAHUB PRO"
            } else render()
        }
    }

    init {
        label.apply {
            text = "MEDIAHUB PRO"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(18), dp(9), dp(18), dp(9))
            elevation = dp(12).toFloat()
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                setColor(if (prefs.getBoolean("island_glass", true)) 0xE612101B.toInt() else 0xFF000000.toInt())
                cornerRadius = dp(30).toFloat()
                setStroke(dp(1), 0x55FFFFFF)
            }
            setOnClickListener { render() }
        }
        host.addView(label, FrameLayout.LayoutParams(widthPx(), dp(38), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(8) + statusBarInset()
        })
        render()
    }

    fun start() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(context, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
        render()
    }

    fun stop() {
        if (!receiverRegistered) return
        try { context.unregisterReceiver(batteryReceiver) } catch (_: Exception) { }
        receiverRegistered = false
    }

    fun refresh() {
        val params = label.layoutParams as FrameLayout.LayoutParams
        params.width = widthPx()
        label.layoutParams = params
        label.background = GradientDrawable().apply {
            setColor(if (prefs.getBoolean("island_glass", true)) 0xE612101B.toInt() else 0xFF000000.toInt())
            cornerRadius = dp(30).toFloat()
            setStroke(dp(1), 0x55FFFFFF)
        }
        label.visibility = if (prefs.getBoolean("island_enabled", true)) View.VISIBLE else View.GONE
        render()
    }

    private fun render() {
        when (prefs.getString("island_mode", "smart")) {
            "music" -> label.text = "♫  " + prefs.getString("island_title", "Music player")
            "video" -> label.text = "▶  " + prefs.getString("island_title", "Video player")
            "charging" -> label.text = "⚡  Charging mode"
            "notifications" -> label.text = "●  MediaHub alerts"
            else -> label.text = prefs.getString("island_title", null)?.let { "♫  $it" } ?: "MEDIAHUB PRO"
        }
    }

    private fun widthPx(): Int {
        val widthDp = prefs.getInt("island_width_dp", 190).coerceIn(140, 300)
        return dp(widthDp)
    }

    private fun statusBarInset(): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
