package com.mediahub.rebuilt.ui.home

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mediahub.rebuilt.media.MediaRepository
import kotlinx.coroutines.launch

/**
 * Premium home dashboard — hero, library stats chips, recent media list.
 */
class HomeFragment : Fragment() {
    private lateinit var summary: TextView
    private lateinit var recent: LinearLayout
    private lateinit var statsRow: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val scroll = ScrollView(requireContext()).apply {
            setBackgroundColor(0xFF08060C.toInt())
            clipToPadding = false
            isFillViewport = true
        }
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(48), dp(20), dp(120))
        }
        scroll.addView(root)

        // Brand label
        root.addView(TextView(requireContext()).apply {
            text = "MEDIAHUB PRO"
            setTextColor(0xFFFF5C8A.toInt())
            textSize = 11f
            letterSpacing = 0.22f
            setTypeface(null, Typeface.BOLD)
        })

        // Headline
        root.addView(TextView(requireContext()).apply {
            text = "Your media.\nBeautifully organised."
            setTextColor(0xFFFFF7FB.toInt())
            textSize = 32f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(6))
            setLineSpacing(0f, 1.05f)
        })

        root.addView(TextView(requireContext()).apply {
            text = "Music, videos and photos in one liquid-glass hub."
            setTextColor(0xFFC9B8C8.toInt())
            textSize = 14f
            setPadding(0, 0, 0, dp(20))
        })

        // Hero card
        val hero = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(22))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF5A2A45.toInt(), 0xFF2A1830.toInt(), 0xFF12101C.toInt())
            ).apply {
                cornerRadius = dp(24).toFloat()
            }
        }
        hero.addView(TextView(requireContext()).apply {
            text = "YOUR LIBRARY"
            setTextColor(0xFFFF8FB0.toInt())
            textSize = 11f
            letterSpacing = 0.16f
            setTypeface(null, Typeface.BOLD)
        })
        hero.addView(TextView(requireContext()).apply {
            text = "Discover and play everything you love."
            setTextColor(0xFFF9EFF5.toInt())
            textSize = 16f
            setPadding(0, dp(8), 0, 0)
        })
        root.addView(hero)

        // Stats row
        statsRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(18), 0, dp(8))
        }
        root.addView(statsRow)

        summary = TextView(requireContext()).apply {
            text = "Scanning your library…"
            setTextColor(0xFFFF5C8A.toInt())
            textSize = 13f
            setPadding(0, dp(8), 0, dp(4))
        }
        root.addView(summary)

        recent = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(recent)

        return scroll
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val context = requireContext()
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
                setTextColor(0xFFFFF7FB.toInt())
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, dp(16), 0, dp(12))
            })

            val latest = (music + videos + photos).sortedByDescending { it.dateAdded }.take(8)
            if (latest.isEmpty()) {
                recent.addView(TextView(context).apply {
                    text = "Your library is empty. Add media on your device and grant access to see it here."
                    setTextColor(0xFF8E7F93.toInt())
                    textSize = 14f
                    setPadding(dp(4), 0, dp(4), 0)
                })
            } else {
                latest.forEach { item ->
                    recent.addView(TextView(context).apply {
                        text = item.title
                        setTextColor(0xFFEFE6F0.toInt())
                        textSize = 14f
                        setPadding(dp(16), dp(14), dp(16), dp(14))
                        background = GradientDrawable().apply {
                            setColor(0xFF1A1422.toInt())
                            cornerRadius = dp(14).toFloat()
                            setStroke(dp(1), 0x18FFFFFF)
                        }
                        layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                            bottomMargin = dp(8)
                        }
                    })
                }
            }
        }
    }

    private fun statChip(value: String, label: String, accent: Int): View {
        val chip = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(14), dp(12), dp(14))
            background = GradientDrawable().apply {
                setColor(0xFF1A1422.toInt())
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), 0x22FFFFFF)
            }
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = dp(8)
            }
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
