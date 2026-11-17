package com.mediahub.rebuilt.media

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64

/**
 * Default cover: real album art when available, otherwise the mountain sunrise scene
 * (golden peak against deep purple sky) matching the user's provided image.
 */
object DefaultAlbumArt {

    @Volatile private var cachedMountain: Bitmap? = null

    fun forTrack(context: Context, item: MediaItem, size: Int = 800): Bitmap {
        // 1) Embedded / MediaStore thumbnail
        MediaThumbnails.load(context, item.uri, size, size)?.let { return it }

        // 2) Album art by album id
        if (item.albumId > 0L) {
            try {
                val artUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    item.albumId
                )
                context.contentResolver.openInputStream(artUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.let { return it }
                }
            } catch (_: Exception) {
            }
        }

        // 3) Asset JPEG if present (mountain photo)
        loadFromAsset(context, size)?.let { return it }

        // 4) Procedural mountain (same colour story as the photo)
        return mountainBitmap(size)
    }

    private fun loadFromAsset(context: Context, size: Int): Bitmap? {
        return try {
            val b64 = context.assets.open("default_album_art.b64").bufferedReader().readText().trim()
            if (b64.length < 100 || b64 == "PLACEHOLDER") return null
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            val full = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            Bitmap.createScaledBitmap(full, size, size, true)
        } catch (_: Exception) {
            null
        }
    }

    fun mountainBitmap(size: Int = 800): Bitmap {
        cachedMountain?.let {
            if (it.width == size) return it
        }
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        // Deep purple night sky
        val sky = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, size * 0.55f,
                intArrayOf(0xFF0B0A2E.toInt(), 0xFF2A1B5C.toInt(), 0xFF6B3A8C.toInt()),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), sky)

        // Warm glow behind peak
        val glow = Paint().apply {
            shader = RadialGradient(
                size * 0.5f, size * 0.38f, size * 0.35f,
                intArrayOf(0xAAFFB15A.toInt(), 0x44FF6B3A.toInt(), 0x00000000),
                floatArrayOf(0f, 0.4f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawCircle(size * 0.5f, size * 0.38f, size * 0.35f, glow)

        // Mountain body
        val mountain = Path().apply {
            moveTo(0f, size * 0.95f)
            lineTo(size * 0.18f, size * 0.62f)
            lineTo(size * 0.32f, size * 0.72f)
            lineTo(size * 0.48f, size * 0.28f) // peak
            lineTo(size * 0.55f, size * 0.34f)
            lineTo(size * 0.62f, size * 0.30f)
            lineTo(size * 0.78f, size * 0.58f)
            lineTo(size * 0.90f, size * 0.50f)
            lineTo(size.toFloat(), size * 0.70f)
            lineTo(size.toFloat(), size.toFloat())
            lineTo(0f, size.toFloat())
            close()
        }
        val rock = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                size * 0.3f, size * 0.25f, size * 0.7f, size * 0.9f,
                intArrayOf(0xFFFFC47A.toInt(), 0xFFE08A4A.toInt(), 0xFF5A3A4A.toInt(), 0xFF2A1A2E.toInt()),
                floatArrayOf(0f, 0.25f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawPath(mountain, rock)

        // Snow highlight on peak
        val snow = Path().apply {
            moveTo(size * 0.44f, size * 0.36f)
            lineTo(size * 0.48f, size * 0.28f)
            lineTo(size * 0.56f, size * 0.34f)
            lineTo(size * 0.52f, size * 0.40f)
            close()
        }
        c.drawPath(snow, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xEEFFF5E8.toInt() })

        // Soft mist
        val mist = Paint().apply {
            shader = LinearGradient(
                0f, size * 0.55f, 0f, size * 0.85f,
                intArrayOf(0x00FFFFFF, 0x55E8C4FF.toInt(), 0x00FFFFFF),
                null,
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, size * 0.55f, size.toFloat(), size * 0.85f, mist)

        cachedMountain = bmp
        return bmp
    }
}
