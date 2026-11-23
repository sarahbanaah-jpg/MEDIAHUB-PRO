package com.mediahub.rebuilt.ui.home

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mediahub.rebuilt.BuildConfig
import com.mediahub.rebuilt.R
import com.mediahub.rebuilt.ambient.AmbientDisplayActivity
import com.mediahub.rebuilt.arcade.MediaHubGameCenterActivity
import com.mediahub.rebuilt.intelligence.MediaHubIntelligenceActivity
import com.mediahub.rebuilt.content.MediaHubContentLibraryActivity
import com.mediahub.rebuilt.learn.MediaHubLearnActivity
import com.mediahub.rebuilt.recorder.MediaHubRecorderActivity
import com.mediahub.rebuilt.media.MediaItem
import com.mediahub.rebuilt.media.MediaRepository
import com.mediahub.rebuilt.media.MediaThumbnails
import com.mediahub.rebuilt.performance.DevicePerformance
import com.mediahub.rebuilt.remote.MediaHubRemoteActivity
import com.mediahub.rebuilt.studio.MediaHubStudioActivity
import com.mediahub.rebuilt.transfer.MediaHubTransferActivity
import com.mediahub.rebuilt.toolkit.MediaHubToolkitActivity
import com.mediahub.rebuilt.ui.MainActivity
import com.mediahub.rebuilt.updates.UpdateChecker
import com.mediahub.rebuilt.vault.PrivateVaultActivity
import com.mediahub.rebuilt.whatsnew.MediaHubWhatsNewActivity
import com.mediahub.rebuilt.whatsapp.WhatsAppBotActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {
    private lateinit var recent: LinearLayout
    private lateinit var categoryGrid: LinearLayout
    private lateinit var commandGrid: LinearLayout
    private lateinit var updateStatus: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val prefs = requireContext().getSharedPreferences("mediahub_settings", 0)
        val bg = if (prefs.getString("theme", "midnight") == "amoled") 0xFF000000.toInt() else 0xFF08080D.toInt()
        val scroll = ScrollView(requireContext()).apply {
            setBackgroundColor(bg)
            isFillViewport = true
            clipToPadding = false
        }
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(40), dp(16), dp(120))
        }
        scroll.addView(root)
        root.addView(TextView(requireContext()).apply {
            text = "MEDIAHUB PRO"
            setTextColor(0xFFFF2D95.toInt())
            textSize = 11f
            letterSpacing = 0.2f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(4))
        })
        root.addView(TextView(requireContext()).apply {
            text = "Your media. Your world."
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(16))
        })
        val onlineMusicCard = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF4A1738.toInt(), 0xFF211326.toInt(), 0xFF17151E.toInt())
            ).apply {
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), 0x66FF4D8D)
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(Intent(requireContext(), com.mediahub.rebuilt.ui.music.AudiusOnlineMusicActivity::class.java))
            }
        }
        onlineMusicCard.addView(TextView(requireContext()).apply {
            text = "♫  ONLINE MUSIC"
            setTextColor(0xFFFFA0B8.toInt())
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.12f
        })
        onlineMusicCard.addView(TextView(requireContext()).apply {
            text = "Discover & stream music"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(3))
        })
        onlineMusicCard.addView(TextView(requireContext()).apply {
            text = "Search artists and tracks · Trending · Latest releases"
            setTextColor(0xFFE3C8D8.toInt())
            textSize = 12f
        })
        onlineMusicCard.addView(TextView(requireContext()).apply {
            text = "OPEN ONLINE MUSIC  →"
            setTextColor(0xFFFF6FAE.toInt())
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(14), 0, 0)
        })
        root.addView(onlineMusicCard)
        root.addView(TextView(requireContext()).apply {
            text = "Command Centre"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(12))
        })
        commandGrid = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        root.addView(commandGrid)
        root.addView(TextView(requireContext()).apply {
            text = "Recents"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(8))
        })
        recent = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        root.addView(recent)
        root.addView(TextView(requireContext()).apply {
            text = "Categories"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(16), 0, dp(12))
        })
        categoryGrid = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        root.addView(categoryGrid)
        val updateCard = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = glassCard()
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) }
        }
        updateCard.addView(TextView(requireContext()).apply {
            text = "APP UPDATE"
            setTextColor(0xFFFF6BB5.toInt())
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
        })
        updateStatus = TextView(requireContext()).apply {
            text = "Checking…  ·  v${BuildConfig.VERSION_NAME}"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            setPadding(0, dp(8), 0, 0)
        }
        updateCard.addView(updateStatus)
        root.addView(updateCard)
        root.addView(TextView(requireContext()).apply {
            text = "© Grimy. All rights reserved."
            setTextColor(0xFF777381.toInt())
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, dp(28), 0, dp(8))
        })
        return scroll
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        fun toolRow(a: View, b: View) = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(a); addView(b)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        }
        commandGrid.removeAllViews()
        commandGrid.addView(toolRow(
            toolCard("🎬", "Studio", "Trim & export") { startActivity(Intent(requireContext(), MediaHubStudioActivity::class.java)) },
            toolCard("🔒", "Vault", "Encrypted files") { startActivity(Intent(requireContext(), PrivateVaultActivity::class.java)) }
        ))
        commandGrid.addView(toolRow(
            toolCard("📡", "Remote", "Offline QR control") { startActivity(Intent(requireContext(), MediaHubRemoteActivity::class.java)) },
            toolCard("🌙", "Ambient", "Charging clock") { startActivity(Intent(requireContext(), AmbientDisplayActivity::class.java)) }
        ))
        commandGrid.addView(toolRow(
            toolCard("🧠", "Intelligence", "Library tips") { startActivity(Intent(requireContext(), MediaHubIntelligenceActivity::class.java)) },
            toolCard("⇄", "Transfer", "QR · APK · Xender") { startActivity(Intent(requireContext(), MediaHubTransferActivity::class.java)) }
        ))
        commandGrid.addView(toolRow(
            toolCard("🎮", "Game Center", "200 offline challenges") { startActivity(Intent(requireContext(), MediaHubGameCenterActivity::class.java)) },
            toolCard("🎙", "Recorder", "Private voice notes") { startActivity(Intent(requireContext(), MediaHubRecorderActivity::class.java)) }
        ))
        commandGrid.addView(toolRow(
            toolCard("📚", "Learn", "Offline media guides") { startActivity(Intent(requireContext(), MediaHubLearnActivity::class.java)) },
            toolCard("🧰", "20 Tools", "Calculators & media guides") { startActivity(Intent(requireContext(), MediaHubToolkitActivity::class.java)) }
        ))
        commandGrid.addView(toolRow(
            toolCard("✨", "Content Library", "351 MB offline packs") { startActivity(Intent(requireContext(), MediaHubContentLibraryActivity::class.java)) },
            toolCard("💬", "WhatsApp Bot", "Baileys · real QR link") { startActivity(Intent(requireContext(), WhatsAppBotActivity::class.java)) }
        ))
        commandGrid.addView(toolRow(
            toolCard("✦", "What's New", "Read every feature") { startActivity(Intent(requireContext(), MediaHubWhatsNewActivity::class.java)) },
            toolCard("▣", "Media Library", "Your local collection") { openTab(R.id.navPhotos) }
        ))

        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = requireContext()
            val music = MediaRepository.loadMusic(ctx)
            val videos = MediaRepository.loadVideos(ctx)
            val photos = MediaRepository.loadPhotos(ctx)
            val snap = withContext(Dispatchers.IO) { DevicePerformance.snapshot(ctx) }
            recent.removeAllViews()
            val latest = (music + videos + photos).sortedByDescending { it.dateAdded }.take(4)
            if (latest.isEmpty()) {
                recent.addView(TextView(ctx).apply {
                    text = "No recent media yet"
                    setTextColor(0xFF9693A1.toInt())
                    textSize = 14f
                })
            } else latest.forEach { recent.addView(recentRow(it)) }
            categoryGrid.removeAllViews()
            fun rowOf(a: View, b: View) = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(a); addView(b)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
            }
            categoryGrid.addView(rowOf(
                catCard("♫", "Audio", "${music.size} tracks", 0xFFFF2D95.toInt()) { openTab(R.id.navMusic) },
                catCard("▶", "Videos", "${videos.size} files", 0xFFFF806F.toInt()) { openTab(R.id.navVideos) }
            ))
            categoryGrid.addView(rowOf(
                catCard("▣", "Images", "${photos.size} photos", 0xFFFFA0B8.toInt()) { openTab(R.id.navPhotos) },
                catCard("☁", "Storage", "${snap.freeStorageMb} MB free", 0xFFB59BFF.toInt()) { openTab(R.id.navSettings) }
            ))
        }

        UpdateChecker.check(requireContext(), notify = false) { info ->
            if (!isAdded) return@check
            if (info == null) updateStatus.text = "You're up to date  ·  v${BuildConfig.VERSION_NAME}"
            else {
                updateStatus.text = "v${info.version} available  ·  tap to update"
                updateStatus.setOnClickListener { UpdateChecker.openUpdateScreen(requireContext(), info) }
            }
        }
    }

    private fun openTab(navId: Int) {
        (activity as? MainActivity)?.findViewById<View>(navId)?.performClick()
    }

    private fun toolCard(icon: String, title: String, subtitle: String, onClick: () -> Unit): View {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = glassCard()
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5); marginStart = dp(5) }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        card.addView(TextView(requireContext()).apply { text = icon; textSize = 20f })
        card.addView(TextView(requireContext()).apply {
            text = title; setTextColor(0xFFFFFFFF.toInt()); textSize = 13f; setTypeface(null, Typeface.BOLD); setPadding(0, dp(8), 0, 0)
        })
        card.addView(TextView(requireContext()).apply {
            text = subtitle; setTextColor(0xFF9693A1.toInt()); textSize = 11f; setPadding(0, dp(2), 0, 0)
        })
        return card
    }

    private fun recentRow(item: MediaItem): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = glassCard()
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
            isClickable = true
            setOnClickListener {
                when (item.kind) {
                    MediaItem.Kind.AUDIO -> openTab(R.id.navMusic)
                    MediaItem.Kind.VIDEO -> openTab(R.id.navVideos)
                    MediaItem.Kind.IMAGE -> openTab(R.id.navPhotos)
                }
            }
        }
        val thumb = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply { setColor(0xFF25202D.toInt()); cornerRadius = dp(12).toFloat() }
            clipToOutline = true
        }
        row.addView(thumb)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val bmp = MediaThumbnails.load(requireContext(), item.uri, 96, 96)
            withContext(Dispatchers.Main) { if (bmp != null) thumb.setImageBitmap(bmp) }
        }
        val col = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        col.addView(TextView(requireContext()).apply {
            text = item.title; setTextColor(0xFFFFFFFF.toInt()); textSize = 14f; maxLines = 1; setTypeface(null, Typeface.BOLD)
        })
        col.addView(TextView(requireContext()).apply {
            text = item.kind.name; setTextColor(0xFF9693A1.toInt()); textSize = 12f
        })
        row.addView(col)
        return row
    }

    private fun catCard(icon: String, title: String, subtitle: String, accent: Int, onClick: () -> Unit): View {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = glassCard()
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5); marginStart = dp(5) }
            isClickable = true
            setOnClickListener { onClick() }
        }
        card.addView(TextView(requireContext()).apply { text = icon; setTextColor(accent); textSize = 18f })
        card.addView(TextView(requireContext()).apply {
            text = title; setTextColor(0xFFFFFFFF.toInt()); textSize = 14f; setTypeface(null, Typeface.BOLD); setPadding(0, dp(8), 0, 0)
        })
        card.addView(TextView(requireContext()).apply {
            text = subtitle; setTextColor(0xFF9693A1.toInt()); textSize = 11f
        })
        return card
    }

    private fun glassCard() = GradientDrawable().apply {
        setColor(0xCC17171F.toInt())
        cornerRadius = dp(18).toFloat()
        setStroke(dp(1), 0x33FFFFFF)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
