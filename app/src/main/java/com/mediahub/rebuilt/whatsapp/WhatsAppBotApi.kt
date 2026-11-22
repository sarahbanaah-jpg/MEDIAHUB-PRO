package com.mediahub.rebuilt.whatsapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class BotStatus(
    val status: String,
    val accountName: String?,
    val accountId: String?,
    val botEnabled: Boolean,
    val qrDataUrl: String?,
    val received: Int,
    val sent: Int,
    val conversations: Int,
    val logs: List<String>,
    val botName: String
)

object WhatsAppBotApi {
    private const val PREFS = "mediahub_whatsapp_bot"

    fun getBaseUrl(context: Context): String =
        context.getSharedPreferences(PREFS, 0).getString("base_url", "")?.trim()?.trimEnd('/') ?: ""

    fun getToken(context: Context): String =
        context.getSharedPreferences(PREFS, 0).getString("api_token", "")?.trim() ?: ""

    fun saveConnection(context: Context, baseUrl: String, token: String) {
        context.getSharedPreferences(PREFS, 0).edit()
            .putString("base_url", baseUrl.trim().trimEnd('/'))
            .putString("api_token", token.trim())
            .apply()
    }

    suspend fun status(context: Context): BotStatus = request(context, "GET", "/api/status")

    suspend fun start(context: Context): BotStatus = request(context, "POST", "/api/start")

    suspend fun reconnect(context: Context): BotStatus = request(context, "POST", "/api/reconnect")

    suspend fun logout(context: Context): BotStatus = request(context, "POST", "/api/logout")

    suspend fun setEnabled(context: Context, enabled: Boolean): BotStatus =
        request(context, "POST", "/api/enabled", JSONObject().put("enabled", enabled))

    suspend fun health(context: Context): Boolean = withContext(Dispatchers.IO) {
        val base = getBaseUrl(context)
        if (base.isBlank()) return@withContext false
        try {
            val conn = (URL("$base/health").openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
            }
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        }
    }

    fun qrToBitmap(dataUrl: String?): Bitmap? {
        if (dataUrl.isNullOrBlank()) return null
        return try {
            val b64 = dataUrl.substringAfter("base64,", dataUrl)
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun request(
        context: Context,
        method: String,
        path: String,
        body: JSONObject? = null
    ): BotStatus = withContext(Dispatchers.IO) {
        val base = getBaseUrl(context)
        val token = getToken(context)
        if (base.isBlank() || token.isBlank()) {
            throw IllegalStateException("Set backend URL and API token first")
        }
        val conn = (URL("$base$path").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        if (body != null) {
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        if (code !in 200..299) {
            throw IllegalStateException(JSONObject(text).optString("error", "HTTP $code"))
        }
        parseStatus(JSONObject(text))
    }

    private fun parseStatus(json: JSONObject): BotStatus {
        val account = json.optJSONObject("account")
        val stats = json.optJSONObject("stats")
        val config = json.optJSONObject("config")
        val logsArr = json.optJSONArray("logs")
        val logs = mutableListOf<String>()
        if (logsArr != null) {
            for (i in 0 until logsArr.length()) {
                val o = logsArr.optJSONObject(i)
                logs += o?.optString("line") ?: logsArr.optString(i)
            }
        }
        return BotStatus(
            status = json.optString("status", "disconnected"),
            accountName = account?.optString("name"),
            accountId = account?.optString("id"),
            botEnabled = json.optBoolean("botEnabled", false),
            qrDataUrl = json.optString("qr", null),
            received = stats?.optInt("received") ?: 0,
            sent = stats?.optInt("sent") ?: 0,
            conversations = stats?.optInt("activeConversations") ?: 0,
            logs = logs,
            botName = config?.optString("botName") ?: "MediaHub Bot"
        )
    }
}
