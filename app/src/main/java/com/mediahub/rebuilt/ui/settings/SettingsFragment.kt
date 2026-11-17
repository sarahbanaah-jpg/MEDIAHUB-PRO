package com.mediahub.rebuilt.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mediahub.rebuilt.BuildConfig
import com.mediahub.rebuilt.diagnostics.Diagnostics
import com.mediahub.rebuilt.performance.DevicePerformance
import com.mediahub.rebuilt.updates.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {
    override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, savedInstanceState: Bundle?): android.view.View {
        val scroll = ScrollView(requireContext()).apply { setBackgroundColor(0xFF05040A.toInt()) }
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(34), dp(20), dp(112))
        }
        scroll.addView(root)

        root.addView(TextView(requireContext()).apply {
            text = "Settings"
            setTextColor(0xFFFFF8FC.toInt())
            textSize = 29f
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(requireContext()).apply {
            text = "MEDIAHUB PRO  ·  v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
            setTextColor(0xFFC9B8C8.toInt())
            textSize = 14f
            setPadding(0, dp(8), 0, dp(18))
        })

        section(root, "UPDATES")
        root.addView(action(updateNotificationLabel()) {
            val prefs = requireContext().getSharedPreferences("mediahub_settings", 0)
            val enabled = !prefs.getBoolean("update_notifications", true)
            prefs.edit().putBoolean("update_notifications", enabled).apply()
            it.text = updateNotificationLabel()
            Toast.makeText(requireContext(), if (enabled) "Update notifications on" else "Update notifications off", Toast.LENGTH_SHORT).show()
        })
        root.addView(action("Check for updates") {
            it.text = "Checking…"
            UpdateChecker.check(requireContext(), notify = true) { info ->
                if (!isAdded) return@check
                it.text = "Check for updates"
                if (info == null) {
                    Toast.makeText(requireContext(), "You are on the latest version.", Toast.LENGTH_LONG).show()
                } else {
                    UpdateChecker.openUpdateScreen(requireContext(), info)
                }
            }
        })

        section(root, "PERMISSIONS")
        root.addView(action("Media permissions") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", requireContext().packageName, null)
            })
        })
        root.addView(action("Notification permissions") {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            })
        })

        section(root, "PERFORMANCE")
        root.addView(action("Refresh device performance") {
            viewLifecycleOwner.lifecycleScope.launch {
                val snap = withContext(Dispatchers.IO) { DevicePerformance.snapshot(requireContext()) }
                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        "RAM free ${snap.availableRamMb} MB · Storage free ${snap.freeStorageMb} MB · Battery ${snap.batteryPercent}%",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        })
        root.addView(action("Clear safe app cache") {
            viewLifecycleOwner.lifecycleScope.launch {
                val freed = withContext(Dispatchers.IO) { DevicePerformance.clearSafeCache(requireContext()) }
                if (isAdded) Toast.makeText(requireContext(), "Freed about ${freed} MB of cache", Toast.LENGTH_LONG).show()
            }
        })

        section(root, "DIAGNOSTICS")
        root.addView(action("Run health check") {
            viewLifecycleOwner.lifecycleScope.launch {
                val report = withContext(Dispatchers.IO) { Diagnostics.createReport(requireContext()) }
                if (isAdded) Toast.makeText(requireContext(), "Report: ${report.name}", Toast.LENGTH_LONG).show()
            }
        })
        root.addView(action("Share diagnostics on WhatsApp") {
            viewLifecycleOwner.lifecycleScope.launch {
                val report = withContext(Dispatchers.IO) { Diagnostics.createReport(requireContext()) }
                if (!isAdded) return@launch
                val uri = FileProvider.getUriForFile(requireContext(), "${BuildConfig.APPLICATION_ID}.files", report)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "MediaHub Pro diagnostic report for Lexington Grimy (254702678294).")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    setPackage("com.whatsapp")
                }
                try {
                    startActivity(send)
                } catch (_: Exception) {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TEXT, "Please send this MediaHub Pro diagnostic ZIP to 254702678294.")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Share diagnostic ZIP"))
                }
            }
        })
        root.addView(action("Clear saved crash report") {
            Diagnostics.clearLastCrash(requireContext())
            Toast.makeText(requireContext(), "Crash report cleared", Toast.LENGTH_SHORT).show()
        })

        section(root, "ABOUT")
        root.addView(TextView(requireContext()).apply {
            text = "MediaHub Pro is an offline-first media hub for music, videos and photos.\n\nUpdates download inside the app — you never need to visit GitHub.\n\nVersion ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
            setTextColor(0xFF8E7F93.toInt())
            textSize = 13f
            setPadding(0, dp(4), 0, 0)
            setLineSpacing(4f, 1f)
        })

        return scroll
    }

    private fun section(root: LinearLayout, title: String) {
        root.addView(TextView(requireContext()).apply {
            text = title
            setTextColor(0xFFFF5C8A.toInt())
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(16), 0, dp(9))
        })
    }

    private fun updateNotificationLabel(): String {
        val enabled = requireContext().getSharedPreferences("mediahub_settings", 0).getBoolean("update_notifications", true)
        return "Update notifications: ${if (enabled) "On" else "Off"}"
    }

    private fun action(title: String, click: (TextView) -> Unit) = TextView(requireContext()).apply {
        text = title
        setTextColor(0xFFFFF8FC.toInt())
        textSize = 14f
        setPadding(dp(16), dp(17), dp(16), dp(17))
        background = GradientDrawable().apply {
            setColor(0xFF16121F.toInt())
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), 0x28FFFFFF)
        }
        isClickable = true
        isFocusable = true
        setOnClickListener { click(this) }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
