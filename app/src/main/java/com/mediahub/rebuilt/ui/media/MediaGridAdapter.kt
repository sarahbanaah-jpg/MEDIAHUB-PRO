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
            layoutParams = LinearLayout.LayoutParams(-1, dp(if (videos) 112 else 116))
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                setColor(0xFF241D29.toInt())
                cornerRadius = dp(12).toFloat()
            }
        }
        val title = TextView(context).apply {
            setTextColor(0xFFF5EDF7.toInt())
            textSize = 12f
            maxLines = 2
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(3), dp(7), dp(3), dp(3))
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(8))
            background = GradientDrawable().apply {
                setColor(0xFF17121C.toInt())
                cornerRadius = dp(17).toFloat()
                setStroke(dp(1), 0x22FFFFFF)
            }
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
        holder.image.setImageDrawable(null)
        holder.title.text = item.title
        holder.card.setOnClickListener { onClick(holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: return@setOnClickListener) }
        holder.job = scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                MediaThumbnails.load(context, item.uri, 420, 320)
            }
            if (holder.image.tag == item.uri.toString() && bitmap != null) holder.image.setImageBitmap(bitmap)
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
