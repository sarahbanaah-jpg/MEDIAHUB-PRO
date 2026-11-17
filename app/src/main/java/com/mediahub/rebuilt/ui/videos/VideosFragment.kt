package com.mediahub.rebuilt.ui.videos

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mediahub.rebuilt.media.MediaRepository
import com.mediahub.rebuilt.ui.player.VideoPlayerActivity
import kotlinx.coroutines.launch

class VideosFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF08080D.toInt())
            setPadding(dp(20), dp(32), dp(20), dp(100))
        }
        root.addView(TextView(requireContext()).apply {
            text = "Videos"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 28f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(requireContext()).apply {
            text = "Your local video library"
            setTextColor(0xFFB7AEBB.toInt())
            textSize = 14f
            setPadding(0, dp(4), 0, dp(18))
        })
        val list = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)
        viewLifecycleOwner.lifecycleScope.launch {
            val videos = MediaRepository.loadVideos(requireContext())
            list.removeAllViews()
            if (videos.isEmpty()) {
                list.addView(label("No videos found. Allow media access and try again."))
            } else {
                videos.forEach { item ->
                    val row = TextView(requireContext()).apply {
                        text = "▶   ${item.title}\n      ${formatDuration(item.durationMs)}"
                        setTextColor(0xFFF5EDF7.toInt())
                        textSize = 15f
                        setPadding(dp(14), dp(16), dp(14), dp(16))
                        background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(0xFF19151E.toInt())
                            cornerRadius = dp(16).toFloat()
                            setStroke(dp(1), 0x33FFFFFF)
                        }
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            startActivity(Intent(requireContext(), VideoPlayerActivity::class.java).apply {
                                data = item.uri
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            })
                        }
                    }
                    val params = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
                    list.addView(row, params)
                }
            }
        }
        return root
    }

    private fun label(value: String) = TextView(requireContext()).apply {
        text = value; setTextColor(0xFFB7AEBB.toInt()); textSize = 14f; setPadding(0, dp(20), 0, 0)
    }
    private fun formatDuration(ms: Long): String = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
