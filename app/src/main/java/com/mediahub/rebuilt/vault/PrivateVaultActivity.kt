package com.mediahub.rebuilt.vault

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.mediahub.rebuilt.BuildConfig
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Offline, app-private encrypted vault. Each item is protected with AES-256-GCM
 * using a non-exportable Android Keystore key. No media is uploaded.
 */
class PrivateVaultActivity : AppCompatActivity() {
    private val vaultDir by lazy { File(filesDir, "private-vault").apply { mkdirs() } }
    private val previewDir by lazy { File(cacheDir, "vault").apply { mkdirs() } }
    private lateinit var list: LinearLayout

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importItem(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(36), dp(20), dp(24))
            setBackgroundColor(0xFF0B0618.toInt())
        }
        root.addView(TextView(this).apply {
            text = "Private Vault"
            textSize = 27f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, dp(8))
        })
        root.addView(TextView(this).apply {
            text = "Files are encrypted on this device with AES-256-GCM. They remain private to MediaHub Pro and are never uploaded."
            textSize = 13f
            setTextColor(0xFFD4C4E8.toInt())
            setPadding(0, 0, 0, dp(16))
        })
        root.addView(Button(this).apply {
            text = "＋  Add files to vault"
            setOnClickListener { picker.launch(arrayOf("*/*")) }
        })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)
        setContentView(root)
        renderItems()
    }

    private fun importItem(uri: Uri) {
        try {
            val resolver = contentResolver
            val name = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: "Private file"
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val target = File(vaultDir, "${UUID.randomUUID()}.mhv")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            }
            FileOutputStream(target).use { file ->
                file.write(byteArrayOf(0x4d, 0x48, 0x56, 0x31))
                file.write(cipher.iv.size)
                file.write(cipher.iv)
                CipherOutputStream(file, cipher).use { encrypted ->
                    encrypted.write((mime + "\n" + name + "\n").toByteArray(Charsets.UTF_8))
                    resolver.openInputStream(uri)?.use { input -> input.copyTo(encrypted) }
                        ?: throw IllegalStateException("Unable to read selected file")
                }
            }
            Toast.makeText(this, "Encrypted and saved to Private Vault.", Toast.LENGTH_LONG).show()
            renderItems()
        } catch (error: Exception) {
            Toast.makeText(this, "Could not encrypt file: ${error.localizedMessage ?: "unknown error"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun renderItems() {
        list.removeAllViews()
        val files = vaultDir.listFiles { file -> file.isFile && file.extension == "mhv" }
            ?.sortedByDescending { it.lastModified() }.orEmpty()
        if (files.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Your vault is empty."
                setTextColor(0xFF9A8BB0.toInt())
                setPadding(0, dp(24), 0, 0)
            })
            return
        }
        files.forEachIndexed { index, file ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                setBackgroundColor(0xFF1A1230.toInt())
                val title = TextView(this@PrivateVaultActivity).apply {
                    text = "Encrypted item ${index + 1}"
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                }
                addView(title)
                addView(Button(this@PrivateVaultActivity).apply {
                    text = "Open"
                    setOnClickListener { openItem(file) }
                })
                addView(Button(this@PrivateVaultActivity).apply {
                    text = "Delete"
                    setOnClickListener {
                        AlertDialog.Builder(this@PrivateVaultActivity)
                            .setTitle("Delete encrypted item?")
                            .setMessage("This permanently removes this vault copy. The original file is not changed.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete") { _, _ -> file.delete(); renderItems() }
                            .show()
                    }
                })
            }
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
    }

    private fun openItem(source: File) {
        try {
            val input = FileInputStream(source)
            val magic = ByteArray(4)
            if (input.read(magic) != 4 || !magic.contentEquals(byteArrayOf(0x4d, 0x48, 0x56, 0x31))) {
                input.close()
                throw IllegalStateException("This vault item has an invalid format")
            }
            val ivLength = input.read()
            if (ivLength !in 12..16) {
                input.close()
                throw IllegalStateException("Invalid encryption header")
            }
            val iv = ByteArray(ivLength)
            var offset = 0
            while (offset < iv.size) {
                val count = input.read(iv, offset, iv.size - offset)
                if (count < 0) throw IllegalStateException("Incomplete encryption header")
                offset += count
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val decrypted = CipherInputStream(input, cipher)
            val mime = readLine(decrypted)
            val originalName = readLine(decrypted).ifBlank { "private-file" }
            val safeName = originalName.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[^A-Za-z0-9._-]"), "_")
            val extension = safeName.substringAfterLast('.', "")
            val preview = File(previewDir, "preview-${System.currentTimeMillis()}" + if (extension.isNotBlank()) ".$extension" else "")
            FileOutputStream(preview).use { output -> decrypted.copyTo(output) }
            decrypted.close()
            val contentUri = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.files", preview)
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mime.ifBlank { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(view, "Open decrypted vault item"))
        } catch (error: Exception) {
            Toast.makeText(this, "Unable to decrypt item: ${error.localizedMessage ?: "wrong key or damaged file"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun readLine(input: java.io.InputStream): String {
        val bytes = ByteArrayOutputStream()
        while (bytes.size() < 4096) {
            val value = input.read()
            if (value < 0 || value == 10) break
            if (value != 13) bytes.write(value)
        }
        return bytes.toByteArray().toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    override fun onDestroy() {
        previewDir.listFiles()?.forEach { it.delete() }
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object { private const val KEY_ALIAS = "mediahub_private_vault_aes_key" }
}
