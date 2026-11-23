package com.mediahub.rebuilt.ui.music

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
import com.mediahub.rebuilt.media.Favourites
import com.mediahub.rebuilt.media.MediaItem
import com.mediahub.rebuilt.media.MediaRepository
import com.mediahub.rebuilt.service.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Music library — structure inspired by classic All Songs / Favorites player sample,
 * with full phone+SD scan and Media3 queue playback.
 */
class MusicFragment : Fragment() {
    private var allSongs: List<MediaItem> = emptyList()
    private var visibleSongs: List<MediaItem> = emptyList()
    private var showFavouritesOnly = false
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var pendingIndex: Int? = null
    private lateinit var status: TextView
    private lateinit var songList: RecyclerView
    private lateinit var tabAll: TextView
    private lateinit var tabFav: TextView
    private lateinit var searchField: EditText
    private var searchQuery = ""

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
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF07070B.toInt())
            setPadding(dp(18), dp(24), dp(18), dp(12))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val greetCol = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        greetCol.addView(TextView(requireContext()).apply {
            text = "Music"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
        })
        greetCol.addView(TextView(requireContext()).apply {
            text = "Phone · SD card · Favourites"
            setTextColor(0xFFFFA0B8.toInt())
            textSize = 13f
            setPadding(0, dp(4), 0, 0)
        })
        header.addView(greetCol)
        header.addView(TextView(requireContext()).apply {
            text = "COMET MUSIC"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF08070B.toInt())
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = GradientDrawable().apply {
                setColor(0xFFFF4D8D.toInt())
                cornerRadius = dp(14).toFloat()
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(Intent(requireContext(), CometMusicActivity::class.java)
            }
        })
        root.addView(header)

        // Tabs: All Songs | Favourites (from reference player)
        val tabs = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(16), 0, dp(8))
        }
        tabAll = tabChip("All Songs", true) {
            showFavouritesOnly = false
            applyFilter()
            styleTabs()
        }
        tabFav = tabChip("Favourites", false) {
            showFavouritesOnly = true
            applyFilter()
            styleTabs()
        }
        tabs.addView(tabAll)
        tabs.addView(tabFav)
        root.addView(tabs)

        val searchBox = LinearLayout(requireContext()).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(3), dp(14), dp(3))
            background = GradientDrawable().apply {
                setColor(0xFF17151E.toInt())
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), 0x33FFFFFF)
            }
        }
        searchField = EditText(requireContext()).apply {
            hint = "Search songs, artists and albums"
            setHintTextColor(0xFF827D8A.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            setSingleLine(true)
            background = null
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f)
        }
        searchBox.addView(TextView(requireContext()).apply {
            text = "⌕"; textSize = 22f; setTextColor(0xFFFF79A8.toInt())
            setPadding(0, 0, dp(8), 0)
        })
        searchBox.addView(searchField)
        root.addView(searchBox)
        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim().orEmpty()
                applyFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        status = TextView(requireContext()).apply {
            text = "Scanning phone + SD card…"
            setTextColor(0xFFFF4D8D.toInt())
            textSize = 12f
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(status)

        val libraryActions = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        libraryActions.addView(actionBtn("＋ ADD FOLDER", 1f) { folderPicker.launch(null) }.apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(8)
        })
        libraryActions.addView(actionBtn("↻ RESCAN", 0f) { refreshLibrary() })
        root.addView(libraryActions)

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
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
            setOnClickListener { if (visibleSongs.isNotEmpty()) playAt(0) }
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) }
        })
        row.addView(TextView(requireContext()).apply {
            text = "NOW PLAYING"
            setTextColor(0xFFFFF8FC.toInt())
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(0xFF17171F.toInt())
                cornerRadius = dp(15).toFloat()
                setStroke(dp(1), 0x33FFFFFF)
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
            isNestedScrollingEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setPadding(0, dp(12), 0, dp(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        root.addView(songList)

        return root
    }

    private fun tabChip(label: String, active: Boolean, onClick: () -> Unit): TextView {
        return TextView(requireContext()).apply {
            text = label
            textSize = 13f
            setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(if (active) 0xFF07070B.toInt() else 0xFFFFA0B8.toInt())
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = GradientDrawable().apply {
                setColor(if (active) 0xFFFF4D8D.toInt() else 0xFF17171F.toInt())
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), 0x55FF4D8D)
            }
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(10) }
            isClickable = true
            setOnClickListener { onClick() }
        }
    }

    private fun styleTabs() {
        fun paint(tab: TextView, active: Boolean) {
            tab.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
            tab.setTextColor(if (active) 0xFF07070B.toInt() else 0xFFFFA0B8.toInt())
            tab.background = GradientDrawable().apply {
                setColor(if (active) 0xFFFF4D8D.toInt() else 0xFF17171F.toInt())
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), 0x55FF4D8D)
            }
        }
        paint(tabAll, !showFavouritesOnly)
        paint(tabFav, showFavouritesOnly)
    }

    private fun actionBtn(label: String, weight: Float, click: () -> Unit): TextView {
        return TextView(requireContext()).apply {
            text = label
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 11f
            setPadding(dp(13), dp(12), dp(13), dp(12))
            background = GradientDrawable().apply {
                setColor(0xFF191922.toInt())
                cornerRadius = dp(12).toFloat()
            }
            isClickable = true
            setOnClickListener { click() }
            layoutParams = if (weight > 0f)
                LinearLayout.LayoutParams(0, -2, weight)
            else
                LinearLayout.LayoutParams(-2, -2)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (hasAudioPermission()) refreshLibrary() else audioPermission.launch(requiredAudioPermission())
    }

    override fun onResume() {
        super.onResume()
        if (isAdded && hasAudioPermission() && allSongs.isEmpty()) refreshLibrary()
        else if (isAdded) applyFilter()
    }

    private fun requiredAudioPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), requiredAudioPermission()) == PackageManager.PERMISSION_GRANTED

    private fun refreshLibrary() {
        if (!isAdded || !hasAudioPermission()) return
        status.text = "Scanning all phone, SD card and added music folders…"
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                allSongs = MediaRepository.loadMusic(requireContext()).sortedWith(
                    compareBy<MediaItem, String>(String.CASE_INSENSITIVE_ORDER) { it.title.trim() }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.artist.trim() }
                )
                if (!isAdded) return@launch
                applyFilter()
            } catch (error: Exception) {
                if (isAdded) status.text = "Music scan failed: ${error.localizedMessage ?: "unknown error"}"
            }
        }
    }

    private fun applyFilter() {
        val base = if (showFavouritesOnly) {
            allSongs.filter { Favourites.isFavourite(requireContext(), it.id) }
        } else {
            allSongs
        }
        visibleSongs = base.filter { song ->
            searchQuery.isBlank() ||
                song.title.contains(searchQuery, true) ||
                song.artist.contains(searchQuery, true) ||
                song.album.contains(searchQuery, true)
        }.sortedWith(
            compareBy<MediaItem, String>(String.CASE_INSENSITIVE_ORDER) { it.title.trim() }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.artist.trim() }
        )
        status.text = when {
            allSongs.isEmpty() -> "No audio indexed yet. Add your Music folder to include files Android has not indexed."
            showFavouritesOnly && visibleSongs.isEmpty() -> "No favourites yet. Long-press a song to add it."
            showFavouritesOnly -> "${visibleSongs.size} favourites"
            else -> "${visibleSongs.size} of ${allSongs.size} audio files · sorted A–Z"
        }
        songList.adapter = SongAdapter(visibleSongs,
            onSongClick = { playAt(it) },
            onSongLongClick = { song ->
                val now = Favourites.toggle(requireContext(), song.id)
                Toast.makeText(
                    requireContext(),
                    if (now) "Added to Favourites" else "Removed from Favourites",
                    Toast.LENGTH_SHORT
                ).show()
                if (showFavouritesOnly) applyFilter()
                else songList.adapter?.notifyDataSetChanged()
            }
        )
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
        if (index !in visibleSongs.indices) return
        val player = controller
        if (player == null) {
            pendingIndex = index
            status.text = "Starting player…"
            return
        }
        requireContext().getSharedPreferences("mediahub_settings", 0).edit()
            .putString("island_mode", "music")
            .putString("island_title", visibleSongs[index].title)
            .apply()
        val queue = visibleSongs.map { song ->
            Media3Item.Builder()
                .setUri(song.uri)
                .setMediaId(song.id.toString())
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
        status.text = "Playing · ${visibleSongs[index].title}"
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
        private val onSongClick: (Int) -> Unit,
        private val onSongLongClick: (MediaItem) -> Unit
    ) : RecyclerView.Adapter<SongAdapter.SongHolder>() {
        inner class SongHolder(
            val row: LinearLayout,
            val art: ImageView,
            val title: TextView,
            val detail: TextView,
            val favMark: TextView
        ) : RecyclerView.ViewHolder(row)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongHolder {
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply {
                    setColor(0xFF17171F.toInt())
                    cornerRadius = dp(16).toFloat()
                    setStroke(dp(1), 0x22FFFFFF)
                }
            }
            val art = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply {
                    setColor(0xFF25202D.toInt())
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
                setTextColor(0xFF9693A1.toInt())
                textSize = 12f
                setPadding(0, dp(3), 0, 0)
                maxLines = 1
            }
            col.addView(title)
            col.addView(detail)
            row.addView(col)
            val favMark = TextView(requireContext()).apply {
                text = "♡"
                setTextColor(0xFFFF2D95.toInt())
                textSize = 16f
                setPadding(dp(6), dp(6), dp(4), dp(6))
            }
            row.addView(favMark)
            row.addView(TextView(requireContext()).apply {
                text = "▶"
                setTextColor(0xFFFF4D8D.toInt())
                textSize = 16f
                setPadding(dp(6), dp(8), dp(4), dp(8))
            })
            return SongHolder(row, art, title, detail, favMark)
        }

        override fun onBindViewHolder(holder: SongHolder, position: Int) {
            val song = items[position]
            holder.title.text = song.title
            holder.detail.text = listOf(
                song.artist.ifBlank { "Unknown" },
                duration(song.durationMs)
            ).joinToString(" · ")
            val fav = Favourites.isFavourite(requireContext(), song.id)
            holder.favMark.text = if (fav) "♥" else "♡"
            holder.row.setOnClickListener { onSongClick(holder.bindingAdapterPosition) }
            holder.row.setOnLongClickListener {
                onSongLongClick(song)
                true
            }
            holder.favMark.setOnClickListener { onSongLongClick(song) }
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
