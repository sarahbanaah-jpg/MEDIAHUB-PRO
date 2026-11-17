package com.mediahub.rebuilt.ui.videos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.Intent
import android.net.Uri
import android.widget.Button
import com.mediahub.rebuilt.ui.player.VideoPlayerActivity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mediahub.rebuilt.media.MediaRepository
import kotlinx.coroutines.launch

class VideosFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0A060C.toInt())
            setPadding(dp(20), dp(48), dp(20), dp(100))
        }
        val title = TextView(requireContext()).apply {
            text = "Videos"
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
        val demoButton = Button(requireContext()).apply {
            text = "Play included offline demo clip"
            setOnClickListener {
                startActivity(Intent(requireContext(), VideoPlayerActivity::class.java).apply {
                    data = Uri.parse("asset:///mediahub-demo.mp4")
                })
            }
        }
        root.addView(demoButton)
        viewLifecycleOwner.lifecycleScope.launch {
            val videos = MediaRepository.loadVideos(requireContext())
            list.text = if (videos.isEmpty()) "No videos found" else videos.take(30).joinToString("\n") { "• ${it.title}" }
        }
        return root
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
