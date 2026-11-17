package com.mediahub.rebuilt.ui.music

import android.content.ComponentName
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

class MusicFragment : Fragment() {
    private var songs: List<MediaItem> = emptyList()
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var pendingIndex: Int? = null
    private lateinit var status: TextView
    private lateinit var songList: RecyclerView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF09070D.toInt())
            setPadding(dp(18), dp(34), dp(18), dp(100))
        }
        root.addView(TextView(requireContext()).apply {
            text = "Music"; setTextColor(0xFFFFFFFF.toInt()); textSize = 29f
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(requireContext()).apply {
            text = "Your soundtrack"; setTextColor(0xFFB9ADBD.toInt()); textSize = 14f
            setPadding(0, dp(5), 0, dp(16))
        })
        val playAll = TextView(requireContext()).apply {
            text = "▶   PLAY ALL"; setTextColor(0xFFFFFFFF.toInt()); textSize = 13f
            setTypeface(null, Typeface.BOLD); setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply { setColor(0xFFB83D68.toInt()); cornerRadius = dp(15).toFloat() }
            isClickable = true; isFocusable = true
            setOnClickListener { if (songs.isNotEmpty()) playAt(0) }
        }
        root.addView(playAll)
        status = TextView(requireContext()).apply {
            text = "Loading your music…"; setTextColor(0xFFFF7898.toInt()); textSize = 12f
            setPadding(0, dp(14), 0, dp(10))
        }
        root.addView(status)
        songList = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(requireContext())
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        root.addView(songList)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            songs = MediaRepository.loadMusic(requireContext())
            status.text = if (songs.isEmpty()) "NO AUDIO FILES FOUND" else "${songs.size} TRACKS"
            songList.adapter = SongAdapter(songs) { playAt(it) }
        }
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
                pendingIndex?.let { index -> pendingIndex = null; playAt(index) }
            } catch (_: Exception) {
                if (isAdded) status.text = "Player unavailable. Try restarting the app."
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun playAt(index: Int) {
        val player = controller
        if (player == null) {
            pendingIndex = index
            status.text = "Starting player…"
            return
        }
        val queue = songs.map { song ->
            Media3Item.Builder()
                .setUri(song.uri)
                .setMediaMetadata(MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .build())
                .build()
        }
        player.setMediaItems(queue, index, 0L)
        player.prepare()
        player.play()
        status.text = "NOW PLAYING  ·  ${songs[index].title}"
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
                orientation = LinearLayout.VERTICAL
                setPadding(dp(15), dp(13), dp(15), dp(13))
                background = GradientDrawable().apply {
                    setColor(0xFF17121C.toInt()); cornerRadius = dp(14).toFloat()
                    setStroke(dp(1), 0x22FFFFFF)
                }
            }
            val title = TextView(requireContext()).apply { setTextColor(0xFFF5EDF7.toInt()); textSize = 15f; setTypeface(null, Typeface.BOLD) }
            val detail = TextView(requireContext()).apply { setTextColor(0xFFB9ADBD.toInt()); textSize = 12f; setPadding(0, dp(4), 0, 0) }
            row.addView(title); row.addView(detail)
            return SongHolder(row, title, detail)
        }
        override fun onBindViewHolder(holder: SongHolder, position: Int) {
            val song = items[position]
            holder.title.text = song.title
            holder.detail.text = listOf(song.artist.ifBlank { "Unknown artist" }, duration(song.durationMs)).joinToString("  ·  ")
            holder.row.setOnClickListener { onSongClick(holder.bindingAdapterPosition) }
            holder.row.layoutParams = RecyclerView.LayoutParams(-1, -2).apply { bottomMargin = dp(9) }
        }
        override fun getItemCount() = items.size
    }

    private fun duration(ms: Long): String = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
