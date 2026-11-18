package com.mediahub.rebuilt.ambient

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Low-distraction in-app charging clock. Does not claim to replace OEM always-on display. */
class AmbientDisplayActivity : AppCompatActivity() {
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var battery: TextView
    private lateinit var bars: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val now = Date()
            clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
            date.text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now)
            handler.postDelayed(this, 1000L)
        }
    }
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            battery.text = (if (charging) "⚡ Charging" else "Battery") + if (level >= 0 && scale > 0) " · ${level * 100 / scale}%" else ""
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(0xFF030208.toInt())
        }
        clock = TextView(this).apply {
            text = "00:00"
            textSize = 76f
            letterSpacing = 0.04f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
        }
        date = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(0xFFB9A9C9.toInt())
        }
        battery = TextView(this).apply {
            text = "Battery status"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFFFF79B8.toInt())
        }
        bars = TextView(this).apply {
            text = "▂ ▄ ▆ ▄ ▂ ▄ ▆ ▄ ▂"
            textSize = 24f
            letterSpacing = 0.18f
            gravity = Gravity.CENTER
            setTextColor(0xFFB66BFF.toInt())
            setPadding(0, dp(32), 0, 0)
        }
        root.addView(clock)
        root.addView(date)
        root.addView(battery.apply { setPadding(0, dp(18), 0, 0) })
        root.addView(bars)
        root.addView(TextView(this).apply {
            text = "MEDIAHUB PRO  ·  Tap anywhere to exit"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(0xFF6F627B.toInt())
            setPadding(0, dp(48), 0, 0)
        })
        root.setOnClickListener { finish() }
        setContentView(root)
        root.setOnClickListener { finish() }
        tick.run()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, powerReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        try { unregisterReceiver(powerReceiver) } catch (_: Exception) { }
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
