package com.mediahub.rebuilt.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.mediahub.rebuilt.BuildConfig

class SettingsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0A060C.toInt())
            setPadding(dp(20), dp(48), dp(20), dp(100))
        }
        val title = TextView(requireContext()).apply {
            text = "Settings"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        root.addView(title)

        val version = TextView(requireContext()).apply {
            text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
            setTextColor(0xFFB0A0B0.toInt())
            textSize = 14f
            setPadding(0, dp(16), 0, 0)
        }
        root.addView(version)

        val note = TextView(requireContext()).apply {
            text = "\nPlayback, appearance, notifications and update settings will appear here in the next phase."
            setTextColor(0xFFC8B8C8.toInt())
            textSize = 14f
        }
        root.addView(note)

        return root
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
