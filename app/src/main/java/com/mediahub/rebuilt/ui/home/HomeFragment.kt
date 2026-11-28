package com.mediahub.rebuilt.ui.home

import android.content.Intent
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
