package com.mediahub.rebuilt.intelligence

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mediahub.rebuilt.media.MediaItem
import com.mediahub.rebuilt.media.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Private, on-device library insights. This is deterministic media analysis,
 * not a cloud AI service; no filenames or media are uploaded.
 */
class MediaHubIntelligenceActivity : AppCompatActivity() {
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = android.widget.ScrollView(this).apply { setBackgroundColor(0xFF0B0618.toInt()) }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(36), dp(20), dp(32))
        }
        scroll.addView(content)
        setContentView(scroll)
        content.addView(TextView(this).apply {
            text = "MediaHub Intelligence"
            textSize = 27f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(null, Typeface.BOLD)
        })
        content.addView(TextView(this).apply {
            text = "Private, on-device insights for your media library. Nothing is uploaded."
            textSize = 13f
            setTextColor(0xFFB9A9C9.toInt())
            setPadding(0, dp(8), 0, dp(18))
        })
        addCard("Analysing your library…")
        lifecycleScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                val audio = MediaRepository.loadMusic(this@MediaHubIntelligenceActivity)
                val video = MediaRepository.loadVideos(this@MediaHubIntelligenceActivity)
                val images = MediaRepository.loadPhotos(this@MediaHubIntelligenceActivity)
                Triple(audio, video, images)
            }
            render(snapshot.first, snapshot.second, snapshot.third)
        }
    }

    private fun render(audio: List<MediaItem>, videos: List<MediaItem>, images: List<MediaItem>) {
        content.removeViews(2, (content.childCount - 2).coerceAtLeast(0))
        val all = audio + videos + images
        addCard("YOUR LIBRARY\n${audio.size} music tracks  ·  ${videos.size} videos  ·  ${images.size} photos")
        val recent = all.sortedByDescending { it.dateAdded }.take(5)
        addCard(if (recent.isEmpty()) "RECENT ACTIVITY\nNo media found yet. Add files or check media permissions."
            else "RECENTLY ADDED\n" + recent.joinToString("\n") { "• ${it.title}" })
        val large = all.filter { it.sizeBytes > 0 }.sortedByDescending { it.sizeBytes }.take(5)
        addCard(if (large.isEmpty()) "STORAGE INSIGHT\nFile sizes are unavailable for this library."
            else "LARGEST FILES\n" + large.joinToString("\n") { "• ${it.title}  ·  ${formatBytes(it.sizeBytes)}" })
        val duplicateGroups = all.filter { it.title.isNotBlank() }
            .groupBy { it.title.trim().lowercase() }
            .filterValues { it.size > 1 }
        addCard(if (duplicateGroups.isEmpty()) "DUPLICATE CHECK\nNo duplicate titles detected."
            else "POSSIBLE DUPLICATES\n" + duplicateGroups.entries.take(8).joinToString("\n") { "• ${it.value.first().title} (${it.value.size} items)" } +
                "\n\nThese are title matches only. Review each file before deleting anything.")
        val recommendation = when {
            all.isEmpty() -> "Add music, videos or photos to start building your library."
            audio.isNotEmpty() -> "You have ${audio.size} tracks available offline. Open Music and use Play All to start a session."
            videos.isNotEmpty() -> "Your video library contains ${videos.size} files. Browse Videos to continue watching."
            else -> "Your photo library contains ${images.size} images. Open Photos to browse your gallery."
        }
        addCard("SMART SUGGESTION\n$recommendation")
        addCard("PRIVACY\nAnalysis runs locally on this phone. It does not use an online AI service and does not upload media, names or metadata.")
    }

    private fun addCard(text: String) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(0xFFECE5F4.toInt())
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(0xFF191229.toInt())
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), 0x33FFFFFF)
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        })
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) "%.1f GB".format(mb / 1024) else "%.1f MB".format(mb)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
