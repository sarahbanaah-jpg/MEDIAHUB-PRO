package com.mediahub.rebuilt.ui.photos

import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.mediahub.rebuilt.media.MediaRepository
import com.mediahub.rebuilt.media.MediaThumbnails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhotoGalleryActivity : AppCompatActivity() {
    private lateinit var pager: ViewPager2
    private lateinit var counter: TextView
    private lateinit var slideshowBtn: TextView
    private var slideshow = false
    private val handler = Handler(Looper.getMainLooper())
    private val advance = object : Runnable {
        override fun run() {
            if (!slideshow) return
            val count = pager.adapter?.itemCount ?: 0
            if (count <= 0) return
            val next = (pager.currentItem + 1) % count
            pager.setCurrentItem(next, true)
            handler.postDelayed(this, 3500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        pager = ViewPager2(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            setBackgroundColor(Color.BLACK)
            offscreenPageLimit = 1
        }
        root.addView(pager)

        counter = TextView(this).apply {
            setTextColor(0xCCFFFFFF.toInt())
            textSize = 13f
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = FrameLayout.LayoutParams(-2, -2).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
                topMargin = dp(36)
            }
        }
        root.addView(counter)

        slideshowBtn = TextView(this).apply {
            text = "Slideshow"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(dp(18), dp(12), dp(18), dp(12))
            setBackgroundColor(0x66FF5C8A.toInt())
            layoutParams = FrameLayout.LayoutParams(-2, -2).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(40)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { toggleSlideshow() }
        }
        root.addView(slideshowBtn)
        setContentView(root)

        val startIndex = intent.getIntExtra(EXTRA_INDEX, 0)
        lifecycleScope.launch {
            val uris = MediaRepository.loadPhotos(this@PhotoGalleryActivity).map { it.uri.toString() }
            if (uris.isEmpty()) {
                finish()
                return@launch
            }
            pager.adapter = PhotoAdapter(uris)
            pager.setCurrentItem(startIndex.coerceIn(0, uris.lastIndex), false)
            counter.text = "${startIndex.coerceIn(0, uris.lastIndex) + 1} / ${uris.size}"
            pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    counter.text = "${position + 1} / ${uris.size}"
                }
            })
        }
    }

    private fun toggleSlideshow() {
        slideshow = !slideshow
        slideshowBtn.text = if (slideshow) "Stop slideshow" else "Slideshow"
        handler.removeCallbacks(advance)
        if (slideshow) {
            Toast.makeText(this, "Slideshow started", Toast.LENGTH_SHORT).show()
            handler.postDelayed(advance, 3500)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(advance)
        super.onDestroy()
    }

    private inner class PhotoAdapter(private val uris: List<String>) : RecyclerView.Adapter<PhotoAdapter.Page>() {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        inner class Page(val container: FrameLayout, val image: ZoomImageView) : RecyclerView.ViewHolder(container) {
            var job: Job? = null
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Page {
            val container = FrameLayout(this@PhotoGalleryActivity).apply {
                layoutParams = ViewGroup.LayoutParams(-1, -1)
                setBackgroundColor(Color.BLACK)
            }
            val image = ZoomImageView(this@PhotoGalleryActivity).apply {
                layoutParams = FrameLayout.LayoutParams(-1, -1)
                scaleType = ImageView.ScaleType.MATRIX
                setBackgroundColor(Color.BLACK)
            }
            container.addView(image)
            return Page(container, image)
        }

        override fun onBindViewHolder(holder: Page, position: Int) {
            holder.job?.cancel()
            holder.image.tag = uris[position]
            holder.image.setImageDrawable(null)
            holder.image.resetZoom()
            holder.job = scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    MediaThumbnails.load(this@PhotoGalleryActivity, Uri.parse(uris[position]), 2000, 2000)
                }
                if (holder.image.tag == uris[position] && bitmap != null) {
                    holder.image.setImageBitmap(bitmap)
                    holder.image.fitCenter()
                }
            }
        }

        override fun onViewRecycled(holder: Page) {
            holder.job?.cancel()
            holder.image.tag = null
            holder.image.setImageDrawable(null)
        }

        override fun getItemCount() = uris.size
    }

    /** Pinch-to-zoom + pan ImageView */
    private class ZoomImageView(context: android.content.Context) : androidx.appcompat.widget.AppCompatImageView(context) {
        private val matrix = Matrix()
        private val scaleDetector: ScaleGestureDetector
        private var scale = 1f
        private var lastX = 0f
        private var lastY = 0f
        private var mode = 0 // 0 none, 1 drag, 2 zoom

        init {
            scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    scale = (scale * detector.scaleFactor).coerceIn(1f, 5f)
                    matrix.setScale(scale, scale, detector.focusX, detector.focusY)
                    imageMatrix = matrix
                    return true
                }
            })
            setOnTouchListener { _, event ->
                scaleDetector.onTouchEvent(event)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastX = event.x
                        lastY = event.y
                        mode = 1
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (mode == 1 && scale > 1.05f && event.pointerCount == 1) {
                            val dx = event.x - lastX
                            val dy = event.y - lastY
                            matrix.postTranslate(dx, dy)
                            imageMatrix = matrix
                            lastX = event.x
                            lastY = event.y
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> mode = 0
                }
                true
            }
        }

        fun resetZoom() {
            scale = 1f
            matrix.reset()
            imageMatrix = matrix
        }

        fun fitCenter() {
            val d = drawable ?: return
            if (width == 0 || height == 0) {
                post { fitCenter() }
                return
            }
            val scaleX = width.toFloat() / d.intrinsicWidth.coerceAtLeast(1)
            val scaleY = height.toFloat() / d.intrinsicHeight.coerceAtLeast(1)
            scale = minOf(scaleX, scaleY)
            matrix.setScale(scale, scale)
            val dx = (width - d.intrinsicWidth * scale) / 2f
            val dy = (height - d.intrinsicHeight * scale) / 2f
            matrix.postTranslate(dx, dy)
            imageMatrix = matrix
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_INDEX = "com.mediahub.rebuilt.photos.INDEX"
    }
}
