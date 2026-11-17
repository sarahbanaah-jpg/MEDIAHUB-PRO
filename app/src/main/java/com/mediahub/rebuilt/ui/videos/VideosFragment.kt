package com.mediahub.rebuilt.ui.videos

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mediahub.rebuilt.media.MediaRepository
import com.mediahub.rebuilt.ui.media.MediaGridAdapter
import com.mediahub.rebuilt.ui.player.VideoPlayerActivity
import kotlinx.coroutines.launch

class VideosFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF09070D.toInt())
            setPadding(dp(18), dp(34), dp(18), dp(100))
        }
        root.addView(TextView(requireContext()).apply {
            text = "Videos"; setTextColor(0xFFFFFFFF.toInt()); textSize = 29f
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(requireContext()).apply {
            text = "Your video library"; setTextColor(0xFFB9ADBD.toInt()); textSize = 14f
            setPadding(0, dp(5), 0, dp(16))
        })
        val count = TextView(requireContext()).apply {
            text = "Loading videos…"; setTextColor(0xFFFF7898.toInt()); textSize = 12f
            setPadding(0, 0, 0, dp(10))
        }
        root.addView(count)
        val empty = TextView(requireContext()).apply {
            text = "No videos found. Add videos to your device or allow video access in Android settings."
            setTextColor(0xFFB9ADBD.toInt()); textSize = 14f
            setPadding(dp(16), dp(22), dp(16), dp(22))
            background = GradientDrawable().apply { setColor(0xFF17121C.toInt()); cornerRadius = dp(18).toFloat() }
            visibility = View.GONE
        }
        root.addView(empty)
        val grid = RecyclerView(requireContext()).apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            setHasFixedSize(false)
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        root.addView(grid)
        viewLifecycleOwner.lifecycleScope.launch {
            val videos = MediaRepository.loadVideos(requireContext())
            count.text = "${videos.size} VIDEOS"
            empty.visibility = if (videos.isEmpty()) View.VISIBLE else View.GONE
            grid.visibility = if (videos.isEmpty()) View.GONE else View.VISIBLE
            grid.adapter = MediaGridAdapter(requireContext(), videos, true) { index ->
                val selected = videos[index]
                startActivity(Intent(requireContext(), VideoPlayerActivity::class.java).apply {
                    data = selected.uri
                    putExtra(VideoPlayerActivity.EXTRA_TITLE, selected.title)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            }
        }
        return root
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
