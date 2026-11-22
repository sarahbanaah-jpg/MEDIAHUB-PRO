package com.mediahub.rebuilt.remote

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
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
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors

/** QR-paired playback remote. Uses existing Wi-Fi or creates a temporary internet-free hotspot. */
class MediaHubRemoteActivity : AppCompatActivity() {
    private val port = 8765
    private val token = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var server: ServerSocket? = null
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var hotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null
    private lateinit var status: TextView
    private lateinit var wifiQr: ImageView
    private lateinit var remoteQr: ImageView
    private lateinit var wifiDetails: TextView
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startOfflineHotspot()
        else status.text = "Nearby Wi-Fi permission is needed to create an offline remote connection."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(30), dp(20), dp(28))
            setBackgroundColor(0xFF08080D.toInt())
        }
        root.addView(TextView(this).apply {
            text = "MediaHub Remote"; textSize = 28f; setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = "Control playback from another phone. No internet connection is needed."
            textSize = 14f; setTextColor(0xFFB9B6C3.toInt()); setPadding(0, dp(10), 0, dp(12))
        })
        wifiDetails = TextView(this).apply {
            text = "Checking local network…"; textSize = 12f; setTextColor(0xFFFFB1C8.toInt())
            gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(8))
        }
        root.addView(wifiDetails)
        wifiQr = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(150), dp(150)).apply { bottomMargin = dp(6) }
            setBackgroundColor(Color.WHITE); setPadding(dp(5), dp(5), dp(5), dp(5))
        }
        root.addView(wifiQr)
        root.addView(TextView(this).apply {
            text = "1 · Scan to join the temporary MediaHub Wi-Fi (only shown when no Wi-Fi is available)"
            textSize = 11f; setTextColor(0xFFB9B6C3.toInt()); gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        })
        remoteQr = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(190), dp(190)).apply { bottomMargin = dp(6) }
            setBackgroundColor(Color.WHITE); setPadding(dp(5), dp(5), dp(5), dp(5))
        }
        root.addView(remoteQr)
        root.addView(TextView(this).apply {
            text = "2 · After joining the network, scan this code to open the remote controls"
            textSize = 11f; setTextColor(0xFFB9B6C3.toInt()); gravity = Gravity.CENTER
        })
        status = TextView(this).apply {
            textSize = 13f; gravity = Gravity.CENTER; setTextColor(0xFFFF79B8.toInt())
            setPadding(0, dp(14), 0, 0)
        }
        root.addView(status)
        root.addView(TextView(this).apply {
            text = "Security: a random pairing token protects this session. Keep the QR codes private. Leaving this screen stops the remote and temporary hotspot."
            textSize = 11f; setTextColor(0xFF8F8999.toInt()); setPadding(0, dp(14), 0, 0)
        })
        setContentView(root)

        val tokenSession = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, tokenSession).buildAsync()
        controllerFuture = future
        future.addListener({
            try { controller = future.get() }
            catch (_: Exception) { runOnUiThread { status.text = "Player service unavailable. Start playback, then reopen Remote." } }
        }, ContextCompat.getMainExecutor(this))

        val ip = localIpv4()
        if (ip != null) {
            wifiQr.visibility = android.view.View.GONE
            wifiDetails.visibility = android.view.View.GONE
            remoteQr.layoutParams = (remoteQr.layoutParams as LinearLayout.LayoutParams).apply {
                width = dp(230); height = dp(230)
            }
            startRemote(ip)
        } else {
            requestHotspotPermission()
        }
    }

    private fun requestHotspotPermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.NEARBY_WIFI_DEVICES
            else Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            startOfflineHotspot()
        } else {
            permissionRequest.launch(permission)
        }
    }

    private fun startOfflineHotspot() {
        try {
            val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            wifiDetails.text = "Starting a temporary local-only Wi-Fi network…"
            wifi.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                    hotspotReservation = reservation
                    try {
                        @Suppress("DEPRECATION")
                        val legacy = reservation.wifiConfiguration
                        val ssid: String
                        val password: String
                        if (Build.VERSION.SDK_INT >= 30) {
                            val config = reservation.softApConfiguration
                            ssid = config.ssid
                            password = config.passphrase
                        } else {
                            ssid = legacy?.SSID?.trim('"') ?: "MediaHub"
                            password = legacy?.preSharedKey?.trim('"') ?: ""
                        }
                        wifiDetails.text = "Network: $ssid\nPassword: $password"
                        renderQr(wifiQr, "WIFI:T:WPA;S:$ssid;P:$password;;", 300)
                        wifiQr.visibility = android.view.View.VISIBLE
                        wifiDetails.visibility = android.view.View.VISIBLE
                        Handler(Looper.getMainLooper()).postDelayed({
                            val ip = localIpv4()
                            if (ip == null) status.text = "Hotspot is ready. If the remote QR does not appear, reconnect and reopen Remote."
                            else startRemote(ip)
                        }, 700)
                    } catch (error: Exception) {
                        status.text = "Hotspot started, but pairing details could not be read: ${error.localizedMessage}"
                    }
                }

                override fun onStopped() {
                    runOnUiThread { status.text = "Temporary hotspot stopped by Android." }
                }

                override fun onFailed(reason: Int) {
                    runOnUiThread { status.text = "Could not create offline hotspot (system reason $reason). Try enabling Wi-Fi and retry." }
                }
            }, Handler(Looper.getMainLooper()))
        } catch (error: Exception) {
            status.text = "Offline hotspot unavailable: ${error.localizedMessage ?: "system restriction"}"
        }
    }

    private fun startRemote(ip: String) {
        val url = "http://$ip:$port/?token=$token"
        try {
            renderQr(remoteQr, url, 480)
            status.text = "Remote ready · $ip:$port\nPair only with a device you trust."
            executor.execute { runServer() }
        } catch (error: Exception) {
            status.text = "Could not create pairing QR: ${error.localizedMessage}"
        }
    }

    private fun renderQr(view: ImageView, value: String, size: Int) {
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (y in 0 until size) for (x in 0 until size) {
            bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
        view.setImageBitmap(bitmap)
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
        return """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>MediaHub Remote</title><style>body{background:#08080d;color:#fff;font-family:system-ui;text-align:center;padding:24px}h1{color:#ff6b9f}button{background:#191922;color:white;border:1px solid #64436e;border-radius:16px;padding:18px;margin:7px;font-size:18px;min-width:110px}button:active{background:#a52b76}.row{margin:18px}</style></head><body><h1>MEDIAHUB PRO</h1><p>Playback remote · offline local connection</p><div class="row"><button onclick="send('previous')">⏮ Previous</button><button onclick="send('toggle')">▶ / ❚❚</button><button onclick="send('next')">Next ⏭</button></div><div class="row"><button onclick="send('volume_down')">Volume −</button><button onclick="send('volume_up')">Volume +</button></div><p id="status">Paired securely.</p><script>function send(c){fetch('$base'+c).then(r=>document.getElementById('status').textContent=r.ok?'Command sent':'Authorization failed').catch(()=>document.getElementById('status').textContent='Connection lost')}</script></body></html>"""
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
        try { hotspotReservation?.close() } catch (_: Exception) { }
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}