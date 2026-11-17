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
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mediahub.rebuilt.BuildConfig
import com.mediahub.rebuilt.diagnostics.Diagnostics
import com.mediahub.rebuilt.updates.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {
    override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, savedInstanceState: Bundle?): android.view.View {
        val scroll = ScrollView(requireContext()).apply { setBackgroundColor(0xFF08060C.toInt()) }
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(34), dp(20), dp(112))
        }
        scroll.addView(root)
        root.addView(TextView(requireContext()).apply {
            text = "Settings"; setTextColor(0xFFFFF7FB.toInt()); textSize = 29f
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(requireContext()).apply {
            text = "MEDIAHUB PRO  ·  ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n\nManage library access, updates and app health."
            setTextColor(0xFFC9B8C8.toInt()); textSize = 14f; setPadding(0, dp(10), 0, dp(20))
        })
        section(root, "APP")
        root.addView(action("Manage media permissions") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", requireContext().packageName, null)
            })
        })
        root.addView(action("Manage notification permissions") {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            })
        })
        root.addView(action(updateNotificationLabel()) {
            val prefs = requireContext().getSharedPreferences("mediahub_settings", 0)
            val enabled = !prefs.getBoolean("update_notifications", true)
            prefs.edit().putBoolean("update_notifications", enabled).apply()
            it.text = updateNotificationLabel()
            Toast.makeText(requireContext(), if (enabled) "Update notifications enabled" else "Update notifications disabled", Toast.LENGTH_SHORT).show()
        })
        root.addView(action("Check for updates") {
            it.text = "Checking…"
            UpdateChecker.check(requireContext(), notify = true) { info ->
                if (!isAdded) return@check
                it.text = "Check for updates"
                if (info == null) {
                    Toast.makeText(requireContext(), "You are on the latest version.", Toast.LENGTH_LONG).show()
                    return@check
                }
                val canInstall = !info.apkUrl.isNullOrBlank()
                AlertDialog.Builder(requireContext())
                    .setTitle("Update ${info.version}")
                    .setMessage(
                        if (canInstall) "Download and install MediaHub Pro ${info.version} now?"
                        else "Open the release page to download MediaHub Pro ${info.version}?"
                    )
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton(if (canInstall) "Update now" else "Open") { _, _ ->
                        if (canInstall) {
                            Toast.makeText(requireContext(), "Downloading update…", Toast.LENGTH_SHORT).show()
                            viewLifecycleOwner.lifecycleScope.launch {
                                val err = UpdateChecker.downloadAndInstall(requireContext(), info.apkUrl!!)
                                if (err != null && isAdded) {
                                    Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show()
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseUrl)))
                                }
                            }
                        } else {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseUrl)))
                        }
                    }
                    .show()
            }
        })
        section(root, "DIAGNOSTICS")
        root.addView(action("Run app health check") {
            viewLifecycleOwner.lifecycleScope.launch {
                val report = withContext(Dispatchers.IO) { Diagnostics.createReport(requireContext()) }
                if (isAdded) Toast.makeText(requireContext(), "Health report created: ${report.name}", Toast.LENGTH_LONG).show()
            }
        })
        root.addView(action("Create ZIP and share with Lexington on WhatsApp") {
            viewLifecycleOwner.lifecycleScope.launch {
                val report = withContext(Dispatchers.IO) { Diagnostics.createReport(requireContext()) }
                if (!isAdded) return@launch
                val uri = FileProvider.getUriForFile(requireContext(), "${BuildConfig.APPLICATION_ID}.files", report)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "MediaHub Pro diagnostic report for Lexington Grimy (254702678294). Please review the attached ZIP.")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    setPackage("com.whatsapp")
                }
                try {
                    startActivity(send)
                } catch (_: Exception) {
                    val fallback = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TEXT, "Please send this MediaHub Pro diagnostic ZIP to Lexington Grimy (254702678294).")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(fallback, "Share diagnostic ZIP"))
                }
            }
        })
        root.addView(action("Clear saved crash report") {
            Diagnostics.clearLastCrash(requireContext())
            Toast.makeText(requireContext(), "Saved crash report cleared", Toast.LENGTH_SHORT).show()
        })
        root.addView(TextView(requireContext()).apply {
            text = "Updates are checked automatically on launch and once a day in the background. When a new version is published, you get a notification and can install from inside the app."
            setTextColor(0xFF8E7F93.toInt()); textSize = 12f; setPadding(0, dp(14), 0, 0)
        })
        return scroll
    }

    private fun section(root: LinearLayout, title: String) {
        root.addView(TextView(requireContext()).apply {
            text = title; setTextColor(0xFFFF5C8A.toInt()); textSize = 11f
            setTypeface(null, Typeface.BOLD); setPadding(0, dp(14), 0, dp(9))
        })
    }

    private fun updateNotificationLabel(): String {
        val enabled = requireContext().getSharedPreferences("mediahub_settings", 0).getBoolean("update_notifications", true)
        return "Update notifications: ${if (enabled) "On" else "Off"}"
    }

    private fun action(title: String, click: (TextView) -> Unit) = TextView(requireContext()).apply {
        text = title; setTextColor(0xFFFFF7FB.toInt()); textSize = 14f
        setPadding(dp(16), dp(17), dp(16), dp(17))
        background = GradientDrawable().apply {
            setColor(0xFF1A1422.toInt()); cornerRadius = dp(16).toFloat()
            setStroke(dp(1), 0x28FFFFFF)
        }
        isClickable = true; isFocusable = true; setOnClickListener { click(this) }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
