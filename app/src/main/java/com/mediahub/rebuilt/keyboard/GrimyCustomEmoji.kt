package com.mediahub.rebuilt.keyboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/**
 * Original Grimy emoji — drawn at runtime (not Unicode stock).
 * Each has a unique look + a shortcode that inserts into any text field.
 */
data class GrimyEmoji(
    val id: String,
    val shortcode: String,
    val label: String,
    val draw: (Canvas, Float) -> Unit
)

object GrimyCustomEmoji {

    private val cache = mutableMapOf<String, Bitmap>()

    fun bitmap(emoji: GrimyEmoji, sizePx: Int): Bitmap {
        val key = "${emoji.id}_$sizePx"
        cache[key]?.let { return it }
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        emoji.draw(c, sizePx.toFloat())
        cache[key] = bmp
        return bmp
    }

    private fun bgCircle(c: Canvas, s: Float, colors: IntArray) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(
            s * 0.35f, s * 0.3f, s * 0.7f,
            colors, floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(s / 2f, s / 2f, s * 0.46f, p)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = s * 0.03f
            color = 0x55FFFFFF
        }
        c.drawCircle(s / 2f, s / 2f, s * 0.46f, ring)
    }

    private fun letter(c: Canvas, s: Float, ch: String, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = s * 0.42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        c.drawText(ch, s / 2f, s * 0.62f, p)
    }

    val pack: List<GrimyEmoji> = listOf(
        // Brand core
        GrimyEmoji("g_logo", ":grimy:", "Grimy") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFFF4DA6.toInt(), 0xFF5B1A8C.toInt()))
            letter(c, s, "G", Color.WHITE)
        },
        GrimyEmoji("mh", ":mediahub:", "MediaHub") { c, s ->
            bgCircle(c, s, intArrayOf(0xFF5CE1FF.toInt(), 0xFF3D1F7A.toInt()))
            letter(c, s, "M", Color.WHITE)
        },
        GrimyEmoji("g_heart", ":gheart:", "G Heart") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFFF6B9D.toInt(), 0xFF8B1E4A.toInt()))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            val path = Path()
            val cx = s / 2f; val cy = s * 0.42f
            path.moveTo(cx, cy + s * 0.22f)
            path.cubicTo(cx + s * 0.28f, cy - s * 0.05f, cx + s * 0.18f, cy - s * 0.22f, cx, cy - s * 0.02f)
            path.cubicTo(cx - s * 0.18f, cy - s * 0.22f, cx - s * 0.28f, cy - s * 0.05f, cx, cy + s * 0.22f)
            c.drawPath(path, p)
        },
        GrimyEmoji("fire", ":gfire:", "Fire") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFFF8A3D.toInt(), 0xFFCC2200.toInt()))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF3A0.toInt() }
            val path = Path()
            path.moveTo(s * 0.5f, s * 0.22f)
            path.cubicTo(s * 0.72f, s * 0.4f, s * 0.7f, s * 0.7f, s * 0.5f, s * 0.78f)
            path.cubicTo(s * 0.3f, s * 0.7f, s * 0.28f, s * 0.4f, s * 0.5f, s * 0.22f)
            c.drawPath(path, p)
            p.color = 0xFFFF6B2C.toInt()
            c.drawCircle(s * 0.5f, s * 0.58f, s * 0.1f, p)
        },
        GrimyEmoji("bolt", ":gbolt:", "Bolt") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFFFF176.toInt(), 0xFFFF6F00.toInt()))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            val path = Path()
            path.moveTo(s * 0.58f, s * 0.2f)
            path.lineTo(s * 0.35f, s * 0.52f)
            path.lineTo(s * 0.5f, s * 0.52f)
            path.lineTo(s * 0.4f, s * 0.8f)
            path.lineTo(s * 0.68f, s * 0.45f)
            path.lineTo(s * 0.52f, s * 0.45f)
            path.close()
            c.drawPath(path, p)
        },
        GrimyEmoji("star", ":gstar:", "Star") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFFFE082.toInt(), 0xFFFF8F00.toInt()))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            val path = starPath(s / 2f, s / 2f, s * 0.32f, s * 0.14f)
            c.drawPath(path, p)
        },
        GrimyEmoji("music", ":gmusic:", "Music") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFB388FF.toInt(), 0xFF4A148C.toInt()))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = s * 0.06f
                strokeCap = Paint.Cap.ROUND
            }
            c.drawLine(s * 0.42f, s * 0.28f, s * 0.42f, s * 0.62f, p)
            c.drawLine(s * 0.62f, s * 0.24f, s * 0.62f, s * 0.58f, p)
            p.style = Paint.Style.FILL
            c.drawOval(RectF(s * 0.28f, s * 0.55f, s * 0.48f, s * 0.72f), p)
            c.drawOval(RectF(s * 0.48f, s * 0.51f, s * 0.68f, s * 0.68f), p)
        },
        GrimyEmoji("headphone", ":gphones:", "Phones") { c, s ->
            bgCircle(c, s, intArrayOf(0xFF80D8FF.toInt(), 0xFF1565C0.toInt()))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = s * 0.07f
                strokeCap = Paint.Cap.ROUND
            }
            c.drawArc(RectF(s * 0.28f, s * 0.28f, s * 0.72f, s * 0.72f), 200f, 140f, false, p)
            p.style = Paint.Style.FILL
            c.drawRoundRect(RectF(s * 0.22f, s * 0.42f, s * 0.34f, s * 0.68f), s * 0.04f, s * 0.04f, p)
            c.drawRoundRect(RectF(s * 0.66f, s * 0.42f, s * 0.78f, s * 0.68f), s * 0.04f, s * 0.04f, p)
        },
        GrimyEmoji("crown", ":gcrown:", "Crown") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFFFD54F.toInt(), 0xFFF57F17.toInt()))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            val path = Path()
            path.moveTo(s * 0.22f, s * 0.62f)
            path.lineTo(s * 0.22f, s * 0.4f)
            path.lineTo(s * 0.38f, s * 0.52f)
            path.lineTo(s * 0.5f, s * 0.28f)
            path.lineTo(s * 0.62f, s * 0.52f)
            path.lineTo(s * 0.78f, s * 0.4f)
            path.lineTo(s * 0.78f, s * 0.62f)
            path.close()
            c.drawPath(path, p)
        },
        GrimyEmoji("diamond", ":gdiamond:", "Diamond") { c, s ->
            bgCircle(c, s, intArrayOf(0xFF82B1FF.toInt(), 0xFF304FFE.toInt()))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            val path = Path()
            path.moveTo(s * 0.5f, s * 0.22f)
            path.lineTo(s * 0.72f, s * 0.42f)
            path.lineTo(s * 0.5f, s * 0.78f)
            path.lineTo(s * 0.28f, s * 0.42f)
            path.close()
            c.drawPath(path, p)
        },
        GrimyEmoji("moon", ":gmoon:", "Moon") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFB39DDB.toInt(), 0xFF311B92.toInt()))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF9C4.toInt() }
            c.drawCircle(s * 0.52f, s * 0.5f, s * 0.22f, p)
            p.color = 0xFF5E35B1.toInt()
            c.drawCircle(s * 0.62f, s * 0.42f, s * 0.18f, p)
        },
        GrimyEmoji("sun", ":gsun:", "Sun") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFFFF176.toInt(), 0xFFFF6F00.toInt()))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            c.drawCircle(s * 0.5f, s * 0.5f, s * 0.16f, p)
            p.strokeWidth = s * 0.04f
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                c.drawLine(
                    (s * 0.5f + Math.cos(a) * s * 0.22f).toFloat(),
                    (s * 0.5f + Math.sin(a) * s * 0.22f).toFloat(),
                    (s * 0.5f + Math.cos(a) * s * 0.34f).toFloat(),
                    (s * 0.5f + Math.sin(a) * s * 0.34f).toFloat(),
                    p
                )
            }
        },
        GrimyEmoji("rocket", ":grocket:", "Rocket") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFFF8A80.toInt(), 0xFFC62828.toInt()))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            val path = Path()
            path.moveTo(s * 0.5f, s * 0.2f)
            path.lineTo(s * 0.64f, s * 0.55f)
            path.lineTo(s * 0.5f, s * 0.5f)
            path.lineTo(s * 0.36f, s * 0.55f)
            path.close()
            c.drawPath(path, p)
            p.color = 0xFFFFE082.toInt()
            c.drawCircle(s * 0.5f, s * 0.68f, s * 0.08f, p)
        },
        GrimyEmoji("wolf", ":gwolf:", "Wolf") { c, s ->
            bgCircle(c, s, intArrayOf(0xFF90A4AE.toInt(), 0xFF263238.toInt()))
            letter(c, s, "W", Color.WHITE)
        },
        GrimyEmoji("lion", ":glion:", "Lion") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFFFB74D.toInt(), 0xFFE65100.toInt()))
            letter(c, s, "L", Color.WHITE)
        },
        GrimyEmoji("dragon", ":gdragon:", "Dragon") { c, s ->
            bgCircle(c, s, intArrayOf(0xFF69F0AE.toInt(), 0xFF1B5E20.toInt()))
            letter(c, s, "D", Color.WHITE)
        },
        GrimyEmoji("ghost", ":gghost:", "Ghost") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFE1BEE7.toInt(), 0xFF6A1B9A.toInt()))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            c.drawRoundRect(RectF(s * 0.32f, s * 0.28f, s * 0.68f, s * 0.7f), s * 0.16f, s * 0.16f, p)
            p.color = 0xFF4A148C.toInt()
            c.drawCircle(s * 0.42f, s * 0.45f, s * 0.04f, p)
            c.drawCircle(s * 0.58f, s * 0.45f, s * 0.04f, p)
        },
        GrimyEmoji("skull", ":gskull:", "Skull") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFCFD8DC.toInt(), 0xFF37474F.toInt()))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            c.drawOval(RectF(s * 0.3f, s * 0.28f, s * 0.7f, s * 0.68f), p)
            p.color = 0xFF263238.toInt()
            c.drawCircle(s * 0.42f, s * 0.45f, s * 0.05f, p)
            c.drawCircle(s * 0.58f, s * 0.45f, s * 0.05f, p)
            c.drawRect(s * 0.44f, s * 0.58f, s * 0.56f, s * 0.62f, p)
        },
        GrimyEmoji("wave", ":gwave:", "Wave") { c, s ->
            bgCircle(c, s, intArrayOf(0xFF80D8FF.toInt(), 0xFF0277BD.toInt()))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = s * 0.06f
                strokeCap = Paint.Cap.ROUND
            }
            val path = Path()
            path.moveTo(s * 0.22f, s * 0.55f)
            path.cubicTo(s * 0.35f, s * 0.35f, s * 0.45f, s * 0.7f, s * 0.58f, s * 0.5f)
            path.cubicTo(s * 0.68f, s * 0.35f, s * 0.72f, s * 0.6f, s * 0.8f, s * 0.48f)
            c.drawPath(path, p)
        },
        GrimyEmoji("pulse", ":gpulse:", "Pulse") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFFF8A80.toInt(), 0xFFB71C1C.toInt()))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = s * 0.055f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val path = Path()
            path.moveTo(s * 0.2f, s * 0.5f)
            path.lineTo(s * 0.35f, s * 0.5f)
            path.lineTo(s * 0.42f, s * 0.32f)
            path.lineTo(s * 0.52f, s * 0.68f)
            path.lineTo(s * 0.6f, s * 0.5f)
            path.lineTo(s * 0.8f, s * 0.5f)
            c.drawPath(path, p)
        },
        GrimyEmoji("shield", ":gshield:", "Shield") { c, s ->
            bgCircle(c, s, intArrayOf(0xFF81C784.toInt(), 0xFF1B5E20.toInt()))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            val path = Path()
            path.moveTo(s * 0.5f, s * 0.22f)
            path.lineTo(s * 0.72f, s * 0.32f)
            path.lineTo(s * 0.72f, s * 0.52f)
            path.cubicTo(s * 0.72f, s * 0.7f, s * 0.5f, s * 0.8f, s * 0.5f, s * 0.8f)
            path.cubicTo(s * 0.5f, s * 0.8f, s * 0.28f, s * 0.7f, s * 0.28f, s * 0.52f)
            path.lineTo(s * 0.28f, s * 0.32f)
            path.close()
            c.drawPath(path, p)
        },
        GrimyEmoji("lock", ":glock:", "Lock") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFFFD54F.toInt(), 0xFFF57F17.toInt()))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            c.drawRoundRect(RectF(s * 0.34f, s * 0.48f, s * 0.66f, s * 0.72f), s * 0.04f, s * 0.04f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = s * 0.05f
            c.drawArc(RectF(s * 0.38f, s * 0.3f, s * 0.62f, s * 0.55f), 180f, 180f, false, p)
        },
        GrimyEmoji("vault", ":gvault:", "Vault") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFCE93D8.toInt(), 0xFF6A1B9A.toInt()))
            letter(c, s, "V", Color.WHITE)
        },
        GrimyEmoji("studio", ":gstudio:", "Studio") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFFFAB91.toInt(), 0xFFBF360C.toInt()))
            letter(c, s, "S", Color.WHITE)
        },
        GrimyEmoji("play", ":gplay:", "Play") { c, s ->
            bgCircle(c, s, intArrayOf(0xFF69F0AE.toInt(), 0xFF00C853.toInt()))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            val path = Path()
            path.moveTo(s * 0.38f, s * 0.3f)
            path.lineTo(s * 0.72f, s * 0.5f)
            path.lineTo(s * 0.38f, s * 0.7f)
            path.close()
            c.drawPath(path, p)
        },
        GrimyEmoji("pause", ":gpause:", "Pause") { c, s ->
            bgCircle(c, s, intArrayOf(0xFF80CBC4.toInt(), 0xFF00695C.toInt()))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            c.drawRoundRect(RectF(s * 0.34f, s * 0.3f, s * 0.44f, s * 0.7f), s * 0.03f, s * 0.03f, p)
            c.drawRoundRect(RectF(s * 0.56f, s * 0.3f, s * 0.66f, s * 0.7f), s * 0.03f, s * 0.03f, p)
        },
        GrimyEmoji("ok", ":gok:", "OK") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFA5D6A7.toInt(), 0xFF2E7D32.toInt()))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = s * 0.07f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val path = Path()
            path.moveTo(s * 0.3f, s * 0.5f)
            path.lineTo(s * 0.45f, s * 0.65f)
            path.lineTo(s * 0.72f, s * 0.35f)
            c.drawPath(path, p)
        },
        GrimyEmoji("no", ":gno:", "No") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFEF9A9A.toInt(), 0xFFC62828.toInt()))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = s * 0.07f
                strokeCap = Paint.Cap.ROUND
            }
            c.drawLine(s * 0.32f, s * 0.32f, s * 0.68f, s * 0.68f, p)
            c.drawLine(s * 0.68f, s * 0.32f, s * 0.32f, s * 0.68f, p)
        },
        GrimyEmoji("love", ":glove:", "Love") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFF48FB1.toInt(), 0xFFAD1457.toInt()))
            letter(c, s, "♡", Color.WHITE)
        },
        GrimyEmoji("pro", ":gpro:", "Pro") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFFFE082.toInt(), 0xFFFF6F00.toInt()))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = s * 0.28f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            c.drawText("PRO", s / 2f, s * 0.58f, p)
        },
        GrimyEmoji("vip", ":gvip:", "VIP") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFE1BEE7.toInt(), 0xFF6A1B9A.toInt()))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = s * 0.28f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            c.drawText("VIP", s / 2f, s * 0.58f, p)
        },
        GrimyEmoji("night", ":gnight:", "Night") { c, s ->
            bgCircle(c, s, intArrayOf(0xFF9575CD.toInt(), 0xFF311B92.toInt()))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF59D.toInt() }
            c.drawCircle(s * 0.4f, s * 0.4f, s * 0.05f, p)
            c.drawCircle(s * 0.6f, s * 0.35f, s * 0.03f, p)
            c.drawCircle(s * 0.55f, s * 0.55f, s * 0.04f, p)
            p.color = 0xFFFFE082.toInt()
            c.drawCircle(s * 0.62f, s * 0.58f, s * 0.12f, p)
        },
        GrimyEmoji("aurora", ":gaurora:", "Aurora") { c, s ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(
                0f, 0f, s, s,
                intArrayOf(0xFF5CE1FF.toInt(), 0xFFB59BFF.toInt(), 0xFFFF6BB5.toInt()),
                null, Shader.TileMode.CLAMP
            )
            c.drawCircle(s / 2f, s / 2f, s * 0.46f, p)
            letter(c, s, "A", Color.WHITE)
        },
        GrimyEmoji("neon", ":gneon:", "Neon") { c, s ->
            bgCircle(c, s, intArrayOf(0xFF00E676.toInt(), 0xFF00BFA5.toInt()))
            letter(c, s, "N", Color.WHITE)
        },
        GrimyEmoji("glass", ":gglass:", "Glass") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFB388FF.toInt(), 0xFF651FFF.toInt()))
            letter(c, s, "◈", Color.WHITE)
        },
        GrimyEmoji("king", ":gking:", "King") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFFFD740.toInt(), 0xFFFF6D00.toInt()))
            letter(c, s, "K", Color.WHITE)
        },
        GrimyEmoji("queen", ":gqueen:", "Queen") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFFF80AB.toInt(), 0xFFC51162.toInt()))
            letter(c, s, "Q", Color.WHITE)
        },
        GrimyEmoji("boss", ":gboss:", "Boss") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFEF9A9A.toInt(), 0xFFB71C1C.toInt()))
            letter(c, s, "B", Color.WHITE)
        },
        GrimyEmoji("win", ":gwin:", "Win") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFFFF176.toInt(), 0xFFF9A825.toInt()))
            letter(c, s, "★", 0xFF5D4037.toInt())
        },
        GrimyEmoji("chip", ":gchip:", "Chip") { c, s ->
            bgCircle(c, s, intArrayOf(0xFF80CBC4.toInt(), 0xFF00695C.toInt()))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = s * 0.04f
            }
            c.drawRoundRect(RectF(s * 0.32f, s * 0.32f, s * 0.68f, s * 0.68f), s * 0.06f, s * 0.06f, p)
            c.drawLine(s * 0.32f, s * 0.45f, s * 0.22f, s * 0.45f, p)
            c.drawLine(s * 0.68f, s * 0.45f, s * 0.78f, s * 0.45f, p)
            c.drawLine(s * 0.32f, s * 0.55f, s * 0.22f, s * 0.55f, p)
            c.drawLine(s * 0.68f, s * 0.55f, s * 0.78f, s * 0.55f, p)
        },
        GrimyEmoji("signal", ":gsignal:", "Signal") { c, s ->
            bgCircle(c, s, intArrayOf(0xFF81D4FA.toInt(), 0xFF0277BD.toInt()))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = s * 0.05f
                strokeCap = Paint.Cap.ROUND
            }
            c.drawArc(RectF(s * 0.35f, s * 0.35f, s * 0.65f, s * 0.65f), -60f, 120f, false, p)
            c.drawArc(RectF(s * 0.28f, s * 0.28f, s * 0.72f, s * 0.72f), -60f, 120f, false, p)
            p.style = Paint.Style.FILL
            c.drawCircle(s * 0.5f, s * 0.55f, s * 0.05f, p)
        },
        GrimyEmoji("cam", ":gcam:", "Cam") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFB0BEC5.toInt(), 0xFF37474F.toInt()))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            c.drawRoundRect(RectF(s * 0.28f, s * 0.38f, s * 0.72f, s * 0.68f), s * 0.06f, s * 0.06f, p)
            p.color = 0xFF263238.toInt()
            c.drawCircle(s * 0.5f, s * 0.53f, s * 0.1f, p)
            p.color = Color.WHITE
            c.drawCircle(s * 0.5f, s * 0.53f, s * 0.045f, p)
        },
        GrimyEmoji("mic", ":gmic:", "Mic") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFFF8A80.toInt(), 0xFFD50000.toInt()))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            c.drawRoundRect(RectF(s * 0.42f, s * 0.25f, s * 0.58f, s * 0.55f), s * 0.08f, s * 0.08f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = s * 0.05f
            c.drawArc(RectF(s * 0.35f, s * 0.4f, s * 0.65f, s * 0.7f), 0f, 180f, false, p)
            c.drawLine(s * 0.5f, s * 0.7f, s * 0.5f, s * 0.78f, p)
        },
        GrimyEmoji("chat", ":gchat:", "Chat") { c, s ->
            bgCircle(c, s, intArrayOf(0xFF80CBC4.toInt(), 0xFF00695C.toInt()))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            c.drawRoundRect(RectF(s * 0.28f, s * 0.3f, s * 0.72f, s * 0.6f), s * 0.1f, s * 0.1f, p)
            val path = Path()
            path.moveTo(s * 0.4f, s * 0.58f)
            path.lineTo(s * 0.35f, s * 0.72f)
            path.lineTo(s * 0.52f, s * 0.6f)
            path.close()
            c.drawPath(path, p)
        },
        GrimyEmoji("bot", ":gbot:", "Bot") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFB0BEC5.toInt(), 0xFF455A64.toInt()))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            c.drawRoundRect(RectF(s * 0.3f, s * 0.32f, s * 0.7f, s * 0.7f), s * 0.08f, s * 0.08f, p)
            p.color = 0xFF263238.toInt()
            c.drawCircle(s * 0.42f, s * 0.48f, s * 0.05f, p)
            c.drawCircle(s * 0.58f, s * 0.48f, s * 0.05f, p)
            c.drawRect(s * 0.4f, s * 0.58f, s * 0.6f, s * 0.62f, p)
        },
        GrimyEmoji("zap", ":gzap:", "Zap") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFFFFF00.toInt(), 0xFFFF6F00.toInt()))
            letter(c, s, "⚡", Color.WHITE)
        },
        GrimyEmoji("glow", ":gglow:", "Glow") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFFF80AB.toInt(), 0xFFC51162.toInt()))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF }
            c.drawCircle(s * 0.5f, s * 0.5f, s * 0.28f, p)
            p.color = Color.WHITE
            c.drawCircle(s * 0.5f, s * 0.5f, s * 0.12f, p)
        },
        GrimyEmoji("x", ":gx:", "X") { c, s ->
            bgCircle(c, s, intArrayOf(0xFF90A4AE.toInt(), 0xFF263238.toInt()))
            letter(c, s, "X", Color.WHITE)
        },
        GrimyEmoji("plus", ":gplus:", "Plus") { c, s ->
            bgCircle(c, s, intArrayOf(0xFFA5D6A7.toInt(), 0xFF2E7D32.toInt()))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                strokeWidth = s * 0.08f
                strokeCap = Paint.Cap.ROUND
            }
            c.drawLine(s * 0.5f, s * 0.3f, s * 0.5f, s * 0.7f, p)
            c.drawLine(s * 0.3f, s * 0.5f, s * 0.7f, s * 0.5f, p)
        }
    )

    private fun starPath(cx: Float, cy: Float, rOuter: Float, rInner: Float): Path {
        val path = Path()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) rOuter else rInner
            val a = Math.toRadians(-90.0 + i * 36.0)
            val x = cx + (Math.cos(a) * r).toFloat()
            val y = cy + (Math.sin(a) * r).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }
}
