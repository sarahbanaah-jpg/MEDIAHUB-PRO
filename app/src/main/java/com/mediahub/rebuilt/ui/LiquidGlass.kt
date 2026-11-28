package com.mediahub.rebuilt.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi

/**
 * Smooth liquid glass — AGSL multi-octave refraction on API 33+,
 * chained blur on API 31–32.
 * Displacement is low-frequency and heavily smoothed for 100% fluid bend.
 */
object LiquidGlass {

    private const val AGSL = """
        uniform shader content;
        uniform float2 size;
        uniform float time;
        uniform float strength;

        float hash(float2 p) {
            return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453123);
        }

        // Quintic smoothstep for silk-smooth interpolation
        float2 fade(float2 t) {
            return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
        }

        float noise(float2 p) {
            float2 i = floor(p);
            float2 f = fract(p);
            float a = hash(i);
            float b = hash(i + float2(1.0, 0.0));
            float c = hash(i + float2(0.0, 1.0));
            float d = hash(i + float2(1.0, 1.0));
            float2 u = fade(f);
            return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
        }

        // Multi-octave fbm for continuous liquid motion
        float fbm(float2 p) {
            float v = 0.0;
            float a = 0.5;
            for (int i = 0; i < 4; i++) {
                v += a * noise(p);
                p *= 2.02;
                a *= 0.5;
            }
            return v;
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / size;

            // Very low frequency drift — smooth bend, not jitter
            float2 flow = float2(time * 0.045, time * 0.028);
            float n1 = fbm(uv * 1.8 + flow);
            float n2 = fbm(uv * 2.4 - flow * 1.3 + float2(3.7, 1.1));

            // Soft gradient of the field (stable refraction direction)
            float e = 0.012;
            float dx = fbm(uv * 1.8 + flow + float2(e, 0.0)) - fbm(uv * 1.8 + flow - float2(e, 0.0));
            float dy = fbm(uv * 1.8 + flow + float2(0.0, e)) - fbm(uv * 1.8 + flow - float2(0.0, e));

            float amp = strength * 14.0;
            float2 offset = float2(dx, dy) * amp + float2(n1 - 0.5, n2 - 0.5) * amp * 0.35;

            float2 sampleAt = clamp(fragCoord + offset, float2(1.0), size - float2(1.0));
            half4 color = content.eval(sampleAt);

            // Soft specular caustic (smooth, not sparkly)
            float caustic = smoothstep(0.35, 0.75, n1) * smoothstep(0.3, 0.7, n2);
            color.rgb += half3(caustic * 0.12, caustic * 0.13, caustic * 0.15);

            // Gentle edge light
            float edge = smoothstep(0.0, 0.08, uv.x) * smoothstep(0.0, 0.08, uv.y)
                * smoothstep(0.0, 0.08, 1.0 - uv.x) * smoothstep(0.0, 0.08, 1.0 - uv.y);
            color.rgb += half3(0.04, 0.045, 0.055) * (1.0 - edge);

            color.a = max(color.a, half(0.78));
            return color;
        }
    """

    fun apply(view: View, strength: Float = 1.0f) {
        if (Build.VERSION.SDK_INT >= 33) {
            applyAgsl(view, strength)
        } else if (Build.VERSION.SDK_INT >= 31) {
            applyBlur(view)
        }
    }

    @RequiresApi(33)
    private fun applyAgsl(view: View, strength: Float) {
        try {
            val shader = RuntimeShader(AGSL)
            val updateSize = {
                shader.setFloatUniform(
                    "size",
                    view.width.toFloat().coerceAtLeast(1f),
                    view.height.toFloat().coerceAtLeast(1f)
                )
            }
            view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateSize() }
            updateSize()
            shader.setFloatUniform("time", 0f)
            shader.setFloatUniform("strength", strength.coerceIn(0.3f, 2.0f))

            // Chain soft blur under refraction for silk smoothness
            val glass = RenderEffect.createRuntimeShaderEffect(shader, "content")
            val smooth = RenderEffect.createChainEffect(
                glass,
                RenderEffect.createBlurEffect(8f, 8f, Shader.TileMode.CLAMP)
            )
            view.setRenderEffect(smooth)

            // ~30fps time tick — smooth motion, low battery cost
            view.postDelayed(object : Runnable {
                override fun run() {
                    if (!view.isAttachedToWindow || Build.VERSION.SDK_INT < 33) return
                    try {
                        shader.setFloatUniform(
                            "time",
                            (System.nanoTime() % 100_000_000_000L) / 1_000_000_000f
                        )
                        updateSize()
                        val g = RenderEffect.createRuntimeShaderEffect(shader, "content")
                        val s = RenderEffect.createChainEffect(
                            g,
                            RenderEffect.createBlurEffect(8f, 8f, Shader.TileMode.CLAMP)
                        )
                        view.setRenderEffect(s)
                        view.postDelayed(this, 33)
                    } catch (_: Exception) {
                    }
                }
            }, 33)
        } catch (_: Exception) {
            if (Build.VERSION.SDK_INT >= 31) applyBlur(view)
        }
    }

    @RequiresApi(31)
    private fun applyBlur(view: View) {
        try {
            view.setRenderEffect(
                RenderEffect.createBlurEffect(28f, 28f, Shader.TileMode.CLAMP)
            )
        } catch (_: Exception) {
        }
    }

    fun clear(view: View) {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                view.setRenderEffect(null)
            } catch (_: Exception) {
            }
        }
    }
}
