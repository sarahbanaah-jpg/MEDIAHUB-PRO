package com.mediahub.rebuilt.transfer

import android.content.ContentValues
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.Executors

/** Native QR-paired MediaHub-to-MediaHub transfer plus Android Sharesheet interoperability. */
class MediaHubTransferActivity : AppCompatActivity() {
    private data class ShareFile(val uri: Uri, val name: String, val mime: String, val size: Long)
    private val io = Executors.newSingleThreadExecutor()
    @Volatile private var server: ServerSocket? = null
    @Volatile private var activeToken: String? = null
    private var selectedFiles: List<ShareFile> = emptyList()
    private lateinit var status: TextView
    private lateinit var selectedLabel: TextView

    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        try {
            selectedFiles = uris.map { uri ->
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
                readShareFile(uri)
            }
            selectedLabel.text = "${selectedFiles.size} file(s) selected · ${formatBytes(selectedFiles.sumOf { it.size })}"
            status.text = "Ready to create a QR transfer session or share through another app."
        } catch (error: Exception) {
            status.text = "Could not prepare selected files: ${error.localizedMessage ?: "file access denied"}"
        }
    }

    private val scanner = registerForActivityResult(ScanContract()) { result ->
        val contents = result.contents ?: return@registerForActivityResult
        receiveQrSession(contents)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(34), dp(20), dp(28))
            setBackgroundColor(0xFF08080D.toInt())
        }
        root.addView(TextView(this).apply {
            text = "MEDIAHUB SHARE"; textSize = 11f; letterSpacing = .18f
            setTextColor(0xFFFF79B8.toInt())
        })
        root.addView(TextView(this).apply {
            text = "Move anything.\nKeep it yours."
            textSize = 29f; setTextColor(Color.WHITE); setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(8))
        })
        root.addView(TextView(this).apply {
            text = "Send files directly between MediaHub Pro devices with a private QR-paired local connection. No account or internet is needed when both phones share a Wi-Fi network."
            textSize = 13f; setTextColor(0xFFB9B6C3.toInt()); setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(0, 0, 0, dp(18))
        })
        selectedLabel = TextView(this).apply {
            text = "No files selected"
            textSize = 12f; setTextColor(0xFFFFB1C8.toInt()); setPadding(0, 0, 0, dp(10))
        }
        root.addView(selectedLabel)
        root.addView(actionButton("1 · Choose files to send") {
            picker.launch(arrayOf("*/*"))
        })
        root.addView(actionButton("2 · Create QR transfer session") {
            startQrSession()
        })
        root.addView(actionButton("Scan QR to receive files") {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Scan the sender's MediaHub QR code")
                setBeepEnabled(false)
                setOrientationLocked(false)
            }
            scanner.launch(options)
        })
        root.addView(actionButton("Share files with Xender / Quick Share") {
            if (selectedFiles.isEmpty()) picker.launch(arrayOf("*/*")) else shareSelectedFiles()
        })
        root.addView(actionButton("Share an installed app (APK)") { showInstalledApps() })
        status = TextView(this).apply {
            text = "Transfers are started only when you choose them. Review the selected files before sharing."
            textSize = 12f; setTextColor(0xFFFF79B8.toInt()); setPadding(0, dp(18), 0, 0)
        }
        root.addView(status)
        root.addView(TextView(this).apply {
            text = "Security: each QR session has a random one-time token and accepts one receiving device. Keep the QR private. Xender and Quick Share work through Android's standard share interface; MediaHub QR sessions are our own direct protocol."
            textSize = 11f; setTextColor(0xFF8F8999.toInt()); setPadding(0, dp(18), 0, 0)
        })
        setContentView(root)
        receiveSharedFiles(intent)
    }

    private fun actionButton(label: String, click: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(9) }
    }

    private fun readShareFile(uri: Uri): ShareFile {
        var name = "Shared-file"
        var size = -1L
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        if (size < 0L) throw IllegalStateException("$name does not provide a readable file size")
        return ShareFile(uri, name, contentResolver.getType(uri) ?: "application/octet-stream", size)
    }

    private fun shareSelectedFiles() {
        if (selectedFiles.isEmpty()) return
        val uris = ArrayList(selectedFiles.map { it.uri })
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(contentResolver, "MediaHub files", uris.first()).also { clip ->
                uris.drop(1).forEach { clip.addItem(android.content.ClipData.Item(it)) }
            }
        }
        startActivity(Intent.createChooser(send, "Share with Xender, Quick Share or another app"))
    }

    private fun startQrSession() {
        if (selectedFiles.isEmpty()) {
            Toast.makeText(this, "Choose files first.", Toast.LENGTH_SHORT).show()
            return
        }
        val ip = localIpv4()
        if (ip == null) {
            status.text = "No local Wi-Fi address found. Connect both phones to the same Wi-Fi network (internet is not required) and retry."
            return
        }
        try {
            server?.close()
            val socket = ServerSocket(0)
            socket.soTimeout = 180000
            server = socket
            val token = ByteArray(24).also { SecureRandom().nextBytes(it) }
                .joinToString("") { "%02x".format(it) }
            activeToken = token
            val payload = "mediahub://transfer?host=$ip&port=${socket.localPort}&token=$token"
            io.execute { serveOneReceiver(socket, token, selectedFiles.toList()) }
            showQrDialog(payload)
            status.text = "Waiting for one receiving device · ${selectedFiles.size} file(s) · session expires in 3 minutes."
        } catch (error: Exception) {
            status.text = "Could not start transfer session: ${error.localizedMessage ?: "local network unavailable"}"
        }
    }

    private fun showQrDialog(payload: String) {
        val size = 720
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (y in 0 until size) for (x in 0 until size) {
            bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
        val image = ImageView(this).apply {
            setImageBitmap(bitmap)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(280), dp(280)).apply { gravity = Gravity.CENTER }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            addView(image)
            addView(TextView(this@MediaHubTransferActivity).apply {
                text = "On the receiving phone, open MediaHub Share and tap Scan QR. Both devices must be on the same local Wi-Fi. Internet is not required."
                setTextColor(0xFFFFFFFF.toInt()); textSize = 13f
                setPadding(0, dp(12), 0, 0)
            })
        }
        AlertDialog.Builder(this).setTitle("Scan to receive")
            .setView(content)
            .setNegativeButton("Stop session") { _, _ -> stopServer() }
            .setOnCancelListener { stopServer() }
            .show()
    }

    private fun serveOneReceiver(socket: ServerSocket, token: String, files: List<ShareFile>) {
        try {
            socket.use { listening ->
                val client = listening.accept()
                client.use { peer ->
                    peer.soTimeout = 60000
                    val input = peer.getInputStream()
                    val output = peer.getOutputStream()
                    val hello = readLine(input)
                    val supplied = hello.removePrefix("HELLO ").trim()
                    if (!hello.startsWith("HELLO ") || !MessageDigest.isEqual(supplied.toByteArray(), token.toByteArray())) {
                        writeLine(output, "DENIED")
                        return
                    }
                    writeLine(output, "OK ${files.size}")
                    files.forEach { file ->
                        val encodedName = Base64.getUrlEncoder().withoutPadding().encodeToString(file.name.toByteArray(StandardCharsets.UTF_8))
                        val encodedMime = Base64.getUrlEncoder().withoutPadding().encodeToString(file.mime.toByteArray(StandardCharsets.UTF_8))
                        writeLine(output, "FILE\t$encodedName\t$encodedMime\t${file.size}")
                        val source = contentResolver.openInputStream(file.uri) ?: throw IllegalStateException("Cannot read ${file.name}")
                        source.use { stream -> copyExactly(stream, output, file.size) }
                        output.flush()
                    }
                    writeLine(output, "DONE")
                    runOnUiThread { status.text = "Transfer complete · ${files.size} file(s) sent." }
                }
            }
        } catch (error: Exception) {
            runOnUiThread { if (!isFinishing) status.text = "Transfer session ended: ${error.localizedMessage ?: "connection closed"}" }
        } finally {
            activeToken = null
            if (server === socket) server = null
        }
    }

    private fun receiveQrSession(payload: String) {
        val uri = try { Uri.parse(payload) } catch (_: Exception) { Uri.EMPTY }
        if (uri.scheme != "mediahub" || uri.host != "transfer") {
            status.text = "That QR code is not a MediaHub transfer session."
            return
        }
        val host = uri.getQueryParameter("host").orEmpty()
        val port = uri.getQueryParameter("port")?.toIntOrNull() ?: -1
        val token = uri.getQueryParameter("token").orEmpty()
        if (host.isBlank() || port !in 1..65535 || token.length != 48) {
            status.text = "The transfer QR code is invalid or incomplete."
            return
        }
        status.text = "Connecting securely to $host…"
        io.execute {
            try {
                Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress(host, port), 15000)
                    socket.soTimeout = 60000
                    val input = socket.getInputStream()
                    val output = socket.getOutputStream()
                    writeLine(output, "HELLO $token")
                    val response = readLine(input)
                    if (!response.startsWith("OK ")) throw IllegalStateException(if (response == "DENIED") "Pairing token rejected" else "Sender did not accept the connection")
                    val count = response.substringAfter("OK ").toIntOrNull()?.coerceIn(0, 500)
                        ?: throw IllegalStateException("Invalid file count")
                    var received = 0
                    repeat(count) {
                        val header = readLine(input).split('\t')
                        if (header.size != 4 || header[0] != "FILE") throw IllegalStateException("Invalid transfer file header")
                        val name = String(Base64.getUrlDecoder().decode(header[1]), StandardCharsets.UTF_8)
                            .substringAfterLast('/').substringAfterLast('\\')
                            .replace(Regex("[^A-Za-z0-9._-]"), "_").take(120).ifBlank { "Received-file" }
                        val mime = String(Base64.getUrlDecoder().decode(header[2]), StandardCharsets.UTF_8)
                        val size = header[3].toLongOrNull()?.takeIf { it >= 0L && it <= 4L * 1024L * 1024L * 1024L }
                            ?: throw IllegalStateException("Invalid file size")
                        saveIncomingStream(input, name, mime, size)
                        received++
                        runOnUiThread { status.text = "Receiving files · $received of $count complete" }
                    }
                    if (readLine(input) != "DONE") throw IllegalStateException("Transfer did not finish cleanly")
                    runOnUiThread {
                        status.text = "Received $received file(s) · saved in Downloads/MediaHub."
                        Toast.makeText(this, status.text, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (error: Exception) {
                runOnUiThread { status.text = "Receive failed: ${error.localizedMessage ?: "check that both phones are on the same Wi-Fi"}" }
            }
        }
    }

    private fun saveIncomingStream(input: InputStream, name: String, mime: String, size: Long) {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "${System.currentTimeMillis()}-$name")
                put(MediaStore.MediaColumns.MIME_TYPE, mime.ifBlank { "application/octet-stream" })
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MediaHub")
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create destination file")
            try {
                val output = contentResolver.openOutputStream(uri) ?: throw IllegalStateException("Could not open destination file")
                output.use { copyExactly(input, it, size) }
            } catch (error: Exception) {
                contentResolver.delete(uri, null, null)
                throw error
            }
        } else {
            val folder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
            folder.mkdirs()
            val destination = File(folder, "${System.currentTimeMillis()}-$name")
            destination.outputStream().use { copyExactly(input, it, size) }
        }
    }

    private fun copyExactly(input: InputStream, output: OutputStream, size: Long) {
        var remaining = size
        val buffer = ByteArray(128 * 1024)
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (count < 0) throw IllegalStateException("Connection ended before the full file arrived")
            output.write(buffer, 0, count)
            remaining -= count
        }
    }

    private fun readLine(input: InputStream): String {
        val bytes = java.io.ByteArrayOutputStream()
        while (bytes.size() < 4096) {
            val next = input.read()
            if (next < 0) throw IllegalStateException("Connection closed unexpectedly")
            if (next == 10) break
            if (next != 13) bytes.write(next)
        }
        return bytes.toString(StandardCharsets.UTF_8.name())
    }

    private fun writeLine(output: OutputStream, value: String) {
        output.write((value + "\n").toByteArray(StandardCharsets.UTF_8))
        output.flush()
    }

    private fun localIpv4(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }?.hostAddress
    } catch (_: Exception) { null }

    private fun showInstalledApps() {
        val apps = packageManager.getInstalledApplications(0)
            .filter { it.packageName != packageName && (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .sortedBy { packageManager.getApplicationLabel(it).toString().lowercase() }
        if (apps.isEmpty()) {
            Toast.makeText(this, "No shareable installed apps were found.", Toast.LENGTH_LONG).show()
            return
        }
        val labels = apps.map { packageManager.getApplicationLabel(it).toString() }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Choose an installed app")
            .setItems(labels) { _, which -> shareApk(apps[which], labels[which]) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun shareApk(app: ApplicationInfo, label: String) {
        try {
            val sources = (listOf(app.sourceDir) + app.splitSourceDirs.orEmpty()).map(::File)
            if (sources.any { !it.isFile || !it.canRead() }) throw IllegalStateException("Android denied access to one or more APK files")
            val directory = File(cacheDir, "apk-share").apply { mkdirs() }
            val safe = label.replace(Regex("[^A-Za-z0-9._-]"), "_").take(64)
            val uris = ArrayList<Uri>()
            sources.forEachIndexed { index, source ->
                val destination = File(directory, "$safe-${if (index == 0) "base" else "split-$index"}.apk")
                source.inputStream().use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
                uris += FileProvider.getUriForFile(this, "$packageName.files", destination)
            }
            val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "application/vnd.android.package-archive"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newUri(contentResolver, "$label APK package", uris.first()).also { clip ->
                    uris.drop(1).forEach { clip.addItem(android.content.ClipData.Item(it)) }
                }
            }
            startActivity(Intent.createChooser(send, "Share complete $label APK package"))
            status.text = "Sharing $label as ${uris.size} APK part(s). Split apps need a compatible installer."
        } catch (error: Exception) {
            status.text = "Could not share this app: ${error.localizedMessage ?: "APK access denied"}"
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent); receiveSharedFiles(intent)
    }

    @Suppress("DEPRECATION")
    private fun receiveSharedFiles(incoming: Intent) {
        val uris = when (incoming.action) {
            Intent.ACTION_SEND -> listOfNotNull(incoming.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: incoming.data)
            Intent.ACTION_SEND_MULTIPLE -> incoming.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            else -> emptyList()
        }
        if (uris.isEmpty()) return
        status.text = "Receiving ${uris.size} file(s) from another app…"
        CoroutineScope(Dispatchers.IO).launch {
            var saved = 0
            uris.forEach { uri ->
                try {
                    val file = readShareFile(uri)
                    contentResolver.openInputStream(uri)?.use { input ->
                        saveIncomingStream(input, file.name.replace(Regex("[^A-Za-z0-9._-]"), "_"), file.mime, file.size)
                    }
                    saved++
                } catch (_: Exception) { }
            }
            withContext(Dispatchers.Main) {
                status.text = "Received $saved of ${uris.size} file(s). Saved in Downloads/MediaHub."
                Toast.makeText(this@MediaHubTransferActivity, status.text, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun stopServer() {
        activeToken = null
        try { server?.close() } catch (_: Exception) { }
        server = null
        status.text = "Transfer session stopped."
    }

    override fun onDestroy() {
        stopServer()
        io.shutdownNow()
        super.onDestroy()
    }

    private fun formatBytes(bytes: Long): String = if (bytes >= 1_000_000L) "${"%.1f".format(bytes / 1_000_000.0)} MB" else "${bytes / 1000} KB"
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
