package com.mediahub.rebuilt.ui.music

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

/** Lightweight animated waveform for Now Playing (no heavy DSP). */
class AuroraWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }
    private var phase = 0f
    private var active = true

    private val ticker = object : Runnable {
        override fun run() {
            if (!active) return
            phase += 0.18f
            invalidate()
            postDelayed(this, 32)
        }
    }

    fun setPlaying(playing: Boolean) {
        active = playing
        removeCallbacks(ticker)
        if (playing) post(ticker) else invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(ticker)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        paint.shader = LinearGradient(
            0f, 0f, w, 0f,
            intArrayOf(0x005CE1FF, 0xFF5CE1FF.toInt(), 0xFFA78BFF.toInt(), 0x005CE1FF),
            floatArrayOf(0f, 0.35f, 0.65f, 1f),
            Shader.TileMode.CLAMP
        )
        val mid = h / 2f
        val amp = if (active) h * 0.38f else h * 0.12f
        val path = android.graphics.Path()
        val steps = 48
        for (i in 0..steps) {
            val x = w * i / steps
            val y = mid + (sin(phase + i * 0.35) * amp * (0.55 + 0.45 * sin(i * 0.2 + phase * 0.5))).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)
    }
}
