package com.mediahub.rebuilt.remote

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.common.util.concurrent.ListenableFuture
import com.mediahub.rebuilt.service.PlaybackService
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors

/**
 * Same-Wi-Fi browser remote for this phone's Media3 playback session.
 * A fresh random pairing token is required for every session.
 */
class MediaHubRemoteActivity : AppCompatActivity() {
    private val port = 8765
    private val token = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var server: ServerSocket? = null
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(32), dp(22), dp(28))
            setBackgroundColor(0xFF0B0618.toInt())
        }
        root.addView(TextView(this).apply {
            text = "MediaHub Remote"
            textSize = 27f
            setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = "Scan this QR code from another device on the same Wi-Fi network or a phone hotspot. No internet connection is required; both devices only need to be on the same local network. Use the browser controls to control playback on this phone."
            textSize = 14f
            setTextColor(0xFFB9A9C9.toInt())
            setPadding(0, dp(12), 0, dp(18))
        })
        val qr = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(230), dp(230)).apply { bottomMargin = dp(16) }
            setBackgroundColor(Color.WHITE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        root.addView(qr)
        status = TextView(this).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFFFF79B8.toInt())
        }
        root.addView(status)
        root.addView(TextView(this).apply {
            text = "Security: a new random pairing token is generated each time. Stop sharing by leaving this screen. Only pair on a trusted private network or your own hotspot. Internet access is not required."
            textSize = 12f
            setTextColor(0xFF9A8BB0.toInt())
            setPadding(0, dp(18), 0, 0)
        })
        setContentView(root)

        val ip = localIpv4()
        if (ip == null) {
            status.text = "Connect both devices to the same Wi-Fi network or turn on a phone hotspot. Internet access is not required."
            return
        }
        val remoteUrl = "http://$ip:$port/?token=$token"
        try {
            val matrix = QRCodeWriter().encode(remoteUrl, BarcodeFormat.QR_CODE, 480, 480)
            val bitmap = Bitmap.createBitmap(480, 480, Bitmap.Config.RGB_565)
            for (y in 0 until 480) for (x in 0 until 480) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
            qr.setImageBitmap(bitmap)
            status.text = "Open in browser: $ip:$port\nWaiting for a paired device…"
        } catch (error: Exception) {
            status.text = "Could not create pairing QR code: ${error.localizedMessage}"
            return
        }

        val tokenSession = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, tokenSession).buildAsync()
        controllerFuture = future
        future.addListener({
            try { controller = future.get() }
            catch (_: Exception) { runOnUiThread { status.text = "Player service is unavailable. Start playback first, then reopen Remote." } }
        }, ContextCompat.getMainExecutor(this))

        executor.execute { runServer() }
    }

    private fun runServer() {
        try {
            ServerSocket(port).use { socket ->
                server = socket
                while (!socket.isClosed && !isFinishing) {
                    val client = try { socket.accept() } catch (_: Exception) { break }
                    client.use { connection ->
                        connection.soTimeout = 4000
                        val reader = BufferedReader(InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))
                        val requestLine = reader.readLine().orEmpty()
                        while (true) { val line = reader.readLine() ?: break; if (line.isEmpty()) break }
                        val target = requestLine.split(" ").getOrNull(1).orEmpty()
                        val uri = try { Uri.parse("http://localhost$target") } catch (_: Exception) { Uri.EMPTY }
                        val supplied = uri.getQueryParameter("token").orEmpty()
                        val authorized = MessageDigest.isEqual(supplied.toByteArray(), token.toByteArray())
                        val response = when {
                            !authorized -> "HTTP/1.1 403 Forbidden\r\nContent-Type: text/plain\r\nConnection: close\r\n\r\nPairing token required"
                            uri.path == "/control" -> {
                                dispatch(uri.getQueryParameter("cmd").orEmpty())
                                "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nConnection: close\r\n\r\nCommand accepted"
                            }
                            else -> "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nConnection: close\r\n\r\n" + controlPage()
                        }
                        connection.getOutputStream().use { out -> out.write(response.toByteArray(StandardCharsets.UTF_8)); out.flush() }
                    }
                }
            }
        } catch (error: Exception) {
            runOnUiThread { if (!isFinishing) status.text = "Remote server stopped: ${error.localizedMessage}" }
        }
    }

    private fun dispatch(command: String) {
        runOnUiThread {
            val player = controller ?: return@runOnUiThread
            when (command) {
                "play" -> player.play()
                "pause" -> player.pause()
                "next" -> player.seekToNext()
                "previous" -> player.seekToPrevious()
                "toggle" -> if (player.isPlaying) player.pause() else player.play()
                "volume_up" -> player.increaseDeviceVolume()
                "volume_down" -> player.decreaseDeviceVolume()
            }
        }
    }

    private fun controlPage(): String {
        val base = "/control?token=$token&cmd="
        return """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>MediaHub Remote</title><style>body{background:#0b0618;color:#fff;font-family:system-ui;text-align:center;padding:24px}h1{color:#ff6bb5}button{background:#211632;color:white;border:1px solid #64436e;border-radius:16px;padding:18px;margin:7px;font-size:18px;min-width:110px}button:active{background:#a52b76}.row{margin:18px}</style></head><body><h1>MEDIAHUB PRO</h1><p>Playback remote</p><div class="row"><button onclick="send('previous')">⏮ Previous</button><button onclick="send('toggle')">▶ / ❚❚</button><button onclick="send('next')">Next ⏭</button></div><div class="row"><button onclick="send('volume_down')">Volume −</button><button onclick="send('volume_up')">Volume +</button></div><p id="status">Connected to this phone's local remote.</p><script>function send(c){fetch('$base'+c).then(r=>document.getElementById('status').textContent=r.ok?'Command sent':'Remote authorization failed').catch(()=>document.getElementById('status').textContent='Connection lost')}</script></body></html>"""
    }

    private fun localIpv4(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }?.hostAddress
        } catch (_: Exception) { null }
    }

    override fun onDestroy() {
        try { server?.close() } catch (_: Exception) { }
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
