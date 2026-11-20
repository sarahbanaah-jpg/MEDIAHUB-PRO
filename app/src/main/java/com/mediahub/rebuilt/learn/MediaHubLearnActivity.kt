package com.mediahub.rebuilt.learn

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Bundled reference cards that remain available without an internet connection. */
class MediaHubLearnActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(0xFF08080D.toInt()) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(38), dp(18), dp(36))
        }
        scroll.addView(root)
        root.addView(TextView(this).apply {
            text = "MEDIAHUB LEARN"
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Practical media tips, built in and available offline."
            textSize = 14f
            setTextColor(0xFFB9B6C3.toInt())
            setPadding(0, dp(8), 0, dp(20))
        })
        guide(root, "🎧  Better audio", "Use good-quality source files when possible. FLAC and WAV preserve lossless audio; AAC and MP3 use compression. Higher bitrates can preserve more detail, but the original recording quality still matters.")
        guide(root, "🎬  Video formats", "MP4 is a container, not a codec. H.264 video with AAC audio offers broad device compatibility. HEVC can reduce file size at similar quality, but older devices may not decode it smoothly.")
        guide(root, "🗂  Organise your library", "Keep descriptive filenames, group media into folders, and remove duplicate exports only after checking them. Sort by title when you need a predictable list, or by date when finding recent captures.")
        guide(root, "🔐  Protect private media", "Use a strong device screen lock. Keep recovery copies of important files in a separate trusted location. A private folder on one phone is not a substitute for a backup.")
        guide(root, "✂️  Editing safely", "Export edited copies instead of overwriting originals. Check the output before deleting source footage. Trimming does not improve the quality of a low-resolution source.")
        guide(root, "💾  Storage basics", "Video usually consumes more storage than audio or photos. Review large files first, clear exports you no longer need, and keep free space available so recording and editing can complete.")
        guide(root, "🔋  Playback and battery", "High screen brightness, high refresh rates, and background activity can increase battery use. For long listening sessions, lower screen brightness and use the lock-screen playback controls.")
        setContentView(scroll)
    }

    private fun guide(root: LinearLayout, title: String, body: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(0xFF15151D.toInt())
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), 0x33FFFFFF)
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        }
        card.addView(TextView(this).apply {
            text = title; textSize = 17f; setTextColor(0xFFFF6B9F.toInt()); typeface = Typeface.DEFAULT_BOLD
        })
        card.addView(TextView(this).apply {
            text = body; textSize = 14f; setTextColor(0xFFE3E0E9.toInt()); setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(0, dp(8), 0, 0)
        })
        root.addView(card)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
