package com.mediahub.rebuilt.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.EditText
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mediahub.rebuilt.BuildConfig
import com.mediahub.rebuilt.diagnostics.Diagnostics
import com.mediahub.rebuilt.media.MediaLibraryTools
import com.mediahub.rebuilt.performance.DevicePerformance
import com.mediahub.rebuilt.ui.MainActivity
import com.mediahub.rebuilt.updates.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {
    override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, savedInstanceState: Bundle?): android.view.View {
        val scroll = ScrollView(requireContext()).apply { setBackgroundColor(0xFF0B0618.toInt()) }
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(34), dp(20), dp(112))
        }
        scroll.addView(root)

        root.addView(TextView(requireContext()).apply {
            text = "Settings"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 29f
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(requireContext()).apply {
            text = "MEDIAHUB PRO  ·  v${BuildConfig.VERSION_NAME}"
            setTextColor(0xFFD4C4E8.toInt())
            textSize = 14f
            setPadding(0, dp(8), 0, dp(18))
        })

        section(root, "UPDATES")
        root.addView(action(updateNotificationLabel()) {
            val prefs = requireContext().getSharedPreferences("mediahub_settings", 0)
            val enabled = !prefs.getBoolean("update_notifications", true)
            prefs.edit().putBoolean("update_notifications", enabled).apply()
            it.text = updateNotificationLabel()
        })
        root.addView(action("Check for updates") {
            it.text = "Checking…"
            UpdateChecker.check(requireContext(), notify = true) { info ->
                if (!isAdded) return@check
                it.text = "Check for updates"
                if (info == null) Toast.makeText(requireContext(), "You are on the latest version.", Toast.LENGTH_LONG).show()
                else UpdateChecker.openUpdateScreen(requireContext(), info)
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
                if (isAdded) Toast.makeText(requireContext(), "RAM free ${snap.availableRamMb} MB · Storage free ${snap.freeStorageMb} MB", Toast.LENGTH_LONG).show()
            }
        })
        root.addView(action("Clear safe app cache") {
            viewLifecycleOwner.lifecycleScope.launch {
                val freed = withContext(Dispatchers.IO) { DevicePerformance.clearSafeCache(requireContext()) }
                if (isAdded) Toast.makeText(requireContext(), "Freed about ${freed} MB", Toast.LENGTH_LONG).show()
            }
        })

        section(root, "DIAGNOSTICS")
        root.addView(action("Share diagnostics on WhatsApp") {
            viewLifecycleOwner.lifecycleScope.launch {
                val report = withContext(Dispatchers.IO) { Diagnostics.createReport(requireContext()) }
                if (!isAdded) return@launch
                val uri = FileProvider.getUriForFile(requireContext(), "${BuildConfig.APPLICATION_ID}.files", report)
                try {
                    startActivity(Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TEXT, "MediaHub Pro diagnostic report for Grimy (+254702678294).")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        setPackage("com.whatsapp")
                    })
                } catch (_: Exception) {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Share diagnostic ZIP"))
                }
            }
        })

        section(root, "DYNAMIC ISLAND")
        root.addView(action("Configure Dynamic Island mode") {
            val modes = arrayOf("Smart", "Music", "Video", "Charging", "Notifications")
            val keys = arrayOf("smart", "music", "video", "charging", "notifications")
            val prefs = requireContext().getSharedPreferences("mediahub_settings", 0)
            val selected = keys.indexOf(prefs.getString("island_mode", "smart")).coerceAtLeast(0)
            AlertDialog.Builder(requireContext())
                .setTitle("Dynamic Island mode")
                .setSingleChoiceItems(modes, selected) { dialog, which ->
                    prefs.edit().putString("island_mode", keys[which]).apply()
                    (activity as? MainActivity)?.refreshDynamicIsland()
                    dialog.dismiss()
                    Toast.makeText(requireContext(), "Dynamic Island mode: ${modes[which]}", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        })
        root.addView(action("Grant notification access for Dynamic Island") {
            try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            catch (_: Exception) { Toast.makeText(requireContext(), "Open Android Special app access settings to enable notification access.", Toast.LENGTH_LONG).show() }
        })
        root.addView(action("Dynamic Island: enable / disable") {
            val prefs = requireContext().getSharedPreferences("mediahub_settings", 0)
            val enabled = !prefs.getBoolean("island_enabled", true)
            prefs.edit().putBoolean("island_enabled", enabled).apply()
            (activity as? MainActivity)?.refreshDynamicIsland()
            it.text = "Dynamic Island: ${if (enabled) "enabled" else "disabled"}"
            Toast.makeText(requireContext(), "Reopen Home to refresh the island.", Toast.LENGTH_SHORT).show()
        })
        root.addView(action("Dynamic Island: Liquid Glass") {
            val prefs = requireContext().getSharedPreferences("mediahub_settings", 0)
            val enabled = !prefs.getBoolean("island_glass", true)
            prefs.edit().putBoolean("island_glass", enabled).apply()
            (activity as? MainActivity)?.refreshDynamicIsland()
            Toast.makeText(requireContext(), "Liquid Glass ${if (enabled) "enabled" else "disabled"} for Dynamic Island.", Toast.LENGTH_SHORT).show()
        })
        root.addView(action("Resize Dynamic Island") {
            val prefs = requireContext().getSharedPreferences("mediahub_settings", 0)
            val seek = SeekBar(requireContext()).apply {
                max = 160
                progress = (prefs.getInt("island_width_dp", 190) - 140).coerceIn(0, 160)
            }
            val box = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(12), dp(20), 0)
                addView(TextView(requireContext()).apply {
                    text = "Width: ${seek.progress + 140} dp"
                    setTextColor(0xFFFFFFFF.toInt())
                })
                addView(seek)
            }
            val value = box.getChildAt(0) as TextView
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    value.text = "Width: ${progress + 140} dp"
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
            AlertDialog.Builder(requireContext()).setTitle("Dynamic Island size").setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save") { _, _ ->
                    prefs.edit().putInt("island_width_dp", seek.progress + 140).apply()
                    (activity as? MainActivity)?.refreshDynamicIsland()
                    Toast.makeText(requireContext(), "Dynamic Island size saved.", Toast.LENGTH_SHORT).show()
                }.show()
        })

        root.addView(action("Adjust Dynamic Island camera position") {
            val prefs = requireContext().getSharedPreferences("mediahub_settings", 0)
            val horizontal = SeekBar(requireContext()).apply { max = 240; progress = prefs.getInt("island_offset_x_dp", 0).coerceIn(-120, 120) + 120 }
            val vertical = SeekBar(requireContext()).apply { max = 140; progress = prefs.getInt("island_offset_y_dp", 0).coerceIn(-40, 100) + 40 }
            val horizontalLabel = TextView(requireContext()).apply { setTextColor(0xFFFFFFFF.toInt()) }
            val verticalLabel = TextView(requireContext()).apply { setTextColor(0xFFFFFFFF.toInt()) }
            fun updateLabels() {
                horizontalLabel.text = "Horizontal: ${horizontal.progress - 120} dp"
                verticalLabel.text = "Vertical: ${vertical.progress - 40} dp"
            }
            val box = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(12), dp(20), 0)
                addView(horizontalLabel); addView(horizontal); addView(verticalLabel); addView(vertical)
            }
            updateLabels()
            val listener = object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) = updateLabels()
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            }
            horizontal.setOnSeekBarChangeListener(listener)
            vertical.setOnSeekBarChangeListener(listener)
            AlertDialog.Builder(requireContext()).setTitle("Camera cutout alignment").setMessage("The island automatically centres over the camera cutout when Android exposes its position. Fine-tune the placement below.")
                .setView(box).setNegativeButton("Cancel", null)
                .setNeutralButton("Reset") { _, _ ->
                    prefs.edit().putInt("island_offset_x_dp", 0).putInt("island_offset_y_dp", 0).apply()
                    (activity as? MainActivity)?.refreshDynamicIsland()
                }
                .setPositiveButton("Save") { _, _ ->
                    prefs.edit().putInt("island_offset_x_dp", horizontal.progress - 120).putInt("island_offset_y_dp", vertical.progress - 40).apply()
                    (activity as? MainActivity)?.refreshDynamicIsland()
                    Toast.makeText(requireContext(), "Dynamic Island position saved.", Toast.LENGTH_SHORT).show()
                }.show()
        })
        section(root, "OFFLINE MEDIA TRANSFER")
        root.addView(action("Send or receive files with Quick Share") {
            startActivity(Intent(requireContext(), com.mediahub.rebuilt.transfer.MediaHubTransferActivity::class.java))
        })

        section(root, "MEDIAHUB STUDIO")
        root.addView(action("Open video trim and export studio") {
            startActivity(Intent(requireContext(), com.mediahub.rebuilt.studio.MediaHubStudioActivity::class.java))
        })

        section(root, "MEDIAHUB REMOTE")
        root.addView(action("Pair another device on this Wi-Fi") {
            startActivity(Intent(requireContext(), com.mediahub.rebuilt.remote.MediaHubRemoteActivity::class.java))
        })

        section(root, "INTELLIGENCE")
        root.addView(action("Analyse my media library") {
            startActivity(Intent(requireContext(), com.mediahub.rebuilt.intelligence.MediaHubIntelligenceActivity::class.java))
        })

        section(root, "AMBIENT DISPLAY")
        root.addView(action("Launch charging clock") {
            startActivity(Intent(requireContext(), com.mediahub.rebuilt.ambient.AmbientDisplayActivity::class.java))
        })

        section(root, "SECURITY")
        root.addView(action("Open Private Vault") {
            startActivity(Intent(requireContext(), com.mediahub.rebuilt.vault.PrivateVaultActivity::class.java))
        })

        section(root, "LOCAL MEDIA LIBRARY")
        root.addView(action("View storage breakdown") {
            it.text = "Reading media library…"
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val snapshot = withContext(Dispatchers.IO) { MediaLibraryTools.storageSnapshot(requireContext()) }
                    if (!isAdded) return@launch
                    it.text = "View storage breakdown"
                    val message = "Device used: ${MediaLibraryTools.formatBytes(snapshot.usedBytes)} of ${MediaLibraryTools.formatBytes(snapshot.totalBytes)}\\n\\n" +
                        "Music: ${MediaLibraryTools.formatBytes(snapshot.audioBytes)} (${snapshot.audioCount} files)\\n" +
                        "Videos: ${MediaLibraryTools.formatBytes(snapshot.videoBytes)} (${snapshot.videoCount} files)\\n" +
                        "Photos: ${MediaLibraryTools.formatBytes(snapshot.imageBytes)} (${snapshot.imageCount} files)\\n\\n" +
                        "Free: ${MediaLibraryTools.formatBytes(snapshot.freeBytes)}"
                    AlertDialog.Builder(requireContext()).setTitle("Storage overview").setMessage(message).setPositiveButton("OK", null).show()
                } catch (error: Exception) {
                    if (isAdded) {
                        it.text = "View storage breakdown"
                        Toast.makeText(requireContext(), "Unable to read media storage: ${error.localizedMessage ?: "unknown error"}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
        root.addView(action("Search local media by filename") {
            val input = EditText(requireContext()).apply {
                hint = "Enter a song, video or photo name"
                inputType = InputType.TYPE_CLASS_TEXT
                setSingleLine(true)
            }
            AlertDialog.Builder(requireContext())
                .setTitle("Search this device")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Search") { _, _ ->
                    val term = input.text?.toString().orEmpty()
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            val results = withContext(Dispatchers.IO) { MediaLibraryTools.search(requireContext(), term) }
                            if (!isAdded) return@launch
                            val labels = results.map { "${it.displayName}  ·  ${MediaLibraryTools.formatBytes(it.sizeBytes)}" }
                            AlertDialog.Builder(requireContext())
                                .setTitle("Search results (${results.size})")
                                .setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)) { _, which ->
                                    val selected = results[which]
                                    try {
                                        startActivity(Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(selected.contentUri, selected.mimeType ?: "*/*")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        })
                                    } catch (_: Exception) {
                                        Toast.makeText(requireContext(), "No compatible app can open this file.", Toast.LENGTH_LONG).show()
                                    }
                                }
                                .setPositiveButton("Close", null)
                                .show()
                        } catch (error: Exception) {
                            if (isAdded) Toast.makeText(requireContext(), "Search failed: ${error.localizedMessage ?: "unknown error"}", Toast.LENGTH_LONG).show()
                        }
                    }
                }.show()
        })

        section(root, "ABOUT")
        root.addView(TextView(requireContext()).apply {
            text = "MediaHub Pro\nOffline music, videos and photos.\n\n© Grimy. All rights reserved.\nUnauthorized copying or redistribution is prohibited.\n\nVersion ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
            setTextColor(0xFF9A8BB0.toInt())
            textSize = 13f
            setLineSpacing(4f, 1f)
            gravity = Gravity.START
        })

        return scroll
    }

    private fun section(root: LinearLayout, title: String) {
        root.addView(TextView(requireContext()).apply {
            text = title
            setTextColor(0xFFFF2D95.toInt())
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
        setTextColor(0xFFFFFFFF.toInt())
        textSize = 14f
        setPadding(dp(16), dp(17), dp(16), dp(17))
        background = GradientDrawable().apply {
            setColor(0xFF160E28.toInt())
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
