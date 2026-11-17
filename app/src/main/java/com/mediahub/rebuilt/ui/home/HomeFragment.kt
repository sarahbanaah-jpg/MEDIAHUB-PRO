package com.mediahub.rebuilt.ui.home

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

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0A060C.toInt())
            setPadding(dp(20), dp(48), dp(20), dp(100))
        }

        val title = TextView(requireContext()).apply {
            text = "MediaHub Pro"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 28f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        root.addView(title)

        val subtitle = TextView(requireContext()).apply {
            text = "Your premium media centre"
            setTextColor(0xFFB0A0B0.toInt())
            textSize = 14f
            setPadding(0, dp(4), 0, dp(24))
        }
        root.addView(subtitle)

        val status = TextView(requireContext()).apply {
            text = "Loading your library…"
            setTextColor(0xFFC8B8C8.toInt())
            textSize = 15f
        }
        root.addView(status)

        lifecycleScope.launch {
            val videos = MediaRepository.loadVideos(requireContext())
            val music = MediaRepository.loadMusic(requireContext())
            val photos = MediaRepository.loadPhotos(requireContext())
            status.text = "${videos.size} videos  ·  ${music.size} songs  ·  ${photos.size} photos"
        }

        return root
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
