package com.mediahub.rebuilt.ui.photos

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        pager = ViewPager2(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(pager)
        val startIndex = intent.getIntExtra(EXTRA_INDEX, 0)
        lifecycleScope.launch {
            val uris = MediaRepository.loadPhotos(this@PhotoGalleryActivity).map { it.uri.toString() }
            if (uris.isEmpty()) {
                finish()
                return@launch
            }
            pager.adapter = PhotoAdapter(uris)
            pager.setCurrentItem(startIndex.coerceIn(0, uris.lastIndex), false)
        }
    }

    private inner class PhotoAdapter(private val uris: List<String>) : RecyclerView.Adapter<PhotoAdapter.Page>() {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        inner class Page(val image: ImageView) : RecyclerView.ViewHolder(image) { var job: Job? = null }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Page {
            val image = ImageView(this@PhotoGalleryActivity).apply {
                layoutParams = ViewGroup.LayoutParams(-1, -1)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(Color.BLACK)
            }
            return Page(image)
        }
        override fun onBindViewHolder(holder: Page, position: Int) {
            holder.job?.cancel()
            holder.image.tag = uris[position]
            holder.image.setImageDrawable(null)
            holder.job = scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    MediaThumbnails.load(this@PhotoGalleryActivity, Uri.parse(uris[position]), 1800, 1800)
                }
                if (holder.image.tag == uris[position] && bitmap != null) holder.image.setImageBitmap(bitmap)
            }
        }
        override fun onViewRecycled(holder: Page) {
            holder.job?.cancel()
            holder.image.tag = null
            holder.image.setImageDrawable(null)
        }
        override fun getItemCount() = uris.size
    }

    companion object {
        const val EXTRA_INDEX = "com.mediahub.rebuilt.photos.INDEX"
    }
}
