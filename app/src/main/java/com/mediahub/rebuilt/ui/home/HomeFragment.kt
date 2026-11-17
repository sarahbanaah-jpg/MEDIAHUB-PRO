package com.mediahub.rebuilt.ui.home

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
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mediahub.rebuilt.BuildConfig
import com.mediahub.rebuilt.media.MediaItem
import com.mediahub.rebuilt.media.MediaRepository
import com.mediahub.rebuilt.media.MediaThumbnails
import com.mediahub.rebuilt.performance.DevicePerformance
import com.mediahub.rebuilt.updates.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Polished liquid-glass home inspired by premium file managers:
 * search bar, recents strip, category grid, storage cards.
 */
class HomeFragment : Fragment() {
    private lateinit var summary: TextView
    private lateinit var recent: LinearLayout
    private lateinit var categoryGrid: LinearLayout
    private lateinit var updateStatus: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val scroll = ScrollView(requireContext()).apply {
            setBackgroundColor(0xFF0A0714.toInt())
            isFillViewport = true
            clipToPadding = false
        }
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(40), dp(16), dp(120))
        }
        scroll.addView(root)

        // Search-style glass bar
        root.addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(0xCC14101F.toInt())
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), 0x40FFFFFF)
            }
            addView(TextView(requireContext()).apply {
                text = "⌕"
                setTextColor(0xFF9A8BB0.toInt())
                textSize = 18f
                setPadding(0, 0, dp(12), 0)
            })
            addView(TextView(requireContext()).apply {
                text = "Search music, videos, photos"
                setTextColor(0xFF9A8BB0.toInt())
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
        })

        // Brand line
        root.addView(TextView(requireContext()).apply {
            text = "MEDIAHUB PRO"
            setTextColor(0xFFFF2D95.toInt())
            textSize = 11f
            letterSpacing = 0.2f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(20), 0, dp(4))
        })
        root.addView(TextView(requireContext()).apply {
            text = "Listen and enjoy your media"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(16))
        })

        // Recents header
        val recentsHeader = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        recentsHeader.addView(TextView(requireContext()).apply {
            text = "Recents"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        recentsHeader.addView(TextView(requireContext()).apply {
            text = "See all"
            setTextColor(0xFFFF6BB5.toInt())
            textSize = 13f
        })
        root.addView(recentsHeader)

        recent = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(8))
        }
        root.addView(recent)

        // Categories
        root.addView(TextView(requireContext()).apply {
            text = "Categories"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(16), 0, dp(12))
        })

        categoryGrid = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(categoryGrid)

        // Update glass card
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
            letterSpacing = 0.1f
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
            setTextColor(0xFF6B5A80.toInt())
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, dp(28), 0, dp(8))
        })

        return scroll
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = requireContext()
            val music = MediaRepository.loadMusic(ctx)
            val videos = MediaRepository.loadVideos(ctx)
            val photos = MediaRepository.loadPhotos(ctx)
            val snap = withContext(Dispatchers.IO) { DevicePerformance.snapshot(ctx) }

            // Recents strip
            recent.removeAllViews()
            val latest = (music + videos + photos).sortedByDescending { it.dateAdded }.take(4)
            if (latest.isEmpty()) {
                recent.addView(TextView(ctx).apply {
                    text = "No recent media yet"
                    setTextColor(0xFF9A8BB0.toInt())
                    textSize = 14f
                    setPadding(dp(4), dp(8), dp(4), dp(8))
                })
            } else {
                latest.forEach { recent.addView(recentRow(it)) }
            }

            // Category grid 2x3 style
            categoryGrid.removeAllViews()
            fun rowOf(a: View, b: View): LinearLayout {
                return LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(a)
                    addView(b)
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
                }
            }
            categoryGrid.addView(rowOf(
                catCard("♫", "Audio", "${music.size} tracks", 0xFFFF2D95.toInt()),
                catCard("▶", "Videos", "${videos.size} files", 0xFF9B6DFF.toInt())
            ))
            categoryGrid.addView(rowOf(
                catCard("▣", "Images", "${photos.size} photos", 0xFF5CE1FF.toInt()),
                catCard("↓", "Downloads", "Library", 0xFFFF6BB5.toInt())
            ))
            categoryGrid.addView(rowOf(
                catCard("☁", "Storage", "${snap.freeStorageMb} MB free", 0xFFA78BFA.toInt()),
                catCard("⚡", "Performance", "${snap.availableRamMb} MB RAM", 0xFF5EEAD4.toInt())
            ))
        }

        UpdateChecker.check(requireContext(), notify = false) { info ->
            if (!isAdded) return@check
            if (info == null) {
                updateStatus.text = "You're up to date  ·  v${BuildConfig.VERSION_NAME}"
            } else {
                updateStatus.text = "v${info.version} available  ·  tap to update"
                updateStatus.setOnClickListener { UpdateChecker.openUpdateScreen(requireContext(), info) }
            }
        }
    }

    private fun recentRow(item: MediaItem): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = glassCard()
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        }
        val thumb = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                setColor(0xFF22153A.toInt())
                cornerRadius = dp(12).toFloat()
            }
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
            text = item.title
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            maxLines = 1
            setTypeface(null, Typeface.BOLD)
        })
        col.addView(TextView(requireContext()).apply {
            text = when (item.kind) {
                MediaItem.Kind.AUDIO -> item.artist.ifBlank { "Music" }
                MediaItem.Kind.VIDEO -> "Video"
                MediaItem.Kind.IMAGE -> "Photo"
            }
            setTextColor(0xFF9A8BB0.toInt())
            textSize = 12f
            setPadding(0, dp(2), 0, 0)
        })
        row.addView(col)
        return row
    }

    private fun catCard(icon: String, title: String, subtitle: String, accent: Int): View {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = glassCard()
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = dp(5)
                marginStart = dp(5)
            }
        }
        card.addView(TextView(requireContext()).apply {
            text = icon
            setTextColor(accent)
            textSize = 22f
        })
        card.addView(TextView(requireContext()).apply {
            text = title
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(10), 0, 0)
        })
        card.addView(TextView(requireContext()).apply {
            text = subtitle
            setTextColor(0xFF9A8BB0.toInt())
            textSize = 12f
            setPadding(0, dp(4), 0, 0)
        })
        return card
    }

    private fun glassCard() = GradientDrawable().apply {
        setColor(0xFF16122A.toInt())
        cornerRadius = dp(20).toFloat()
        setStroke(dp(1), 0x33FFFFFF)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
