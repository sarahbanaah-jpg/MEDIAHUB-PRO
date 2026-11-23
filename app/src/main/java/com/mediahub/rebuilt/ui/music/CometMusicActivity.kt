package com.mediahub.rebuilt.ui.music

import android.content.ComponentName
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

/** Independent Comet-inspired online catalogue using a public third-party JioSaavn API. */
class CometMusicActivity : AppCompatActivity() {
    private val songs = mutableListOf<CloudSong>()
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private lateinit var searchField: EditText
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(12))
            setBackgroundColor(0xFF07070B.toInt())
        }
        root.addView(TextView(this).apply {
            text = "COMET MUSIC"
            textSize = 23f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Online search · global catalogue · lyrics"
            textSize = 12f
            setTextColor(0xFFFFA0B8.toInt())
            setPadding(0, dp(4), 0, dp(14))
        })
        val sources = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        sources.addView(chip("JIOSAAVN", true) { loadTrending() })
        sources.addView(chip("AUDIUS", false) {
            startActivity(android.content.Intent(this@CometMusicActivity, AudiusOnlineMusicActivity::class.java))
        })
        root.addView(sources)

        val searchRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = panel()
            setPadding(dp(12), 0, dp(8), 0)
        }
        searchField = EditText(this).apply {
            hint = "Search songs or artists"
            setHintTextColor(0xFF898592.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            setSingleLine(true)
            background = null
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
        }
        searchRow.addView(searchField)
        searchRow.addView(TextView(this).apply {
            text = "SEARCH"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFFF4D8D.toInt())
            setPadding(dp(8), dp(12), dp(8), dp(12))
            setOnClickListener { search(searchField.text.toString().trim()) }
        })
        root.addView(searchRow)
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(8))
        }
        actions.addView(chip("TRENDING", true) { loadTrending() })
        actions.addView(chip("NEW SEARCH", false) { searchField.setText("") })
        actions.addView(chip("ABOUT", false) { showAbout() })
        root.addView(actions)
        status = TextView(this).apply {
            text = "Loading charts…"
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
            try { controller = future.get() }
            catch (_: Exception) { if (!isFinishing) status.text = "MEDIAHUB-PRO player unavailable." }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun loadTrending() = fetch("https://jio.shnwaz.dev/api/trending/songs?limit=40", "Trending songs")

    private fun search(query: String) {
        if (query.isBlank()) {
            Toast.makeText(this, "Enter a song or artist", Toast.LENGTH_SHORT).show()
            return
        }
        fetch("https://jio.shnwaz.dev/api/search/songs?query=" + URLEncoder.encode(query, "UTF-8") + "&page=0&limit=40", "Search: " + query)
    }

    private fun fetch(endpoint: String, label: String) {
        if (loading) return
        loading = true
        status.text = "Loading " + label + "…"
        list.removeAllViews()
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val json = requestJson(endpoint)
                    val data = json.optJSONObject("data") ?: json
                    val array = data.optJSONArray("results") ?: data.optJSONArray("songs") ?: json.optJSONArray("results") ?: JSONArray()
                    val parsed = mutableListOf<CloudSong>()
                    for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        val id = item.optString("id")
                        if (id.isNotBlank() && id != "null") parsed.add(parseSong(item))
                    }
                    parsed.distinctBy { it.id }
                }
                songs.clear()
                songs.addAll(result)
                renderSongs()
                status.text = if (songs.isEmpty()) "No songs found. Try another search."
                    else label + " · " + songs.size + " tracks · tap to play, long-press for lyrics"
            } catch (e: Exception) {
                status.text = "Music service unavailable: " + (e.localizedMessage ?: "Check your connection.")
                list.addView(TextView(this@CometMusicActivity).apply {
                    text = "TAP TO RETRY"
                    setTextColor(0xFFFF4D8D.toInt())
                    setPadding(0, dp(18), 0, dp(18))
                    setOnClickListener { loadTrending() }
                })
            } finally { loading = false }
        }
    }

    private fun parseSong(item: JSONObject): CloudSong {
        val primary = item.optJSONObject("artists")?.optJSONArray("primary")
        val artist = if (primary != null) {
            (0 until primary.length()).mapNotNull { primary.optJSONObject(it)?.optString("name") }.filter { it.isNotBlank() }.joinToString(", ")
        } else item.optString("subtitle", "Unknown artist")
        val imageValue = item.opt("image")
        val image = when (imageValue) {
            is JSONArray -> imageValue.optJSONObject(imageValue.length() - 1)?.optString("url").orEmpty()
            is JSONObject -> imageValue.optString("url")
            is String -> imageValue
            else -> ""
        }
        return CloudSong(item.optString("id"), item.optString("name", item.optString("title", "Unknown title")),
            artist.ifBlank { "Unknown artist" }, item.optLong("duration", 0L) * 1000L, image,
            bestStream(item.optJSONArray("downloadUrl")))
    }

    private fun bestStream(array: JSONArray?): String {
        if (array == null) return ""
        var selected = ""
        var best = -1
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val quality = item.optString("quality").replace("kbps", "").toIntOrNull() ?: 0
            val link = item.optString("url")
            if (link.startsWith("https://") && quality > best) { best = quality; selected = link }
        }
        return selected
    }

    private fun renderSongs() {
        list.removeAllViews()
        songs.forEachIndexed { index, song ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = panel()
                setPadding(dp(10), dp(10), dp(12), dp(10))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
                isClickable = true
                setOnClickListener { play(index) }
                setOnLongClickListener { loadLyrics(song); true }
            }
            val art = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply { setColor(0xFF292431.toInt()); cornerRadius = dp(10).toFloat() }
            }
            row.addView(art)
            if (song.artwork.isNotBlank()) lifecycleScope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    try {
                        val c = URL(song.artwork).openConnection() as HttpURLConnection
                        c.connectTimeout = 8000; c.readTimeout = 10000
                        try { c.inputStream.use { BitmapFactory.decodeStream(it) } } finally { c.disconnect() }
                    } catch (_: Exception) { null }
                }
                if (!isFinishing && art.parent != null) art.setImageBitmap(bitmap)
            }
            val details = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, dp(8), 0)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            details.addView(TextView(this).apply {
                text = song.title; textSize = 14f; setTypeface(null, Typeface.BOLD)
                setTextColor(0xFFFFFFFF.toInt()); maxLines = 1
            })
            details.addView(TextView(this).apply {
                text = song.artist + " · " + formatDuration(song.durationMs)
                textSize = 12f; setTextColor(0xFFAAA5B2.toInt()); maxLines = 1
                setPadding(0, dp(3), 0, 0)
            })
            row.addView(details)
            row.addView(TextView(this).apply { text = "▶"; textSize = 18f; setTextColor(0xFFFF4D8D.toInt()) })
            list.addView(row)
        }
    }

    private fun play(index: Int) {
        if (index !in songs.indices) return
        val player = controller
        if (player == null) {
            Toast.makeText(this, "Player is starting. Tap again shortly.", Toast.LENGTH_SHORT).show()
            return
        }
        status.text = "Preparing " + songs[index].title + "…"
        lifecycleScope.launch {
            try {
                val queueSongs = withContext(Dispatchers.IO) {
                    val ready = mutableListOf<CloudSong>()
                    for (song in songs.drop(index).take(12)) {
                        val stream = if (song.streamUrl.isNotBlank()) song.streamUrl else resolveStream(song.id)
                        if (stream.startsWith("https://")) ready.add(song.copy(streamUrl = stream))
                        if (ready.size >= 10) break
                    }
                    ready
                }
                if (queueSongs.isEmpty()) throw IllegalStateException("No playable stream returned by the music service.")
                val queue = queueSongs.map { song ->
                    Media3Item.Builder().setUri(song.streamUrl).setMediaId("jiosaavn:" + song.id)
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist)
                            .setArtworkUri(android.net.Uri.parse(song.artwork).takeIf { song.artwork.isNotBlank() }).build()).build()
                }
                player.setMediaItems(queue, 0, 0L)
                player.prepare()
                player.play()
                getSharedPreferences("mediahub_settings", MODE_PRIVATE).edit()
                    .putString("island_mode", "music").putString("island_title", queueSongs.first().title).apply()
                status.text = "Playing JioSaavn · " + queueSongs.first().title + " · " + queueSongs.size + " queued"
            } catch (e: Exception) {
                status.text = "Playback failed: " + (e.localizedMessage ?: "Stream unavailable")
                Toast.makeText(this@CometMusicActivity, status.text, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun resolveStream(id: String): String {
        val json = requestJson("https://jio.shnwaz.dev/api/songs/" + URLEncoder.encode(id, "UTF-8"))
        val data = json.optJSONObject("data") ?: json
        return bestStream(data.optJSONArray("downloadUrl"))
    }

    private fun loadLyrics(song: CloudSong) {
        status.text = "Loading lyrics…"
        lifecycleScope.launch {
            try {
                val lyrics = withContext(Dispatchers.IO) {
                    val json = requestJson("https://jio.shnwaz.dev/api/lyrics/" + URLEncoder.encode(song.id, "UTF-8"))
                    val data = json.optJSONObject("data") ?: json
                    data.optString("lyrics", data.optString("text", "Lyrics not available."))
                }
                AlertDialog.Builder(this@CometMusicActivity).setTitle(song.title)
                    .setMessage(lyrics.ifBlank { "Lyrics not available." }).setPositiveButton("CLOSE", null).show()
                status.text = "Lyrics loaded"
            } catch (e: Exception) { status.text = "Lyrics unavailable: " + (e.localizedMessage ?: "Try again later") }
        }
    }

    private fun requestJson(endpoint: String): JSONObject {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.connectTimeout = 12000; connection.readTimeout = 18000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "MEDIAHUB-PRO Android")
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("Music API HTTP " + code)
            return JSONObject(body)
        } finally { connection.disconnect() }
    }

    private fun showAbout() {
        AlertDialog.Builder(this).setTitle("Online music")
            .setMessage("JioSaavn search, streaming, charts and lyrics use an unofficial third-party API. MEDIAHUB-PRO is not affiliated with JioSaavn. Availability and playback depend on that service and the rights holders. Audius is available as a separate source.")
            .setPositiveButton("OK", null).show()
    }

    private fun chip(label: String, primary: Boolean, click: () -> Unit) = TextView(this).apply {
        text = label; textSize = 10f; setTypeface(null, Typeface.BOLD)
        setTextColor(if (primary) 0xFF08070B.toInt() else 0xFFFFA0B8.toInt())
        background = GradientDrawable().apply {
            setColor(if (primary) 0xFFFF4D8D.toInt() else 0xFF191922.toInt())
            cornerRadius = dp(12).toFloat()
        }
        setPadding(dp(12), dp(10), dp(12), dp(10))
        setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(7) }
    }

    private fun panel() = GradientDrawable().apply {
        setColor(0xFF17151E.toInt()); cornerRadius = dp(14).toFloat(); setStroke(dp(1), 0x22FFFFFF)
    }
    private fun formatDuration(ms: Long) = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onStop() {
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    private data class CloudSong(
        val id: String, val title: String, val artist: String,
        val durationMs: Long, val artwork: String, val streamUrl: String
    )
}
