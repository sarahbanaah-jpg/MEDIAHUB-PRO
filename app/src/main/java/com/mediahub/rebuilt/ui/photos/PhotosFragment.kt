package com.mediahub.rebuilt.ui.photos

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

class PhotosFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0A060C.toInt())
            setPadding(dp(20), dp(48), dp(20), dp(100))
        }
        val title = TextView(requireContext()).apply {
            text = "Photos"
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
        lifecycleScope.launch {
            val photos = MediaRepository.loadPhotos(requireContext())
            list.text = if (photos.isEmpty()) "No photos found" else "${photos.size} photos found\n\n" + photos.take(20).joinToString("\n") { "• ${it.title}" }
        }
        return root
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
