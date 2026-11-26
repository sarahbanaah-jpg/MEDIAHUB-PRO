package com.mediahub.rebuilt.ui.music

import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.mediahub.rebuilt.service.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Epidemic Sound catalogue integration. The API key is entered on-device and encrypted with Android Keystore. */
class EpidemicSoundActivity : AppCompatActivity() {
    private val tracks = mutableListOf<EsTrack>()
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private lateinit var searchField: EditText
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var apiKey: String? = null
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiKey = readApiKey()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(12))
            setBackgroundColor(0xFF08080D.toInt())
        }
        root.addView(TextView(this).apply {
            text = "EPIDEMIC SOUND"
            textSize = 23f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Official music catalogue · collections · search"
            textSize = 12f
            setTextColor(0xFFFF8FAF.toInt())
            setPadding(0, dp(4), 0, dp(14))
        })
        val searchRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = panel()
            setPadding(dp(12), 0, dp(8), 0)
        }
        searchField = EditText(this).apply {
            hint = "Search tracks, artists or moods"
            setHintTextColor(0xFF898592.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            setSingleLine(true)
            background = null
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
        }
        searchRow.addView(searchField)
        searchRow.addView(actionText("SEARCH") { searchTracks(searchField.text.toString().trim()) })
        root.addView(searchRow)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(8))
        }
        actions.addView(chip("COLLECTIONS", true) { loadCollections() })
        actions.addView(chip("API KEY", false) { showApiKeyDialog() })
        actions.addView(chip("CLEAR", false) {
            searchField.setText("")
            loadCollections()
        })
        root.addView(actions)
        status = TextView(this).apply {
            text = "Connecting to Epidemic Sound…"
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

        if (apiKey.isNullOrBlank()) showApiKeyDialog()
        else loadCollections()
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, android.content.ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try { controller = future.get() }
            catch (_: Exception) { if (!isFinishing) status.text = "MEDIAHUB-PRO player unavailable." }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun showApiKeyDialog() {
        val input = EditText(this).apply {
            hint = "Paste your Epidemic Sound API key"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(0xFF202020.toInt())
        }
        AlertDialog.Builder(this)
            .setTitle("Connect Epidemic Sound")
            .setMessage("Enter your API key. It will be encrypted on this device using Android Keystore.")
            .setView(input)
            .setCancelable(apiKey?.isNotBlank() == true)
            .setPositiveButton("SAVE & CONNECT") { _, _ ->
                val value = input.text.toString().trim().removePrefix("Bearer ").trim()
                if (value.isBlank()) {
                    status.text = "An API key is required to connect."
                } else {
                    try {
                        saveApiKey(value)
                        apiKey = value
                        loadCollections()
                    } catch (e: Exception) {
                        status.text = "Could not securely save the API key: ${e.localizedMessage ?: "error"}"
                    }
                }
            }
            .setNegativeButton(if (apiKey.isNullOrBlank()) "CANCEL" else "KEEP CURRENT KEY", null)
            .show()
    }

    private fun loadCollections() {
        val key = apiKey
        if (key.isNullOrBlank()) { showApiKeyDialog(); return }
        fetchTracks("Loading collections…") {
            val json = requestJson("/v0/collections?limit=20")
            val collections = json.optJSONArray("collections") ?: JSONArray()
            val parsed = mutableListOf<EsTrack>()
            val names = mutableListOf<String>()
            for (i in 0 until collections.length()) {
                val collection = collections.optJSONObject(i) ?: continue
                names.add(collection.optString("name"))
                val items = collection.optJSONArray("tracks") ?: JSONArray()
                for (j in 0 until items.length()) {
                    val track = items.optJSONObject(j) ?: continue
                    parsed.add(parseTrack(track))
                }
            }
            val unique = parsed.distinctBy { it.id }
            Pair(unique, if (unique.isEmpty()) "No tracks are available for this API key."
                else "\${names.size} collections · \${unique.size} tracks")
        }
    }

    private fun searchTracks(query: String) {
        if (query.isBlank()) { loadCollections(); return }
        fetchTracks("Searching Epidemic Sound…") {
            val path = "/v0/tracks/search?term=" + URLEncoder.encode(query, "UTF-8") + "&limit=60"
            val json = requestJson(path)
            val array = json.optJSONArray("tracks") ?: JSONArray()
            val parsed = mutableListOf<EsTrack>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                parsed.add(parseTrack(item))
            }
            val unique = parsed.distinctBy { it.id }
            Pair(unique, if (unique.isEmpty()) "No matching tracks found." else "Search results · \${unique.size} tracks")
        }
    }

    private fun fetchTracks(loadingText: String, work: () -> Pair<List<EsTrack>, String>) {
        if (loading) return
        loading = true
        status.text = loadingText
        list.removeAllViews()
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { work() }
                tracks.clear()
                tracks.addAll(result.first)
                renderTracks()
                status.text = result.second
            } catch (e: Exception) {
                val message = e.localizedMessage ?: "Unknown error"
                status.text = when {
                    message.contains("HTTP 401") -> "API key rejected. Tap API KEY and enter a valid key."
                    message.contains("HTTP 403") -> "Access denied. Check your Epidemic Sound API access and preview permissions."
                    message.contains("HTTP 429") -> "Rate limit reached. Please wait and retry."
                    else -> "Epidemic Sound unavailable: $message"
                }
                list.addView(TextView(this@EpidemicSoundActivity).apply {
                    text = "TAP TO RETRY COLLECTIONS"
                    setTextColor(0xFFFF4D8D.toInt())
                    setPadding(0, dp(18), 0, dp(18))
                    setOnClickListener { loadCollections() }
                })
            } finally { loading = false }
        }
    }

    private fun parseTrack(item: JSONObject): EsTrack {
        val artists = item.optJSONArray("mainArtists")
        val artist = if (artists != null) (0 until artists.length()).mapNotNull { artists.optString(it).takeIf(String::isNotBlank) }.joinToString(", ")
            else "Unknown artist"
        val images = item.optJSONObject("images")
        val artwork = images?.optString("M")?.takeIf { it.isNotBlank() && it != "null" }
            ?: images?.optString("default").orEmpty()
        return EsTrack(
            id = item.optString("id"),
            title = item.optString("title", "Untitled"),
            artist = artist,
            durationSeconds = item.optLong("length", 0L),
            artwork = artwork,
            bpm = item.optInt("bpm", 0),
            previewOnly = item.optBoolean("isPreviewOnly", false)
        )
    }

    private fun renderTracks() {
        list.removeAllViews()
        if (tracks.isEmpty()) return
        tracks.forEachIndexed { index, song ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = panel()
                setPadding(dp(10), dp(10), dp(12), dp(10))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
                isClickable = true
                setOnClickListener { playTrack(index) }
            }
            val art = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(54), dp(54))
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply { setColor(0xFF292431.toInt()); cornerRadius = dp(10).toFloat() }
            }
            row.addView(art)
            if (song.artwork.isNotBlank()) lifecycleScope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    try {
                        val connection = URL(song.artwork).openConnection() as HttpURLConnection
                        connection.connectTimeout = 8000
                        connection.readTimeout = 10000
                        try { connection.inputStream.use { BitmapFactory.decodeStream(it) } }
                        finally { connection.disconnect() }
                    } catch (_: Exception) { null }
                }
                if (!isFinishing && art.parent != null && bitmap != null) art.setImageBitmap(bitmap)
            }
            val details = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, dp(8), 0)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            details.addView(TextView(this).apply {
                text = song.title
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(0xFFFFFFFF.toInt())
                maxLines = 1
            })
            details.addView(TextView(this).apply {
                text = song.artist + " · " + formatDuration(song.durationSeconds) +
                    if (song.bpm > 0) " · ${song.bpm} BPM" else ""
                textSize = 11f
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
    }

    private fun playTrack(index: Int) {
        if (index !in tracks.indices) return
        val player = controller
        if (player == null) {
            Toast.makeText(this, "Player is starting. Tap again shortly.", Toast.LENGTH_SHORT).show()
            return
        }
        val song = tracks[index]
        status.text = "Preparing ${song.title}…"
        lifecycleScope.launch {
            try {
                val streamUrl = withContext(Dispatchers.IO) {
                    val json = requestJson("/v0/tracks/${URLEncoder.encode(song.id, "UTF-8")}/download?format=mp3&quality=normal")
                    json.optString("url").takeIf { it.startsWith("https://") }
                        ?: throw IllegalStateException("The API did not return a playable download URL.")
                }
                val media = Media3Item.Builder().setUri(streamUrl).setMediaId("epidemicsound:${song.id}")
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist)
                        .setArtworkUri(android.net.Uri.parse(song.artwork).takeIf { it.isNotBlank() }).build()).build()
                player.setMediaItem(media)
                player.prepare()
                player.play()
                getSharedPreferences("mediahub_settings", MODE_PRIVATE).edit()
                    .putString("island_mode", "music").putString("island_title", song.title).apply()
                status.text = "Playing Epidemic Sound · ${song.title}"
            } catch (e: Exception) {
                status.text = "Playback unavailable: ${e.localizedMessage ?: "Track access denied"}"
            }
        }
    }

    private fun requestJson(path: String): JSONObject {
        val key = apiKey ?: throw IllegalStateException("API key is missing.")
        val connection = URL("https://api.epidemicsound.com" + path).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Authorization", "Bearer " + key)
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("Epidemic Sound HTTP $code")
            return JSONObject(body)
        } finally { connection.disconnect() }
    }

    private fun saveApiKey(value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateEncryptionKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        getSharedPreferences("epidemic_auth", MODE_PRIVATE).edit()
            .putString("api_key_ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("api_key_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    private fun readApiKey(): String? {
        return try {
            val prefs = getSharedPreferences("epidemic_auth", MODE_PRIVATE)
            val ciphertext = prefs.getString("api_key_ciphertext", null) ?: return null
            val iv = Base64.decode(prefs.getString("api_key_iv", null), Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateEncryptionKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) { null }
    }

    private fun getOrCreateEncryptionKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }

    private fun actionText(label: String, click: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 11f
        setTypeface(null, Typeface.BOLD)
        setTextColor(0xFFFF4D8D.toInt())
        setPadding(dp(10), dp(12), dp(8), dp(12))
        setOnClickListener { click() }
    }

    private fun chip(label: String, primary: Boolean, click: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 10f
        setTypeface(null, Typeface.BOLD)
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
        setColor(0xFF17151E.toInt())
        cornerRadius = dp(14).toFloat()
        setStroke(dp(1), 0x22FFFFFF)
    }
    private fun formatDuration(seconds: Long) = "%d:%02d".format(seconds / 60, seconds % 60)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onStop() {
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    private data class EsTrack(
        val id: String,
        val title: String,
        val artist: String,
        val durationSeconds: Long,
        val artwork: String,
        val bpm: Int,
        val previewOnly: Boolean
    )

    companion object { private const val KEY_ALIAS = "mediahub_epidemic_sound_api_key" }
}
