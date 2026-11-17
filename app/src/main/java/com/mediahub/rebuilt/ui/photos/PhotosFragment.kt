package com.mediahub.rebuilt.ui.photos

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
import kotlinx.coroutines.launch

class PhotosFragment : Fragment() {
    private lateinit var count: TextView
    private lateinit var empty: TextView
    private lateinit var grid: RecyclerView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF09070D.toInt())
            setPadding(dp(18), dp(34), dp(18), dp(100))
        }
        root.addView(TextView(requireContext()).apply {
            text = "Photos"; setTextColor(0xFFFFFFFF.toInt()); textSize = 29f
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(requireContext()).apply {
            text = "Your moments, all in one place"; setTextColor(0xFFB9ADBD.toInt()); textSize = 14f
            setPadding(0, dp(5), 0, dp(16))
        })
        count = TextView(requireContext()).apply {
            text = "Loading photos…"; setTextColor(0xFFFF7898.toInt()); textSize = 12f
            setPadding(0, 0, 0, dp(10))
        }
        root.addView(count)
        empty = TextView(requireContext()).apply {
            text = "No photos found. Add images to your device or allow photo access in Android settings."
            setTextColor(0xFFB9ADBD.toInt()); textSize = 14f
            setPadding(dp(16), dp(22), dp(16), dp(22))
            background = GradientDrawable().apply { setColor(0xFF17121C.toInt()); cornerRadius = dp(18).toFloat() }
            visibility = View.GONE
        }
        root.addView(empty)
        grid = RecyclerView(requireContext()).apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            setHasFixedSize(false)
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        root.addView(grid)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val photos = MediaRepository.loadPhotos(requireContext())
            count.text = "${photos.size} PHOTOS"
            empty.visibility = if (photos.isEmpty()) View.VISIBLE else View.GONE
            grid.visibility = if (photos.isEmpty()) View.GONE else View.VISIBLE
            grid.adapter = MediaGridAdapter(requireContext(), photos, false) { index ->
                startActivity(Intent(requireContext(), PhotoGalleryActivity::class.java).apply {
                    putExtra(PhotoGalleryActivity.EXTRA_INDEX, index)
                })
            }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
