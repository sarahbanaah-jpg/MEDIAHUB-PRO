package com.mediahub.rebuilt.ui.photos

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mediahub.rebuilt.media.MediaItem
import com.mediahub.rebuilt.media.MediaRepository
import com.mediahub.rebuilt.ui.media.MediaGridAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhotosFragment : Fragment() {
    private lateinit var count: TextView
    private lateinit var empty: TextView
    private lateinit var grid: RecyclerView
    private lateinit var search: EditText
    private var allPhotos: List<MediaItem> = emptyList()
    private var newestFirst = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF08080D.toInt())
            setPadding(dp(18), dp(34), dp(18), dp(100))
        }
        root.addView(TextView(requireContext()).apply {
            text = "Your moments"; setTextColor(0xFFFFFFFF.toInt()); textSize = 29f
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(requireContext()).apply {
            text = "A beautiful home for every photo and memory"
            setTextColor(0xFFB9ADBD.toInt()); textSize = 13f
            setPadding(0, dp(5), 0, dp(16))
        })
        val searchBox = LinearLayout(requireContext()).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(3), dp(14), dp(3))
            background = GradientDrawable().apply {
                setColor(0xFF17151E.toInt()); cornerRadius = dp(16).toFloat()
                setStroke(dp(1), 0x33FFFFFF)
            }
        }
        search = EditText(requireContext()).apply {
            hint = "Search photos"
            setHintTextColor(0xFF827D8A.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            setSingleLine(true)
            background = null
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
        }
        searchBox.addView(TextView(requireContext()).apply {
            text = "⌕"; textSize = 22f; setTextColor(0xFFFF79A8.toInt())
            setPadding(0, 0, dp(8), 0)
        })
        searchBox.addView(search)
        root.addView(searchBox)
        val toolbar = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, dp(12))
        }
        count = TextView(requireContext()).apply {
            text = "Loading photos…"; setTextColor(0xFFFF8DB4.toInt()); textSize = 12f
            letterSpacing = .08f
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        toolbar.addView(count)
        toolbar.addView(TextView(requireContext()).apply {
            text = "SORT: NEWEST ↓"; textSize = 11f; setTextColor(0xFFFFB1C8.toInt())
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = GradientDrawable().apply { setColor(0xFF201B27.toInt()); cornerRadius = dp(12).toFloat() }
            isClickable = true; isFocusable = true
            setOnClickListener {
                newestFirst = !newestFirst
                text = if (newestFirst) "SORT: NEWEST ↓" else "SORT: OLDEST ↑"
                renderPhotos()
            }
        })
        root.addView(toolbar)
        empty = TextView(requireContext()).apply {
            text = "No photos match your search. Try another name or add images to your device."
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
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderPhotos() }
            override fun afterTextChanged(s: Editable?) {}
        })
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        count.text = "SCANNING YOUR LIBRARY…"
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                allPhotos = withContext(Dispatchers.IO) { MediaRepository.loadPhotos(requireContext()) }
                renderPhotos()
            } catch (error: Exception) {
                count.text = "PHOTO LIBRARY UNAVAILABLE"
                empty.text = "Could not load photos: ${error.localizedMessage ?: "check media permissions"}"
                empty.visibility = View.VISIBLE
            }
        }
    }

    private fun renderPhotos() {
        if (!isAdded || !::grid.isInitialized) return
        val query = search.text?.toString()?.trim().orEmpty()
        val filtered = allPhotos.filter { query.isBlank() || it.title.contains(query, true) }
            .sortedBy { it.dateAdded }
            .let { if (newestFirst) it.reversed() else it }
        count.text = "${filtered.size} OF ${allPhotos.size} PHOTOS"
        empty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        grid.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        grid.adapter = MediaGridAdapter(requireContext(), filtered, false) { index ->
            startActivity(Intent(requireContext(), PhotoGalleryActivity::class.java).apply {
                putExtra(PhotoGalleryActivity.EXTRA_INDEX, allPhotos.indexOf(filtered[index]).coerceAtLeast(0))
            })
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
