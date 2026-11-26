package com.mediahub.rebuilt.ui.music

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Cross-service music catalogue search using MusicAPI's public catalog endpoints. */
class MusicApiActivity : AppCompatActivity() {
    private val clientId = "3d9fe1f5-bc89-4431-a199-d716daa4da5c"
    private lateinit var searchField: EditText
    private lateinit var status: TextView
    private lateinit var results: LinearLayout
    private var sources = listOf("spotify", "appleMusic", "deezer", "tidal", "youtube")
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(12))
            setBackgroundColor(0xFF08080D.toInt())
        }
        root.addView(TextView(this).apply {
            text = "GLOBAL MUSIC"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt())
        })
        root.addView(TextView(this).apply {
            text = "Search songs across Spotify, Apple Music, Deezer, TIDAL and YouTube"
            textSize = 12f
            setTextColor(0xFFFF8FAF.toInt())
            setPadding(0, dp(5), 0, dp(14))
        })
        val searchRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = panel()
            setPadding(dp(12), 0, dp(10), 0)
        }
        searchField = EditText(this).apply {
            hint = "Song title or artist (e.g. Anguka Nayo)"
            setHintTextColor(0xFF898592.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            setSingleLine(true)
            background = null
            layoutParams = LinearLayout.LayoutParams(0, dp(50), 1f)
            setOnEditorActionListener { _, _, _ -> search(); true }
        }
        searchRow.addView(searchField)
        searchRow.addView(action("SEARCH", true) { search() })
        root.addView(searchRow)
        val quick = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(8))
        }
        quick.addView(action("KENYAN MUSIC", false) {
            searchField.setText("Kenyan hits")
            search()
        })
        quick.addView(action("CLEAR", false) { searchField.setText(""); results.removeAllViews(); status.text = "Search across music services." })
        root.addView(quick)
        status = TextView(this).apply {
            text = "Connecting to music catalogues…"
            textSize = 12f
            setTextColor(0xFFFFA0B8.toInt())
            setPadding(0, dp(4), 0, dp(10))
        }
        root.addView(status)
        val scroll = ScrollView(this)
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(results)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        loadSources()
    }

    private fun loadSources() {
        lifecycleScope.launch {
            try {
                val live = withContext(Dispatchers.IO) {
                    val json = request("GET", "/public/search/introspection")
                    val arr = json.optJSONArray("sources") ?: JSONArray()
                    (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
                }
                if (live.isNotEmpty()) sources = live
                status.text = "Ready · ${sources.joinToString { displaySource(it) }}"
            } catch (e: Exception) {
                status.text = "Catalogue ready to search · source list could not be refreshed."
            }
        }
    }

    private fun search() {
        val query = searchField.text.toString().trim()
        if (query.isBlank()) {
            Toast.makeText(this, "Enter a song or artist name", Toast.LENGTH_SHORT).show()
            return
        }
        if (loading) return
        loading = true
        status.text = "Searching music services…"
        results.removeAllViews()
        lifecycleScope.launch {
            try {
                val found = withContext(Dispatchers.IO) {
                    val body = JSONObject().put("type", "track").put("track", query).put("sources", JSONArray(sources))
                    val json = request("POST", "/public/search", body)
                    val array = json.optJSONArray("tracks") ?: JSONArray()
                    val list = mutableListOf<CatalogTrack>()
                    for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        if (item.optString("status") != "success") continue
                        val data = item.optJSONObject("data") ?: continue
                        val url = data.optString("url")
                        if (url.startsWith("https://")) {
                            val artists = data.optJSONArray("artistNames")
                            val artist = if (artists != null) (0 until artists.length()).map { artists.optString(it) }.joinToString(", ") else "Unknown artist"
                            list.add(CatalogTrack(item.optString("source"), data.optString("name", "Unknown title"), artist, data.optString("albumName"), data.optString("imageUrl"), data.optString("previewUrl"), url))
                        }
                    }
                    list.distinctBy { it.source + "|" + it.url }
                }
                found.forEach { addTrack(it) }
                status.text = if (found.isEmpty()) "No matches found. Try a different spelling or artist name."
                    else "${found.size} results across music services · tap LISTEN to open the official player"
            } catch (e: Exception) {
                status.text = "Music search failed: ${e.localizedMessage ?: "Check your internet connection."}"
                results.addView(TextView(this@MusicApiActivity).apply {
                    text = "TAP TO RETRY"
                    setTextColor(0xFFFF4D8D.toInt())
                    setPadding(0, dp(18), 0, dp(18))
                    setOnClickListener { search() }
                })
            } finally { loading = false }
        }
    }

    private fun addTrack(track: CatalogTrack) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panel()
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) }
        }
        card.addView(TextView(this).apply {
            text = track.title
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt())
        })
        card.addView(TextView(this).apply {
            text = "${track.artist}  ·  ${displaySource(track.source)}"
            textSize = 12f
            setTextColor(0xFFFFA0B8.toInt())
            setPadding(0, dp(4), 0, dp(3))
        })
        if (track.album.isNotBlank()) card.addView(TextView(this).apply {
            text = track.album
            textSize = 11f
            setTextColor(0xFFAAA5B2.toInt())
        })
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        actions.addView(action("LISTEN", true) { openOfficial(track.url) })
        actions.addView(action("IN-APP PLAYER", false) { openEmbed(track.url) })
        card.addView(actions)
        results.addView(card)
    }

    private fun openOfficial(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: Exception) { Toast.makeText(this, "No music app can open this link.", Toast.LENGTH_SHORT).show() }
    }

    private fun openEmbed(url: String) {
        status.text = "Preparing official embedded player…"
        lifecycleScope.launch {
            try {
                val embedUrl = withContext(Dispatchers.IO) {
                    request("POST", "/public/embed/url", JSONObject().put("url", url), authenticated = false)
                        .optString("embedUrl")
                }
                if (!embedUrl.startsWith("https://")) throw IllegalStateException("This service did not provide an embedded player.")
                val web = WebView(this@MusicApiActivity).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = true
                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()
                    loadUrl(embedUrl)
                }
                AlertDialog.Builder(this@MusicApiActivity)
                    .setTitle("Now Playing")
                    .setView(web.apply { layoutParams = android.view.ViewGroup.LayoutParams(-1, dp(230)) })
                    .setPositiveButton("DONE") { _, _ -> web.destroy() }
                    .setOnCancelListener { web.destroy() }
                    .show()
                status.text = "Embedded player ready."
            } catch (e: Exception) {
                status.text = "Embedded player unavailable: ${e.localizedMessage ?: "Use LISTEN to open the service."}"
            }
        }
    }

    private fun request(method: String, path: String, body: JSONObject? = null, authenticated: Boolean = true): JSONObject {
        val connection = URL("https://api.musicapi.com" + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 12000
        connection.readTimeout = 20000
        connection.setRequestProperty("Accept", "application/json")
        if (authenticated) connection.setRequestProperty("Authorization", "Token $clientId")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("MusicAPI HTTP $code" + if (text.isBlank()) "" else ": $text")
            return JSONObject(text)
        } finally { connection.disconnect() }
    }

    private fun displaySource(source: String) = when (source) {
        "appleMusic" -> "Apple Music"
        "youtube" -> "YouTube"
        else -> source.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private fun action(label: String, primary: Boolean, click: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 10f
        setTypeface(null, Typeface.BOLD)
        setTextColor(if (primary) 0xFF08080D.toInt() else 0xFFFFA0B8.toInt())
        background = GradientDrawable().apply {
            setColor(if (primary) 0xFFFF4D8D.toInt() else 0xFF191922.toInt())
            cornerRadius = dp(12).toFloat()
        }
        setPadding(dp(13), dp(10), dp(13), dp(10))
        setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(7) }
    }

    private fun panel() = GradientDrawable().apply {
        setColor(0xFF17151E.toInt())
        cornerRadius = dp(15).toFloat()
        setStroke(dp(1), 0x22FFFFFF)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private data class CatalogTrack(
        val source: String, val title: String, val artist: String,
        val album: String, val image: String, val preview: String, val url: String
    )
}
