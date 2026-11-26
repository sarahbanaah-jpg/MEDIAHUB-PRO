package com.mediahub.rebuilt.device

import android.app.Activity
import android.app.AlertDialog
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class DeviceCenterActivity : Activity() {
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val firebaseAuth by lazy { FirebaseAuth.getInstance() }
    private lateinit var directoryStatus: TextView
    private lateinit var directoryList: LinearLayout
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

        section(root, "GLOBAL DEVICE DIRECTORY")
        root.addView(TextView(this).apply {
            text = "See MediaHub Pro installations registered by other users. Sharing is optional. Only your manufacturer, model, Android version, app version and primary CPU ABI are published. No serial number, IMEI, location, RAM, storage or account name is shared."
            setTextColor(muted)
            textSize = 12f
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(2), 0, dp(2), dp(12))
        })
        directoryStatus = TextView(this).apply {
            text = "Loading the shared device directory…"
            setTextColor(white)
            textSize = 13f
            setPadding(dp(2), dp(8), dp(2), dp(10))
        }
        root.addView(directoryStatus)
        root.addView(directoryButton("SHARE THIS DEVICE") {
            AlertDialog.Builder(this)
                .setTitle("Share this device?")
                .setMessage("Your manufacturer, model, Android version, MediaHub Pro version and primary CPU ABI will be visible to other MediaHub Pro users in the global directory. No unique hardware ID or location is uploaded.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Share device") { _, _ -> registerDevice() }
                .show()
        })
        root.addView(directoryButton("REMOVE MY DEVICE FROM DIRECTORY") { removeDevice() })
        directoryList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(directoryList)
        loadDirectory()
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

    private fun section(root: LinearLayout, title: String) {
        root.addView(TextView(this).apply {
            text = title
            setTextColor(accent)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.08f
            setPadding(dp(2), dp(18), dp(2), dp(8))
        })
    }

    private fun directoryButton(label: String, click: () -> Unit) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(white)
        textSize = 12f
        setTypeface(null, Typeface.BOLD)
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = GradientDrawable().apply {
            setColor(0xFF24132E.toInt())
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), 0x55FF2D95)
        }
        setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(6) }
    }

    private fun registerDevice() {
        directoryStatus.text = "Connecting securely to Firebase…"
        val publish: (String) -> Unit = { uid ->
            val device = hashMapOf(
                "manufacturer" to Build.MANUFACTURER.take(80),
                "model" to Build.MODEL.take(100),
                "androidVersion" to Build.VERSION.RELEASE.take(30),
                "sdkInt" to Build.VERSION.SDK_INT,
                "appVersion" to com.mediahub.rebuilt.BuildConfig.VERSION_NAME,
                "appVersionCode" to com.mediahub.rebuilt.BuildConfig.VERSION_CODE,
                "primaryAbi" to Build.SUPPORTED_ABIS.firstOrNull().orEmpty().take(40),
                "lastSeen" to FieldValue.serverTimestamp()
            )
            firestore.collection("device_directory").document(uid).set(device)
                .addOnSuccessListener {
                    directoryStatus.text = "This installation is now listed in the global directory."
                    loadDirectory()
                }
                .addOnFailureListener { error ->
                    directoryStatus.text = "Could not publish device: ${error.localizedMessage ?: "Firebase error"}"
                }
        }
        val current = firebaseAuth.currentUser
        if (current != null) publish(current.uid)
        else firebaseAuth.signInAnonymously()
            .addOnSuccessListener { result -> result.user?.uid?.let(publish) }
            .addOnFailureListener { error ->
                directoryStatus.text = "Firebase sign-in failed. Enable Anonymous authentication in Firebase: ${error.localizedMessage ?: "unknown error"}"
            }
    }

    private fun removeDevice() {
        val remove: (String) -> Unit = { uid ->
            firestore.collection("device_directory").document(uid).delete()
                .addOnSuccessListener {
                    directoryStatus.text = "Your installation has been removed from the directory."
                    loadDirectory()
                }
                .addOnFailureListener { error ->
                    directoryStatus.text = "Could not remove this installation: ${error.localizedMessage ?: "Firebase error"}"
                }
        }
        val current = firebaseAuth.currentUser
        if (current != null) remove(current.uid)
        else firebaseAuth.signInAnonymously()
            .addOnSuccessListener { result -> result.user?.uid?.let(remove) }
            .addOnFailureListener { error ->
                directoryStatus.text = "Firebase sign-in failed: ${error.localizedMessage ?: "unknown error"}"
            }
    }

    private fun loadDirectory() {
        firestore.collection("device_directory")
            .orderBy("lastSeen", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .addOnSuccessListener { snapshot ->
                directoryList.removeAllViews()
                directoryStatus.text = "Registered installations: ${snapshot.size()} · showing up to 100 recent entries"
                if (snapshot.isEmpty) {
                    directoryStatus.text = "No devices have registered yet. Be the first to share yours."
                }
                snapshot.documents.forEachIndexed { index, doc ->
                    val manufacturer = doc.getString("manufacturer").orEmpty()
                    val model = doc.getString("model").orEmpty()
                    val android = doc.getString("androidVersion").orEmpty()
                    val sdk = doc.getLong("sdkInt")?.toString().orEmpty()
                    val version = doc.getString("appVersion").orEmpty()
                    val abi = doc.getString("primaryAbi").orEmpty()
                    val details = listOf(
                        "${manufacturer} ${model}".trim(),
                        "Android ${android} (API ${sdk})",
                        "MediaHub Pro ${version}",
                        if (abi.isNotBlank()) "CPU: ${abi}" else ""
                    ).filter { it.isNotBlank() }.joinToString("\n")
                    val item = TextView(this).apply {
                        text = "${index + 1}. ${details}"
                        setTextColor(white)
                        textSize = 13f
                        setLineSpacing(dp(3).toFloat(), 1f)
                        setPadding(dp(14), dp(13), dp(14), dp(13))
                        background = GradientDrawable().apply {
                            setColor(0xFF160E28.toInt())
                            cornerRadius = dp(14).toFloat()
                            setStroke(dp(1), 0x28FFFFFF)
                        }
                        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
                    }
                    directoryList.addView(item)
                }
            }
            .addOnFailureListener { error ->
                directoryStatus.text = "Global directory unavailable: ${error.localizedMessage ?: "Check Firebase setup and Firestore rules"}"
            }
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
