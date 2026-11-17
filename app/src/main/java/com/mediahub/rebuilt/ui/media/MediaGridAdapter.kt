package com.mediahub.rebuilt.ui.media

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mediahub.rebuilt.media.MediaItem
import com.mediahub.rebuilt.media.MediaThumbnails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Premium media grid cards with soft glass borders and rounded thumbnails.
 */
class MediaGridAdapter(
    private val context: Context,
    private val items: List<MediaItem>,
    private val videos: Boolean,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<MediaGridAdapter.Holder>() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    class Holder(val card: LinearLayout, val image: ImageView, val title: TextView) : RecyclerView.ViewHolder(card) {
        var job: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val image = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(if (videos) 118 else 122))
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                setColor(0xFF241D29.toInt())
                cornerRadius = dp(14).toFloat()
            }
            clipToOutline = true
        }

        val title = TextView(context).apply {
            setTextColor(0xFFF5EDF7.toInt())
            textSize = 12.5f
            maxLines = 2
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(4), dp(8), dp(4), dp(2))
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(7), dp(7), dp(7), dp(10))
            background = GradientDrawable().apply {
                setColor(0xFF1A1422.toInt())
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), 0x28FFFFFF)
            }
            elevation = 4f * density
            addView(image)
            addView(title)
            isClickable = true
            isFocusable = true
        }
        return Holder(card, image, title)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.job?.cancel()
        holder.image.tag = item.uri.toString()
        holder.image.setImageResource(
            if (videos) android.R.drawable.ic_media_play else android.R.drawable.ic_menu_gallery
        )
        holder.title.text = item.title
        holder.card.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onClick(pos)
        }
        holder.job = scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                MediaThumbnails.load(context, item.uri, 420, 320)
            }
            if (holder.image.tag == item.uri.toString() && bitmap != null) {
                holder.image.setImageBitmap(bitmap)
            }
        }
    }

    override fun onViewRecycled(holder: Holder) {
        holder.job?.cancel()
        holder.image.tag = null
        holder.image.setImageDrawable(null)
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = items.size
}
