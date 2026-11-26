package com.mediahub.rebuilt.device

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.StatFs
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class DeviceCenterActivity : Activity() {
    private val white = 0xFFFFFFFF.toInt()
    private val muted = 0xFFB8ADC9.toInt()
    private val accent = 0xFFFF2D95.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(0xFF08080D.toInt()) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(36))
        }
        scroll.addView(root)
        root.addView(TextView(this).apply {
            text = "Device Center"
            setTextColor(white)
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Your device profile, hardware capabilities and app compatibility"
            setTextColor(muted)
            textSize = 13f
            setPadding(0, dp(7), 0, dp(20))
        })

        val model = listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.distinct().joinToString(" ")
        card(root, "THIS DEVICE", listOf(
            "Device" to model.ifBlank { Build.DEVICE },
            "Manufacturer" to Build.MANUFACTURER,
            "Model code" to Build.MODEL,
            "Device codename" to Build.DEVICE,
            "Android version" to Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")",
            "Build fingerprint" to Build.FINGERPRINT.substringBeforeLast("/")
        ))

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        @Suppress("DEPRECATION")
        val refreshRate = (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.refreshRate
        card(root, "DISPLAY", listOf(
            "Resolution" to metrics.widthPixels.toString() + " × " + metrics.heightPixels + " pixels",
            "Density" to metrics.densityDpi.toString() + " dpi",
            "Refresh rate" to String.format(Locale.US, "%.0f Hz", refreshRate)
        ))

        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val stat = StatFs(filesDir.absolutePath)
        card(root, "MEMORY & STORAGE", listOf(
            "Total RAM" to formatBytes(memory.totalMem),
            "Available RAM" to formatBytes(memory.availMem),
            "Internal storage total" to formatBytes(stat.totalBytes),
            "Internal storage available" to formatBytes(stat.availableBytes)
        ))

        card(root, "PROCESSOR & SOFTWARE", listOf(
            "Supported CPU ABIs" to Build.SUPPORTED_ABIS.joinToString(", "),
            "Primary ABI" to Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            "Kernel" to System.getProperty("os.version", "Not reported"),
            "MediaHub Pro version" to com.mediahub.rebuilt.BuildConfig.VERSION_NAME + " (" + com.mediahub.rebuilt.BuildConfig.VERSION_CODE + ")",
            "Minimum supported Android" to "Android 8.0 (API 26) or newer"
        ))

        val pm = packageManager
        val capabilities = listOf(
            "Camera" to android.content.pm.PackageManager.FEATURE_CAMERA_ANY,
            "Flash" to android.content.pm.PackageManager.FEATURE_CAMERA_FLASH,
            "Microphone" to android.content.pm.PackageManager.FEATURE_MICROPHONE,
            "Bluetooth" to android.content.pm.PackageManager.FEATURE_BLUETOOTH,
            "Bluetooth Low Energy" to android.content.pm.PackageManager.FEATURE_BLUETOOTH_LE,
            "Wi-Fi" to android.content.pm.PackageManager.FEATURE_WIFI,
            "NFC" to android.content.pm.PackageManager.FEATURE_NFC,
            "GPS" to android.content.pm.PackageManager.FEATURE_LOCATION_GPS,
            "Telephony" to android.content.pm.PackageManager.FEATURE_TELEPHONY,
            "Touchscreen" to android.content.pm.PackageManager.FEATURE_TOUCHSCREEN,
            "Accelerometer" to android.content.pm.PackageManager.FEATURE_SENSOR_ACCELEROMETER,
            "Gyroscope" to android.content.pm.PackageManager.FEATURE_SENSOR_GYROSCOPE
        )
        card(root, "HARDWARE CAPABILITIES", capabilities.map { pair ->
            pair.first to if (pm.hasSystemFeature(pair.second)) "Available" else "Not available"
        })

        card(root, "MEDIAHUB PRO COMPATIBILITY", listOf(
            "Installation status" to "Installed and running",
            "Android requirement" to if (Build.VERSION.SDK_INT >= 26) "Passed · API 26+" else "Not supported",
            "Local music library" to "Supported",
            "Local video library" to "Supported",
            "Photo gallery" to "Supported",
            "Background playback" to "Supported where Android permits background services",
            "Camera-dependent features" to if (pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)) "Hardware available" else "Limited · no camera detected",
            "Microphone-dependent features" to if (pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_MICROPHONE)) "Hardware available" else "Limited · no microphone detected"
        ))

        card(root, "GLOBAL DEVICE DIRECTORY", listOf(
            "Cloud registration" to "Not connected",
            "Shared installation count" to "Unavailable until a shared registry is configured",
            "Privacy" to "Device details stay on this phone. No device ID or account identity is uploaded."
        ))
        root.addView(TextView(this).apply {
            text = "Compatibility can vary by Android version, manufacturer firmware, permissions and hardware. Optional hardware is reported as available or unavailable; no unique device ID, serial number, IMEI or account identity is collected."
            setTextColor(muted)
            textSize = 12f
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(4), dp(14), dp(4), dp(8))
        })
        root.addView(TextView(this).apply {
            text = "CLOSE"
            gravity = Gravity.CENTER
            setTextColor(white)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = GradientDrawable().apply {
                setColor(0xFF24132E.toInt())
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), 0x55FF2D95)
            }
            setOnClickListener { finish() }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        })
        setContentView(scroll)
    }

    private fun card(root: LinearLayout, title: String, rows: List<Pair<String, String>>) {
        root.addView(TextView(this).apply {
            text = title
            setTextColor(accent)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.08f
            setPadding(dp(2), dp(18), dp(2), dp(8))
        })
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(5), dp(15), dp(5))
            background = GradientDrawable().apply {
                setColor(0xFF160E28.toInt())
                cornerRadius = dp(17).toFloat()
                setStroke(dp(1), 0x28FFFFFF)
            }
        }
        rows.forEachIndexed { index, pair ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(10), 0, dp(10))
            }
            row.addView(TextView(this).apply {
                text = pair.first
                setTextColor(muted)
                textSize = 11f
            })
            row.addView(TextView(this).apply {
                text = pair.second.ifBlank { "Not reported by device" }
                setTextColor(white)
                textSize = 14f
                setPadding(0, dp(3), 0, 0)
            })
            box.addView(row)
            if (index != rows.lastIndex) {
                box.addView(android.view.View(this).apply {
                    setBackgroundColor(0x22FFFFFF)
                    layoutParams = LinearLayout.LayoutParams(-1, dp(1))
                })
            }
        }
        root.addView(box)
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "Not reported"
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 1.0) String.format(Locale.US, "%.2f GB", gb)
        else String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024.0))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
