package com.mediahub.rebuilt.ui.home

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.mediahub.rebuilt.BuildConfig
import com.mediahub.rebuilt.keyboard.GrimyBoardSettingsActivity
import com.mediahub.rebuilt.updates.UpdateChecker

/**
 * Restricted mode: only GRIMY BOARD and the app updater are exposed from the home screen.
 * Other MediaHub modules remain installed but are not navigable in this mode.
 */
class HomeFragment : Fragment() {
    private lateinit var updateStatus: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val scroll = ScrollView(context).apply {
            setBackgroundColor(0xFF080607.toInt())
            isFillViewport = true
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(54), dp(22), dp(36))
        }
        scroll.addView(root)

        root.addView(TextView(context).apply {
            text = "MEDIAHUB-PRO"
            setTextColor(0xFFFF3789.toInt())
            textSize = 12f
            letterSpacing = 0.22f
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(context).apply {
            text = "Focused Mode"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(8))
        })
        root.addView(TextView(context).apply {
            text = "Only GRIMY BOARD and app updates are available in this release."
            setTextColor(0xFFB8AAB1.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(28))
        })

        root.addView(actionCard(
            "⌨",
            "GRIMY BOARD",
            "Open keyboard themes, typing tools, fonts and Android keyboard activation"
        ) {
            startActivity(Intent(requireContext(), GrimyBoardSettingsActivity::class.java))
        })

        root.addView(actionCard(
            "↻",
            "APP UPDATE",
            "Check for the latest MEDIAHUB-PRO release and install updates"
        ) { checkForUpdates() })

        root.addView(TextView(context).apply {
            text = "WHY THE OTHER FEATURES ARE RESTRICTED"
            setTextColor(0xFFFF6BA6.toInt())
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(8))
        })
        root.addView(TextView(context).apply {
            text = "Our usage review showed that GRIMY BOARD was the feature people used most, while music and the other MediaHub features received limited interest. We have therefore focused this release on the keyboard and app updates. The other features are restricted for now, not permanently ruled out. If you would like us to bring any of them back, send us a request."
            setTextColor(0xFFD0C3CB.toInt())
            textSize = 13f
            gravity = Gravity.CENTER
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(0, 0, 0, dp(12))
        })
        root.addView(actionCard(
            "✉",
            "REQUEST A FEATURE",
            "Email the developer. A device-specification report will be prepared for you to review."
        ) { requestFeatureRestoration() })

        updateStatus = TextView(context).apply {
            text = "Installed version · v${BuildConfig.VERSION_NAME}"
            setTextColor(0xFFB8AAB1.toInt())
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(12))
        }
        root.addView(updateStatus)
        root.addView(TextView(context).apply {
            text = "RESTRICTED MODE"
            setTextColor(0xFFFF3789.toInt())
            textSize = 10f
            letterSpacing = 0.18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, 0)
        })
        return scroll
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkForUpdates()
    }

    private fun checkForUpdates() {
        if (!isAdded) return
        updateStatus.text = "Checking GitHub for updates…"
        UpdateChecker.check(
            requireContext(),
            notify = false,
            onError = { message ->
                if (isAdded) updateStatus.text = "Update check failed · ${message.take(100)}"
            },
            callback = { info ->
                if (!isAdded) return@check
                if (info == null) {
                    updateStatus.text = "You're up to date · v${BuildConfig.VERSION_NAME}"
                } else {
                    updateStatus.text = "Version ${info.version} available · Tap APP UPDATE to install"
                    updateStatus.setOnClickListener { UpdateChecker.openUpdateScreen(requireContext(), info) }
                }
            }
        )
    }

    private fun requestFeatureRestoration() {
        val context = requireContext()
        val metrics = context.resources.displayMetrics
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val storage = StatFs(Environment.getDataDirectory().path)
        val totalStorage = storage.totalBytes
        val freeStorage = storage.availableBytes
        val totalMemory = memory.totalMem
        val deviceReport = buildString {
            appendLine("MEDIAHUB-PRO feature restoration request")
            appendLine()
            appendLine("Requested feature(s): [Please describe the feature(s) you want restored]")
            appendLine("Message: [Add your comments here]")
            appendLine()
            appendLine("Device specifications (available to the app):")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Device: ${Build.DEVICE}")
            appendLine("Product: ${Build.PRODUCT}")
            appendLine("Android version: ${Build.VERSION.RELEASE}")
            appendLine("Android SDK: ${Build.VERSION.SDK_INT}")
            if (Build.VERSION.SDK_INT >= 23) appendLine("Android security patch: ${Build.VERSION.SECURITY_PATCH}")
            appendLine("Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Screen resolution: ${metrics.widthPixels} × ${metrics.heightPixels} px")
            appendLine("Screen density: ${metrics.densityDpi} dpi")
            appendLine("Total RAM: ${Formatter.formatFileSize(context, totalMemory)}")
            appendLine("Total internal storage: ${Formatter.formatFileSize(context, totalStorage)}")
            appendLine("Available internal storage: ${Formatter.formatFileSize(context, freeStorage)}")
            appendLine("MEDIAHUB-PRO version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine()
            appendLine("Privacy: This report is prepared on this device and is only shared if you choose to send this email. It does not include an IMEI, serial number, contacts, or message contents.")
        }
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:leonelsquare7@gmail.com")
            putExtra(Intent.EXTRA_SUBJECT, "MEDIAHUB-PRO — feature restoration request")
            putExtra(Intent.EXTRA_TEXT, deviceReport)
        }
        try {
            startActivity(emailIntent)
        } catch (_: Exception) {
            Toast.makeText(context, "No email app is available on this device.", Toast.LENGTH_LONG).show()
        }
    }

    private fun actionCard(icon: String, title: String, subtitle: String, action: () -> Unit): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(22), dp(24), dp(22), dp(24))
            background = GradientDrawable().apply {
                setColor(0xFF171016.toInt())
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), 0x99FF3789.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) }
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
            addView(TextView(requireContext()).apply {
                text = icon
                textSize = 30f
                setTextColor(0xFFFF6BA6.toInt())
                gravity = Gravity.CENTER
            })
            addView(TextView(requireContext()).apply {
                text = title
                textSize = 17f
                setTextColor(0xFFFFFFFF.toInt())
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, dp(5))
            })
            addView(TextView(requireContext()).apply {
                text = subtitle
                textSize = 12f
                setTextColor(0xFFB8AAB1.toInt())
                gravity = Gravity.CENTER
            })
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
