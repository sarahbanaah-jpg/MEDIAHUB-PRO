package com.mediahub.rebuilt.ui.music

import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.mediahub.rebuilt.media.MediaItem
import com.mediahub.rebuilt.media.MediaRepository
import com.mediahub.rebuilt.service.PlaybackService
import kotlinx.coroutines.launch

/**
 * Neon dream music home — genre chips, popular cards, trending list.
 */
class MusicFragment : Fragment() {
    private var songs: List<MediaItem> = emptyList()
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var pendingIndex: Int? = null
    private lateinit var status: TextView
    private lateinit var songList: RecyclerView
    private lateinit var popularRow: LinearLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val scroll = android.widget.ScrollView(requireContext()).apply {
            setBackgroundColor(0xFF0B0618.toInt())
            isFillViewport = true
        }
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(40), dp(18), dp(110))
        }
        scroll.addView(root)

        // Greeting header
        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val greetCol = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        greetCol.addView(TextView(requireContext()).apply {
            text = "Hi, Grimy"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
        })
        greetCol.addView(TextView(requireContext()).apply {
            text = "Listen and enjoy your music"
            setTextColor(0xFFD4C4E8.toInt())
            textSize = 13f
            setPadding(0, dp(4), 0, 0)
        })
        header.addView(greetCol)
        header.addView(TextView(requireContext()).apply {
            text = "🔔"
            textSize = 20f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(0x33FFFFFF)
                cornerRadius = dp(20).toFloat()
            }
        })
        root.addView(header)

        // Genre chips
        val chipsScroll = HorizontalScrollView(requireContext()).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, dp(18), 0, dp(8))
        }
        val chips = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Popular", "Hip Hop", "Pop", "R&B", "Gospel", "Local").forEachIndexed { i, label ->
            chips.addView(TextView(requireContext()).apply {
                text = label
                setTextColor(if (i == 0) 0xFFFFFFFF.toInt() else 0xFFD4C4E8.toInt())
                textSize = 13f
                setPadding(dp(16), dp(10), dp(16), dp(10))
                background = GradientDrawable().apply {
                    if (i == 0) {
                        colors = intArrayOf(0xFFFF2D95.toInt(), 0xFF9B6DFF.toInt())
                        orientation = GradientDrawable.Orientation.LEFT_RIGHT
                    } else {
                        setColor(0x22FFFFFF)
                    }
                    cornerRadius = dp(20).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) }
            })
        }
        chipsScroll.addView(chips)
        root.addView(chipsScroll)

        // Popular section
        root.addView(TextView(requireContext()).apply {
            text = "Popular"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(14), 0, dp(12))
        })

        val popularScroll = HorizontalScrollView(requireContext()).apply {
            isHorizontalScrollBarEnabled = false
        }
        popularRow = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        popularScroll.addView(popularRow)
        root.addView(popularScroll)

        // Hot & Trending
        val trendHeader = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(22), 0, dp(12))
        }
        trendHeader.addView(TextView(requireContext()).apply {
            text = "Hot & Trending 🔥"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        trendHeader.addView(TextView(requireContext()).apply {
            text = "See all"
            setTextColor(0xFFFF6BB5.toInt())
            textSize = 13f
        })
        root.addView(trendHeader)

        status = TextView(requireContext()).apply {
            text = "Loading your music…"
            setTextColor(0xFFFF6BB5.toInt())
            textSize = 12f
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(status)

        songList = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(requireContext())
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        root.addView(songList)

        root.addView(TextView(requireContext()).apply {
            text = "© Grimy. All rights reserved."
            setTextColor(0xFF6B5A80.toInt())
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(8))
        })

        return scroll
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            songs = MediaRepository.loadMusic(requireContext())
            status.text = if (songs.isEmpty()) "No audio files found" else "${songs.size} tracks on this device"

            popularRow.removeAllViews()
            songs.take(6).forEachIndexed { index, song ->
                popularRow.addView(popularCard(song, index))
            }

            songList.adapter = SongAdapter(songs) { playAt(it) }
        }
    }

    private fun popularCard(song: MediaItem, index: Int): View {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(12))
            background = GradientDrawable().apply {
                setColor(0xFF1A1230.toInt())
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), 0x33FFFFFF)
            }
            layoutParams = LinearLayout.LayoutParams(dp(150), -2).apply { marginEnd = dp(12) }
            isClickable = true
            isFocusable = true
            setOnClickListener { playAt(index) }
        }
        card.addView(TextView(requireContext()).apply {
            text = "♪"
            textSize = 36f
            gravity = Gravity.CENTER
            setTextColor(0xFFFF6BB5.toInt())
            setPadding(0, dp(28), 0, dp(28))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF7B2FF7.toInt(), 0xFFFF2D95.toInt())
            ).apply { cornerRadius = dp(14).toFloat() }
        })
        card.addView(TextView(requireContext()).apply {
            text = song.title
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            maxLines = 1
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(10), 0, 0)
        })
        card.addView(TextView(requireContext()).apply {
            text = song.artist.ifBlank { "Unknown artist" }
            setTextColor(0xFF9A8BB0.toInt())
            textSize = 11f
            maxLines = 1
            setPadding(0, dp(2), 0, 0)
        })
        return card
    }

    override fun onStart() {
        super.onStart()
        if (controllerFuture != null) return
        val context = context ?: return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                controller = future.get()
                pendingIndex?.let { index ->
                    pendingIndex = null
                    playAt(index)
                }
            } catch (_: Exception) {
                if (isAdded) status.text = "Player unavailable"
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun playAt(index: Int) {
        if (index !in songs.indices) return
        val player = controller
        if (player == null) {
            pendingIndex = index
            status.text = "Starting player…"
            return
        }
        val queue = songs.map { song ->
            Media3Item.Builder()
                .setUri(song.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .build()
                )
                .build()
        }
        player.setMediaItems(queue, index, 0L)
        player.prepare()
        player.play()
        status.text = "Playing · ${songs[index].title}"
        startActivity(Intent(requireContext(), NowPlayingActivity::class.java))
    }

    override fun onStop() {
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    override fun onDestroyView() {
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onDestroyView()
    }

    private inner class SongAdapter(
        private val items: List<MediaItem>,
        private val onSongClick: (Int) -> Unit
    ) : RecyclerView.Adapter<SongAdapter.SongHolder>() {
        inner class SongHolder(val row: LinearLayout, val title: TextView, val detail: TextView) : RecyclerView.ViewHolder(row)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongHolder {
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = GradientDrawable().apply {
                    setColor(0xFF160E28.toInt())
                    cornerRadius = dp(16).toFloat()
                    setStroke(dp(1), 0x22FFFFFF)
                }
            }
            val art = TextView(requireContext()).apply {
                text = "♪"
                gravity = Gravity.CENTER
                setTextColor(0xFFFF6BB5.toInt())
                textSize = 18f
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(0xFF7B2FF7.toInt(), 0xFFFF2D95.toInt())
                ).apply { cornerRadius = dp(12).toFloat() }
            }
            row.addView(art)
            val col = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            val title = TextView(requireContext()).apply {
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                maxLines = 1
            }
            val detail = TextView(requireContext()).apply {
                setTextColor(0xFF9A8BB0.toInt())
                textSize = 12f
                setPadding(0, dp(3), 0, 0)
                maxLines = 1
            }
            col.addView(title)
            col.addView(detail)
            row.addView(col)
            row.addView(TextView(requireContext()).apply {
                text = "▶"
                setTextColor(0xFFFF2D95.toInt())
                textSize = 16f
                setPadding(dp(8), dp(8), dp(4), dp(8))
            })
            return SongHolder(row, title, detail)
        }

        override fun onBindViewHolder(holder: SongHolder, position: Int) {
            val song = items[position]
            holder.title.text = song.title
            holder.detail.text = listOf(song.artist.ifBlank { "Unknown" }, duration(song.durationMs)).joinToString(" · ")
            holder.row.setOnClickListener { onSongClick(holder.bindingAdapterPosition) }
            holder.row.layoutParams = RecyclerView.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        }

        override fun getItemCount() = items.size
    }

    private fun duration(ms: Long): String = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
