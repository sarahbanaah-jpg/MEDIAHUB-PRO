package com.mediahub.rebuilt.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/** In-app Dynamic Island aligned to the device camera cutout when Android exposes it. */
class DynamicIslandOverlay(private val context: Context, private val host: FrameLayout) {
    private val prefs = context.getSharedPreferences("mediahub_settings", Context.MODE_PRIVATE)
    private val label = TextView(context)
    private var receiverRegistered = false
    private var notificationReceiverRegistered = false
    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { render() }
    }
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (prefs.getString("island_mode", "smart") == "charging") {
                val state = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
                label.text = if (state == BatteryManager.BATTERY_STATUS_CHARGING || state == BatteryManager.BATTERY_STATUS_FULL || plugged != 0) "⚡  Charging" else "MEDIAHUB PRO"
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
            setOnClickListener { render() }
        }
        host.addView(label, FrameLayout.LayoutParams(widthPx(), dp(38), Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        host.setOnApplyWindowInsetsListener { _, insets ->
            refreshPosition()
            insets
        }
        refreshPosition()
        render()
    }

    fun start() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(context, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
        ContextCompat.registerReceiver(context, notificationReceiver, IntentFilter(com.mediahub.rebuilt.service.MediaHubNotificationListener.ACTION_NOTIFICATION_UPDATED), ContextCompat.RECEIVER_NOT_EXPORTED)
        notificationReceiverRegistered = true
        render()
    }

    fun stop() {
        if (receiverRegistered) {
            try { context.unregisterReceiver(batteryReceiver) } catch (_: Exception) { }
            receiverRegistered = false
        }
        if (notificationReceiverRegistered) {
            try { context.unregisterReceiver(notificationReceiver) } catch (_: Exception) { }
            notificationReceiverRegistered = false
        }
    }

    fun refresh() {
        val params = label.layoutParams as FrameLayout.LayoutParams
        params.width = widthPx()
        label.layoutParams = params
        label.background = glassBackground()
        label.visibility = if (prefs.getBoolean("island_enabled", true)) View.VISIBLE else View.GONE
        refreshPosition()
        render()
    }

    private fun glassBackground() = GradientDrawable().apply {
        setColor(if (prefs.getBoolean("island_glass", true)) 0xE612101B.toInt() else 0xFF000000.toInt())
        cornerRadius = dp(30).toFloat()
        setStroke(dp(1), 0x55FFFFFF)
    }

    private fun refreshPosition() {
        val params = label.layoutParams as? FrameLayout.LayoutParams ?: return
        val cutout: Rect? = if (android.os.Build.VERSION.SDK_INT >= 28) {
            host.rootWindowInsets?.displayCutout?.boundingRects?.firstOrNull { !it.isEmpty }
        } else null
        val horizontalOffset = dp(prefs.getInt("island_offset_x_dp", 0).coerceIn(-120, 120))
        val verticalOffset = dp(prefs.getInt("island_offset_y_dp", 0).coerceIn(-40, 100))
        if (cutout != null) {
            params.gravity = Gravity.TOP or Gravity.START
            params.leftMargin = (cutout.centerX() - params.width / 2 + horizontalOffset).coerceIn(0, (host.width - params.width).coerceAtLeast(0))
            params.topMargin = (cutout.centerY() - params.height / 2 + verticalOffset).coerceAtLeast(0)
        } else {
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.leftMargin = 0
            params.topMargin = statusBarInset() + dp(8) + verticalOffset
            if (horizontalOffset != 0) {
                params.gravity = Gravity.TOP or Gravity.START
                params.leftMargin = ((host.width - params.width) / 2 + horizontalOffset).coerceIn(0, (host.width - params.width).coerceAtLeast(0))
            }
        }
        label.layoutParams = params
    }

    private fun render() {
        label.text = when (prefs.getString("island_mode", "smart")) {
            "music" -> "♫  " + prefs.getString("island_title", "Music player")
            "video" -> "▶  " + prefs.getString("island_title", "Video player")
            "charging" -> "⚡  Charging mode"
            "notifications" -> "●  " + prefs.getString("island_notification_title", "No recent notifications")
            else -> prefs.getString("island_title", null)?.let { "♫  $it" } ?: "MEDIAHUB PRO"
        }
        label.background = glassBackground()
    }

    private fun widthPx(): Int = dp(prefs.getInt("island_width_dp", 190).coerceIn(140, 300))
    private fun statusBarInset(): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else dp(24)
    }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}