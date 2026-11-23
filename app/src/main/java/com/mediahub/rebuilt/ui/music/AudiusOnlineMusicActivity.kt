package com.mediahub.rebuilt.ui.music

import android.content.ComponentName
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.mediahub.rebuilt.service.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

/**
 * Audius online catalogue. Uses public read-only endpoints; no API key is required
 * for this initial integration. Track playback is delegated to MEDIAHUB-PRO's
 * existing Media3 PlaybackService so background audio and media controls remain native.
 */
class AudiusOnlineMusicActivity : AppCompatActivity() {
    private val tracks = mutableListOf<OnlineTrack>()
    private var activeSearch: String? = null
    private var activeFeed: String = "trending"
    private var offset = 0
    private val pageSize = 30
    private var canLoadMore = false
    private var loading = false
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(12))
            setBackgroundColor(0xFF07070B.toInt())
        }
        root.addView(TextView(this).apply {
            text = "ONLINE MUSIC"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Discover and stream music from Audius"
            textSize = 13f
            setTextColor(0xFFFFA0B8.toInt())
            setPadding(0, dp(4), 0, dp(14))
        })
        val searchRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = panel()
            setPadding(dp(12), 0, dp(8), 0)
        }
        val query = EditText(this).apply {
            hint = "Search tracks or artists"
            setHintTextColor(0xFF898592.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            setSingleLine(true)
            background = null
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
        }
        searchRow.addView(query)
        searchRow.addView(TextView(this).apply {
            text = "SEARCH"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFFF4D8D.toInt())
            setPadding(dp(8), dp(12), dp(8), dp(12))
            setOnClickListener { search(query.text.toString().trim()) }
        })
        root.addView(searchRow)
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(8))
        }
        actions.addView(action("TRENDING", true) { loadTrending() })
        actions.addView(action("LATEST", false) { loadLatest() })
        actions.addView(action("NEW SEARCH", false) { query.setText("") })
        root.addView(actions)
        status = TextView(this).apply {
            text = "Loading trending tracks…"
            textSize = 12f
            setTextColor(0xFFFFA0B8.toInt())
            setPadding(0, dp(4), 0, dp(8))
        }
        root.addView(status)
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        loadTrending()
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try { controller = future.get() } catch (_: Exception) {
                if (!isFinishing) status.text = "The MEDIAHUB-PRO player is unavailable."
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun loadTrending() { activeFeed = "trending"; fetch(null, reset = true) }

    private fun loadLatest() { activeFeed = "latest"; fetch(null, reset = true) }

    private fun search(query: String) {
        if (query.isBlank()) {
            Toast.makeText(this, "Enter a track or artist name", Toast.LENGTH_SHORT).show()
            return
        }
        activeFeed = "search"
        fetch(query, reset = true)
    }

    /** Read-only Audius discovery works without a developer key. */
    private fun fetch(query: String?, reset: Boolean) {
        if (loading) return
        if (reset) {
            activeSearch = query
            offset = 0
            canLoadMore = true
            tracks.clear()
            list.removeAllViews()
        }
        loading = true
        status.text = if (reset) {
            if (query == null) "Loading trending music…" else "Searching Audius…"
        } else "Loading more tracks…"

        lifecycleScope.launch {
            try {
                val page = withContext(Dispatchers.IO) {
                    val encoded = activeSearch?.let { URLEncoder.encode(it, "UTF-8") }
                    val path = when (activeFeed) {
                        "latest" -> "tracks/latest?limit=$pageSize&offset=$offset"
                        "search" -> "search/full?query=$encoded&kind=tracks&sort_method=relevant&limit=$pageSize&offset=$offset"
                        else -> "tracks/trending?time=week&limit=$pageSize&offset=$offset"
                    }
                    val endpoint = "https://api.audius.co/v1/$path&app_name=MEDIAHUB_PRO"
                    val connection = URL(endpoint).openConnection() as HttpURLConnection
                    connection.connectTimeout = 15000
                    connection.readTimeout = 20000
                    connection.setRequestProperty("Accept", "application/json")
                    try {
                        val code = connection.responseCode
                        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                        val body = stream.bufferedReader().use { it.readText() }
                        if (code !in 200..299) throw IllegalStateException("Audius returned HTTP $code")
                        val responseData = JSONObject(body).opt("data")
                        val data = when (responseData) {
                            is org.json.JSONArray -> responseData
                            is JSONObject -> responseData.optJSONArray("tracks")
                                ?: responseData.optJSONArray("data")
                            else -> null
                        } ?: throw IllegalStateException("Unexpected Audius response")
                        buildList {
                            for (i in 0 until data.length()) {
                                val item = data.optJSONObject(i) ?: continue
                                val id = item.optString("id")
                                if (id.isBlank() || id == "null") continue
                                val user = item.optJSONObject("user")
                                val artwork = item.optJSONObject("artwork")
                                add(OnlineTrack(
                                    id = id,
                                    title = item.optString("title", "Untitled"),
                                    artist = user?.optString("name", "Unknown artist") ?: "Unknown artist",
                                    durationMs = item.optLong("duration", 0L) * 1000L,
                                    artworkUrl = artwork?.optString("150x150", "") ?: ""
                                ))
                            }
                        }
                    } finally {
                        connection.disconnect()
                    }
                }
                val known = tracks.mapTo(mutableSetOf()) { it.id }
                page.filter { known.add(it.id) }.forEach { tracks.add(it) }
                offset += page.size
                canLoadMore = page.size == pageSize
                loading = false
                renderTracks()
                val label = activeSearch?.let { "Search: $it" } ?: if (activeFeed == "latest") "Latest releases" else "Trending music"
                status.text = if (tracks.isEmpty()) "No tracks found. Try another search."
                    else "$label · ${tracks.size} tracks loaded"
            } catch (error: Exception) {
                status.text = if (tracks.isEmpty()) {
                    "Could not load online music: ${error.localizedMessage ?: "Check your connection and retry."}"
                } else {
                    "Showing ${tracks.size} tracks. Could not load the next page."
                }
                if (tracks.isNotEmpty()) {
                    loading = false
                    renderTracks()
                }
                else list.addView(TextView(this@AudiusOnlineMusicActivity).apply {
                    text = "TAP TRENDING OR LATEST TO RETRY"
                    setTextColor(0xFFFF4D8D.toInt())
                    setPadding(0, dp(14), 0, dp(14))
                    setOnClickListener { loadTrending() }
                })
            } finally {
                loading = false
            }
        }
    }

    private fun renderTracks() {
        list.removeAllViews()
        tracks.forEachIndexed { index, track -> addTrackRow(index, track) }
        if (canLoadMore) {
            list.addView(TextView(this).apply {
                text = if (loading) "LOADING MORE…" else "LOAD MORE TRACKS"
                gravity = Gravity.CENTER
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(0xFFFF4D8D.toInt())
                background = panel()
                setPadding(dp(16), dp(16), dp(16), dp(16))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(4)
                    bottomMargin = dp(12)
                }
                setOnClickListener { if (!loading) fetch(activeSearch, reset = false) }
            })
        }
    }

    private fun addTrackRow(index: Int, track: OnlineTrack) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = panel()
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
            isClickable = true
            setOnClickListener { play(index) }
        }
        val art = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                setColor(0xFF292431.toInt())
                cornerRadius = dp(10).toFloat()
            }
        }
        row.addView(art)
        if (track.artworkUrl.isNotBlank()) {
            lifecycleScope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    try {
                        val connection = URL(track.artworkUrl).openConnection() as HttpURLConnection
                        connection.connectTimeout = 10000
                        connection.readTimeout = 10000
                        try { connection.inputStream.use { BitmapFactory.decodeStream(it) } }
                        finally { connection.disconnect() }
                    } catch (_: Exception) { null }
                }
                if (!isFinishing && art.parent != null) art.setImageBitmap(bitmap)
            }
        }
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        details.addView(TextView(this).apply {
            text = track.title
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt())
            maxLines = 1
        })
        details.addView(TextView(this).apply {
            text = "${track.artist} · ${formatDuration(track.durationMs)}"
            textSize = 12f
            setTextColor(0xFFAAA5B2.toInt())
            maxLines = 1
            setPadding(0, dp(3), 0, 0)
        })
        row.addView(details)
        row.addView(TextView(this).apply {
            text = "▶"
            textSize = 18f
            setTextColor(0xFFFF4D8D.toInt())
        })
        list.addView(row)
    }

    private fun play(index: Int) {
        val player = controller
        if (player == null) {
            Toast.makeText(this, "Starting player. Tap the track again in a moment.", Toast.LENGTH_SHORT).show()
            return
        }
        val queue = tracks.map { track ->
            val uri = "https://api.audius.co/v1/tracks/${track.id}/stream?app_name=MEDIAHUB_PRO"
            Media3Item.Builder()
                .setUri(uri)
                .setMediaId("audius:${track.id}")
                .setMediaMetadata(MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(android.net.Uri.parse(track.artworkUrl).takeIf { track.artworkUrl.isNotBlank() })
                    .build())
                .build()
        }
        player.setMediaItems(queue, index, 0L)
        player.prepare()
        player.play()
        getSharedPreferences("mediahub_settings", MODE_PRIVATE).edit()
            .putString("island_mode", "music")
            .putString("island_title", tracks[index].title)
            .apply()
        Toast.makeText(this, "Playing from Audius", Toast.LENGTH_SHORT).show()
    }

    private fun action(label: String, primary: Boolean, click: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 11f
        setTypeface(null, Typeface.BOLD)
        setTextColor(if (primary) 0xFF08070B.toInt() else 0xFFFFA0B8.toInt())
        background = GradientDrawable().apply {
            setColor(if (primary) 0xFFFF4D8D.toInt() else 0xFF191922.toInt())
            cornerRadius = dp(12).toFloat()
        }
        setPadding(dp(14), dp(11), dp(14), dp(11))
        setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) }
    }

    private fun panel() = GradientDrawable().apply {
        setColor(0xFF17151E.toInt())
        cornerRadius = dp(14).toFloat()
        setStroke(dp(1), 0x22FFFFFF)
    }

    private fun formatDuration(ms: Long) = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onStop() {
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    private data class OnlineTrack(
        val id: String,
        val title: String,
        val artist: String,
        val durationMs: Long,
        val artworkUrl: String
    )
}
