package com.mediahub.rebuilt.ui.music

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
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
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
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
import com.mediahub.rebuilt.media.DefaultAlbumArt
import com.mediahub.rebuilt.media.MediaItem
import com.mediahub.rebuilt.media.MediaRepository
import com.mediahub.rebuilt.service.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicFragment : Fragment() {
    private var songs: List<MediaItem> = emptyList()
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var pendingIndex: Int? = null
    private lateinit var status: TextView
    private lateinit var songList: RecyclerView
    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) refreshLibrary() else if (isAdded) status.text = "Allow Music and audio access in Android settings to show your full music library."
    }
    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && isAdded) {
            try {
                requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                MediaRepository.rememberMusicFolder(requireContext(), uri)
                refreshLibrary()
            } catch (error: Exception) {
                status.text = "Could not access that folder: ${error.localizedMessage ?: "permission unavailable"}"
            }
        }
    }

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
        root.addView(header)

        val trendHeader = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(22), 0, dp(12))
        }
        trendHeader.addView(TextView(requireContext()).apply {
            text = "All music · A–Z"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        root.addView(trendHeader)

        status = TextView(requireContext()).apply {
            text = "Scanning phone + SD card…"
            setTextColor(0xFFFF6BB5.toInt())
            textSize = 12f
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(status)

        val libraryActions = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        libraryActions.addView(TextView(requireContext()).apply {
            text = "＋ ADD MUSIC FOLDER"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 11f
            setPadding(dp(13), dp(12), dp(13), dp(12))
            background = GradientDrawable().apply { setColor(0xFF241632.toInt()); cornerRadius = dp(12).toFloat() }
            isClickable = true
            setOnClickListener { folderPicker.launch(null) }
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) }
        })
        libraryActions.addView(TextView(requireContext()).apply {
            text = "↻ RESCAN"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 11f
            setPadding(dp(13), dp(12), dp(13), dp(12))
            background = GradientDrawable().apply { setColor(0xFF241632.toInt()); cornerRadius = dp(12).toFloat() }
            isClickable = true
            setOnClickListener { refreshLibrary() }
        })
        root.addView(libraryActions)

        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(TextView(requireContext()).apply {
            text = "▶  PLAY ALL"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(0xFFFF2D95.toInt())
                cornerRadius = dp(15).toFloat()
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { if (songs.isNotEmpty()) playAt(0) }
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) }
        })
        row.addView(TextView(requireContext()).apply {
            text = "NOW PLAYING"
            setTextColor(0xFFFFF8FC.toInt())
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(0xFF16121F.toInt())
                cornerRadius = dp(15).toFloat()
                setStroke(dp(1), 0x33FF2D95)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(Intent(requireContext(), NowPlayingActivity::class.java))
            }
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        root.addView(row)

        songList = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(requireContext())
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(0, dp(12), 0, 0)
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
        if (hasAudioPermission()) refreshLibrary() else audioPermission.launch(requiredAudioPermission())
    }

    override fun onResume() {
        super.onResume()
        if (isAdded && hasAudioPermission() && songs.isEmpty()) refreshLibrary()
    }

    private fun requiredAudioPermission(): String = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun hasAudioPermission(): Boolean = ContextCompat.checkSelfPermission(requireContext(), requiredAudioPermission()) == PackageManager.PERMISSION_GRANTED

    private fun refreshLibrary() {
        if (!isAdded || !hasAudioPermission()) return
        status.text = "Scanning all phone, SD card and added music folders…"
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                songs = MediaRepository.loadMusic(requireContext()).sortedWith(
                    compareBy<MediaItem, String>(String.CASE_INSENSITIVE_ORDER) { song -> song.title.trim() }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { song -> song.artist.trim() }
                )
                if (!isAdded) return@launch
                status.text = if (songs.isEmpty()) "No audio indexed yet. Add your Music folder to include files Android has not indexed." else "${songs.size} audio files found · phone, SD card and added folders"
                songList.adapter = SongAdapter(songs) { playAt(it) }
            } catch (error: Exception) {
                if (isAdded) status.text = "Music scan failed: ${error.localizedMessage ?: "unknown error"}"
            }
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
        requireContext().getSharedPreferences("mediahub_settings", 0).edit()
            .putString("island_mode", "music")
            .putString("island_title", songs[index].title)
            .apply()
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
        inner class SongHolder(
            val row: LinearLayout,
            val art: ImageView,
            val title: TextView,
            val detail: TextView
        ) : RecyclerView.ViewHolder(row)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongHolder {
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply {
                    setColor(0xFF160E28.toInt())
                    cornerRadius = dp(16).toFloat()
                    setStroke(dp(1), 0x22FFFFFF)
                }
            }
            val art = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply {
                    setColor(0xFF22153A.toInt())
                    cornerRadius = dp(12).toFloat()
                }
                clipToOutline = true
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
            return SongHolder(row, art, title, detail)
        }

        override fun onBindViewHolder(holder: SongHolder, position: Int) {
            val song = items[position]
            holder.title.text = song.title
            holder.detail.text = listOf(
                song.artist.ifBlank { "Unknown" },
                duration(song.durationMs)
            ).joinToString(" · ")
            holder.row.setOnClickListener { onSongClick(holder.bindingAdapterPosition) }
            holder.row.layoutParams = RecyclerView.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
            holder.art.setImageBitmap(null)
            holder.art.tag = song.uri.toString()
            viewLifecycleOwner.lifecycleScope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    DefaultAlbumArt.forTrack(requireContext(), song, 160)
                }
                if (holder.art.tag == song.uri.toString()) {
                    holder.art.setImageBitmap(bmp)
                }
            }
        }

        override fun getItemCount() = items.size
    }

    private fun duration(ms: Long): String = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
