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

class HomeFragment : Fragment() {
    private lateinit var summary: TextView
    private lateinit var recent: LinearLayout
    private lateinit var statsRow: LinearLayout
    private lateinit var performanceCard: LinearLayout
    private lateinit var updateCard: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val scroll = ScrollView(requireContext()).apply {
            setBackgroundColor(0xFF05040A.toInt())
            clipToPadding = false
            isFillViewport = true
        }
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(48), dp(20), dp(120))
        }
        scroll.addView(root)

        root.addView(TextView(requireContext()).apply {
            text = "MEDIAHUB PRO"
            setTextColor(0xFFFF5C8A.toInt())
            textSize = 11f
            letterSpacing = 0.22f
            setTypeface(null, Typeface.BOLD)
        })

        root.addView(TextView(requireContext()).apply {
            text = "Your media.\nBeautifully organised."
            setTextColor(0xFFFFF8FC.toInt())
            textSize = 30f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(6))
            setLineSpacing(0f, 1.05f)
        })

        root.addView(TextView(requireContext()).apply {
            text = "Music · Videos · Photos  ·  offline first"
            setTextColor(0xFFC9B8C8.toInt())
            textSize = 14f
            setPadding(0, 0, 0, dp(18))
        })

        val hero = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF5A2A45.toInt(), 0xFF2A1830.toInt(), 0xFF12101C.toInt())
            ).apply { cornerRadius = dp(24).toFloat() }
        }
        hero.addView(TextView(requireContext()).apply {
            text = "YOUR LIBRARY"
            setTextColor(0xFFFF8FB0.toInt())
            textSize = 11f
            letterSpacing = 0.14f
            setTypeface(null, Typeface.BOLD)
        })
        hero.addView(TextView(requireContext()).apply {
            text = "Everything you love, in one liquid-glass hub."
            setTextColor(0xFFF9EFF5.toInt())
            textSize = 15f
            setPadding(0, dp(8), 0, 0)
        })
        root.addView(hero)

        statsRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(16), 0, dp(4))
        }
        root.addView(statsRow)

        summary = TextView(requireContext()).apply {
            text = "Scanning your library…"
            setTextColor(0xFFFF5C8A.toInt())
            textSize = 13f
            setPadding(0, dp(10), 0, dp(4))
        }
        root.addView(summary)

        // Update status card
        updateCard = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = glass()
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        }
        updateCard.addView(TextView(requireContext()).apply {
            text = "APP UPDATE"
            setTextColor(0xFFFF8FB0.toInt())
            textSize = 11f
            letterSpacing = 0.12f
            setTypeface(null, Typeface.BOLD)
        })
        updateCard.addView(TextView(requireContext()).apply {
            tag = "update_status"
            text = "Checking…  ·  v${BuildConfig.VERSION_NAME}"
            setTextColor(0xFFEFE6F0.toInt())
            textSize = 14f
            setPadding(0, dp(8), 0, 0)
        })
        root.addView(updateCard)

        // Performance card
        performanceCard = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = glass()
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        }
        performanceCard.addView(TextView(requireContext()).apply {
            text = "DEVICE PERFORMANCE"
            setTextColor(0xFFA78BFA.toInt())
            textSize = 11f
            letterSpacing = 0.12f
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(performanceCard)

        recent = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        root.addView(recent)

        return scroll
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val context = requireContext()

            val snap = withContext(Dispatchers.IO) { DevicePerformance.snapshot(context) }
            performanceCard.findViewWithTag<TextView>("perf_lines")?.let { performanceCard.removeView(it) }
            DevicePerformance.summaryLines(snap).forEach { line ->
                performanceCard.addView(TextView(context).apply {
                    text = line
                    setTextColor(0xFFC9B8C8.toInt())
                    textSize = 13f
                    setPadding(0, dp(6), 0, 0)
                })
            }

            val videos = MediaRepository.loadVideos(context)
            val music = MediaRepository.loadMusic(context)
            val photos = MediaRepository.loadPhotos(context)

            summary.text = "Library ready"
            statsRow.removeAllViews()
            statsRow.addView(statChip("${music.size}", "Songs", 0xFFFF5C8A.toInt()))
            statsRow.addView(statChip("${videos.size}", "Videos", 0xFFA78BFA.toInt()))
            statsRow.addView(statChip("${photos.size}", "Photos", 0xFF5EEAD4.toInt()))

            recent.removeAllViews()
            recent.addView(TextView(context).apply {
                text = "Recently added"
                setTextColor(0xFFFFF8FC.toInt())
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, dp(18), 0, dp(12))
            })

            val latest = (music + videos + photos).sortedByDescending { it.dateAdded }.take(10)
            if (latest.isEmpty()) {
                recent.addView(TextView(context).apply {
                    text = "Your library is empty. Add media on your device and grant access to see it here."
                    setTextColor(0xFF8E7F93.toInt())
                    textSize = 14f
                })
            } else {
                latest.forEach { item ->
                    recent.addView(mediaRow(item))
                }
            }
        }

        UpdateChecker.check(requireContext(), notify = false) { info ->
            if (!isAdded) return@check
            val status = updateCard.findViewWithTag<TextView>("update_status")
            if (info == null) {
                status?.text = "You're up to date  ·  v${BuildConfig.VERSION_NAME}"
            } else {
                status?.text = "v${info.version} available  ·  tap to update"
                updateCard.setOnClickListener {
                    UpdateChecker.openUpdateScreen(requireContext(), info)
                }
            }
        }
    }

    private fun mediaRow(item: MediaItem): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = glass()
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        }
        val thumb = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0xFF1A1524.toInt())
        }
        row.addView(thumb)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val bmp = MediaThumbnails.load(requireContext(), item.uri, 96, 96)
            withContext(Dispatchers.Main) {
                if (bmp != null) thumb.setImageBitmap(bmp)
            }
        }
        val col = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        col.addView(TextView(requireContext()).apply {
            text = item.title
            setTextColor(0xFFFFF8FC.toInt())
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
            setTextColor(0xFF8E7F93.toInt())
            textSize = 12f
            setPadding(0, dp(2), 0, 0)
        })
        row.addView(col)
        return row
    }

    private fun glass() = GradientDrawable().apply {
        setColor(0xFF16121F.toInt())
        cornerRadius = dp(16).toFloat()
        setStroke(dp(1), 0x28FFFFFF)
    }

    private fun statChip(value: String, label: String, accent: Int): View {
        val chip = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(14), dp(12), dp(14))
            background = glass()
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) }
        }
        chip.addView(TextView(requireContext()).apply {
            text = value
            setTextColor(accent)
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        chip.addView(TextView(requireContext()).apply {
            text = label
            setTextColor(0xFF8E7F93.toInt())
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, 0)
        })
        return chip
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
