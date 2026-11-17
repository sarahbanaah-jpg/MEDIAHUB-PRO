package com.mediahub.rebuilt.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.mediahub.rebuilt.BuildConfig
import com.mediahub.rebuilt.diagnostics.Diagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {
    override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, savedInstanceState: Bundle?): android.view.View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF08080D.toInt())
            setPadding(dp(20), dp(32), dp(20), dp(100))
        }
        root.addView(TextView(requireContext()).apply {
            text = "Settings"; setTextColor(0xFFFFFFFF.toInt()); textSize = 28f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(requireContext()).apply {
            text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n\nPlayback and media permissions are managed by Android. Updates are installed only after you approve the downloaded package."
            setTextColor(0xFFBDB4C2.toInt()); textSize = 14f; setPadding(0, dp(12), 0, dp(24))
        })
        root.addView(action("Run diagnostics") {
            val report = Diagnostics.createReport(requireContext())
            android.widget.Toast.makeText(requireContext(), "Diagnostics complete: ${report.name}", android.widget.Toast.LENGTH_LONG).show()
        })
        root.addView(action("Create & share error report ZIP") {
            CoroutineScope(Dispatchers.IO).launch {
                val report = Diagnostics.createReport(requireContext())
                requireActivity().runOnUiThread {
                    val uri: Uri = FileProvider.getUriForFile(requireContext(), "${BuildConfig.APPLICATION_ID}.files", report)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TEXT, "MediaHub Pro diagnostic report. Please review the attached ZIP.")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(send, "Share diagnostic report (WhatsApp or another app)"))
                }
            }
        })
        root.addView(TextView(requireContext()).apply {
            text = "Reports contain app/device diagnostics and crash traces only. Review the ZIP before sharing. Nothing is sent automatically."
            setTextColor(0xFF8E8492.toInt()); textSize = 12f; setPadding(0, dp(14), 0, 0)
        })
        return root
    }
    private fun action(title: String, click: () -> Unit) = TextView(requireContext()).apply {
        text = title; setTextColor(0xFFFFFFFF.toInt()); textSize = 15f
        setPadding(dp(16), dp(18), dp(16), dp(18))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(0xFF211824.toInt()); cornerRadius = dp(16).toFloat()
            setStroke(dp(1), 0x66F06A9B)
        }
        isClickable = true; isFocusable = true; setOnClickListener { click() }
        val lp = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        layoutParams = lp
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
