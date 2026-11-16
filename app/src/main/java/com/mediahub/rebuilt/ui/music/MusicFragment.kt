package com.mediahub.rebuilt.ui.music

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mediahub.rebuilt.media.MediaRepository
import kotlinx.coroutines.launch

class MusicFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0A060C.toInt())
            setPadding(dp(20), dp(48), dp(20), dp(100))
        }
        val title = TextView(requireContext()).apply {
            text = "Music"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        root.addView(title)
        val list = TextView(requireContext()).apply {
            setTextColor(0xFFC8B8C8.toInt())
            textSize = 14f
            setPadding(0, dp(16), 0, 0)
        }
        root.addView(list)
        viewLifecycleOwner.lifecycleScope.launch {
            val songs = MediaRepository.loadMusic(requireContext())
            list.text = if (songs.isEmpty()) "No music found" else songs.take(30).joinToString("\n") {
                "• ${it.title}" + if (it.artist.isNotBlank()) " — ${it.artist}" else ""
            }
        }
        return root
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
