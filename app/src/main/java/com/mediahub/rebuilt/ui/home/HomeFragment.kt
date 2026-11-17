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
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mediahub.rebuilt.BuildConfig
import com.mediahub.rebuilt.R
import com.mediahub.rebuilt.media.MediaItem
import com.mediahub.rebuilt.media.MediaRepository
import com.mediahub.rebuilt.media.MediaThumbnails
import com.mediahub.rebuilt.performance.DevicePerformance
import com.mediahub.rebuilt.ui.MainActivity
import com.mediahub.rebuilt.updates.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {
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
            isClickable = true
            isFocusable = true
            setOnClickListener { openTab(R.id.navMusic) }
        })
        root.addView(recentsHeader)

        recent = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(8))
        }
        root.addView(recent)

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

        val updateCard = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = glassCard()
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) }
            isClickable = true
            isFocusable = true
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
                latest.forEach { item ->
                    recent.addView(recentRow(item))
                }
            }

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
                catCard("♫", "Audio", "${music.size} tracks", 0xFFFF2D95.toInt()) {
                    openTab(R.id.navMusic)
                },
                catCard("▶", "Videos", "${videos.size} files", 0xFF9B6DFF.toInt()) {
                    openTab(R.id.navVideos)
                }
            ))
            categoryGrid.addView(rowOf(
                catCard("▣", "Images", "${photos.size} photos", 0xFF5CE1FF.toInt()) {
                    openTab(R.id.navPhotos)
                },
                catCard("↓", "Downloads", "Library", 0xFFFF6BB5.toInt()) {
                    openTab(R.id.navMusic)
                    Toast.makeText(ctx, "Your media library", Toast.LENGTH_SHORT).show()
                }
            ))
            categoryGrid.addView(rowOf(
                catCard("☁", "Storage", "${snap.freeStorageMb} MB free", 0xFFA78BFA.toInt()) {
                    openTab(R.id.navSettings)
                    Toast.makeText(ctx, "Storage: ${snap.freeStorageMb} MB free", Toast.LENGTH_SHORT).show()
                },
                catCard("⚡", "Performance", "${snap.availableRamMb} MB RAM", 0xFF5EEAD4.toInt()) {
                    openTab(R.id.navSettings)
                    Toast.makeText(ctx, "RAM free: ${snap.availableRamMb} MB", Toast.LENGTH_SHORT).show()
                }
            ))
        }

        UpdateChecker.check(requireContext(), notify = false) { info ->
            if (!isAdded) return@check
            if (info == null) {
                updateStatus.text = "You're up to date  ·  v${BuildConfig.VERSION_NAME}"
            } else {
                updateStatus.text = "v${info.version} available  ·  tap to update"
                updateStatus.setOnClickListener {
                    UpdateChecker.openUpdateScreen(requireContext(), info)
                }
            }
        }
    }

    /** Open a bottom-nav tab from Home by simulating a click on the nav item. */
    private fun openTab(navId: Int) {
        val activity = activity as? MainActivity ?: return
        activity.findViewById<View>(navId)?.performClick()
    }

    private fun recentRow(item: MediaItem): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = glassCard()
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
            isClickable = true
            isFocusable = true
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

    private fun catCard(
        icon: String,
        title: String,
        subtitle: String,
        accent: Int,
        onClick: () -> Unit
    ): View {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = glassCard()
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = dp(5)
                marginStart = dp(5)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
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
