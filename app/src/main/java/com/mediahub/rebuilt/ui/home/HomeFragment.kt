package com.mediahub.rebuilt.ui.home

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mediahub.rebuilt.media.MediaRepository
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private lateinit var summary: TextView
    private lateinit var recent: LinearLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val scroll = ScrollView(requireContext()).apply { setBackgroundColor(0xFF09070D.toInt()); clipToPadding = false }
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(42), dp(20), dp(112))
        }
        scroll.addView(root)
        root.addView(TextView(requireContext()).apply {
            text = "MEDIAHUB"; setTextColor(0xFFFF7898.toInt()); textSize = 11f
            letterSpacing = 0.18f; setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(requireContext()).apply {
            text = "Your media.\nYour world."; setTextColor(0xFFFFFFFF.toInt()); textSize = 34f
            setTypeface(null, Typeface.BOLD); setPadding(0, dp(8), 0, dp(8))
        })
        root.addView(TextView(requireContext()).apply {
            text = "Everything you love, beautifully organised."; setTextColor(0xFFB9ADBD.toInt()); textSize = 14f
            setPadding(0, 0, 0, dp(22))
        })
        val hero = TextView(requireContext()).apply {
            text = "YOUR LIBRARY\nDiscover and play your favourite music, videos and photos."
            setTextColor(0xFFF9EFF5.toInt()); textSize = 15f; setPadding(dp(18), dp(20), dp(18), dp(20))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF51263F.toInt(), 0xFF211827.toInt(), 0xFF15121B.toInt())).apply { cornerRadius = dp(22).toFloat() }
        }
        root.addView(hero)
        summary = TextView(requireContext()).apply {
            text = "Scanning your library…"; setTextColor(0xFFFF7898.toInt()); textSize = 14f
            setPadding(0, dp(22), 0, dp(12))
        }
        root.addView(summary)
        recent = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        root.addView(recent)
        return scroll
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val context = requireContext()
            val videos = MediaRepository.loadVideos(context)
            val music = MediaRepository.loadMusic(context)
            val photos = MediaRepository.loadPhotos(context)
            summary.text = "${music.size} SONGS     ·     ${videos.size} VIDEOS     ·     ${photos.size} PHOTOS"
            recent.removeAllViews()
            recent.addView(TextView(context).apply {
                text = "Recently added"; setTextColor(0xFFFFFFFF.toInt()); textSize = 19f
                setTypeface(null, Typeface.BOLD); setPadding(0, dp(12), 0, dp(12))
            })
            val latest = (music + videos + photos).sortedByDescending { it.dateAdded }.take(6)
            if (latest.isEmpty()) {
                recent.addView(TextView(context).apply {
                    text = "Your library is ready. Add media to your device and grant access to see it here."
                    setTextColor(0xFFB9ADBD.toInt()); textSize = 14f
                })
            } else latest.forEach { item ->
                recent.addView(TextView(context).apply {
                    text = item.title; setTextColor(0xFFEFE6F0.toInt()); textSize = 14f
                    setPadding(dp(14), dp(13), dp(14), dp(13))
                    background = GradientDrawable().apply { setColor(0xFF17121C.toInt()); cornerRadius = dp(12).toFloat() }
                    val lp = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) }
                    layoutParams = lp
                })
            }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
