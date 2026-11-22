package com.mediahub.rebuilt.whatsnew

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** A complete, scrollable product changelog. Kept independent from the in-app updater. */
class MediaHubWhatsNewActivity : AppCompatActivity() {
    private val white = 0xFFFFFFFF.toInt()
    private val muted = 0xFFB9B6C3.toInt()
    private val pink = 0xFFFF6B9F.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(0xFF08080D.toInt()) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(38), dp(20), dp(48))
        }
        scroll.addView(root)
        root.addView(TextView(this).apply {
            text = "‹  MEDIAHUB PRO"
            textSize = 13f; setTextColor(pink); setTypeface(null, Typeface.BOLD)
            setOnClickListener { finish() }
        })
        root.addView(TextView(this).apply {
            text = "What's new"
            textSize = 31f; setTextColor(white); setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(18), 0, dp(5))
        })
        root.addView(TextView(this).apply {
            text = "The complete MediaHub Pro product changelog. Scroll to read every section."
            textSize = 14f; setTextColor(muted); setPadding(0, 0, 0, dp(22))
        })
        section(root, "THE MEDIAHUB EXPERIENCE", "A redesigned midnight interface with a cinematic home dashboard, spacious glass-inspired cards, coral-pink highlights, clearer navigation, consistent typography, and responsive visual feedback. Music, video and photo libraries now include dedicated search and sorting experiences.")
        section(root, "MUSIC & MEDIA", "Browse your local audio, video and photo libraries. Enjoy the dedicated player, background playback, media notifications, queue controls, recent items, and Dynamic Island-style playback surface.")
        section(root, "MEDIAHUB TRANSFER", "Select multiple files and create a private QR session to send them directly between MediaHub Pro devices over the same local Wi-Fi network. No internet or account is needed. Scan the sender QR inside MediaHub Share to receive files into Downloads/MediaHub. Export installed apps as base APK plus split APK parts and send them through the same QR flow. Android Sharesheet integration also supports compatible apps such as Xender and Quick Share.")
        section(root, "MEDIAHUB REMOTE", "Pair a second device using a session QR code over existing Wi-Fi, or request a temporary Android local-only Wi-Fi hotspot when no Wi-Fi network is available. Internet access is not required. Control playback, skip tracks and adjust volume from the paired device. Pairing uses a fresh random token and the remote server and temporary hotspot stop when you leave the screen.")
        section(root, "OFFLINE CONTENT LIBRARY", "Optional collections include 100 original Ultra HD wallpapers, ambient audio and game sounds, motion visuals, and game artwork. Downloads now follow secure HTTPS redirects, show live progress, verify exact published archive sizes, safely extract ZIP files and store installed packs for offline browsing. Total optional collection: approximately 350.76 MB.")
        section(root, "GAMES, LEARNING & TOOLS", "Play offline challenges, use the arcade, record voice notes, explore media-learning guides, and access practical media calculators and workflow checklists.")
        section(root, "CREATIVE & PRIVACY TOOLS", "Explore MediaHub Studio for media workflows, Private Vault for protected personal files, Ambient Display, storage insights, and device-performance information.")
        section(root, "PRIVACY & CONTROL", "Media access is used to display your local library. Direct QR transfers are user-initiated and protected by a one-time random session token; both devices must be on the same local Wi-Fi network. Android Sharesheet destinations are reviewed before sending. Optional content packs are separate from app updates.")
        root.addView(TextView(this).apply {
            text = "This page is the in-app feature guide. App installation and update delivery remain on the existing updater."
            textSize = 12f; setTextColor(muted); setPadding(0, dp(18), 0, 0)
        })
        setContentView(scroll)
    }

    private fun section(root: LinearLayout, title: String, body: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(0xFF15151D.toInt()); cornerRadius = dp(18).toFloat()
                setStroke(dp(1), 0x33FFFFFF)
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        }
        card.addView(TextView(this).apply {
            text = title; textSize = 11f; letterSpacing = .08f
            setTextColor(pink); setTypeface(null, Typeface.BOLD)
        })
        card.addView(TextView(this).apply {
            text = body; textSize = 14f; setTextColor(white)
            setLineSpacing(dp(3).toFloat(), 1f); setPadding(0, dp(9), 0, 0)
        })
        root.addView(card)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}