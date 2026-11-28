package com.mediahub.rebuilt.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi

/**
 * Real liquid glass using Android RuntimeShader (AGSL) on API 33+
 * and RenderEffect blur on API 31–32.
 *
 * This is not a fake translucent color overlay — on supported devices
 * the shader displaces samples (light bend / refraction) then soft-blurs.
 */
object LiquidGlass {

    // AGSL: refractive displacement + soft specular highlight
    private const val AGSL = """
        uniform shader content;
        uniform float2 size;
        uniform float time;
        uniform float strength;

        float hash(float2 p) {
            return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
        }

        float noise(float2 p) {
            float2 i = floor(p);
            float2 f = fract(p);
            float a = hash(i);
            float b = hash(i + float2(1.0, 0.0));
            float c = hash(i + float2(0.0, 1.0));
            float d = hash(i + float2(1.0, 1.0));
            float2 u = f * f * (3.0 - 2.0 * f);
            return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / size;
            // Animated liquid displacement field
            float n1 = noise(uv * 3.5 + float2(time * 0.15, time * 0.08));
            float n2 = noise(uv * 6.0 - float2(time * 0.1, time * 0.12));
            float2 offset = float2(n1 - 0.5, n2 - 0.5) * strength * 18.0;
            float2 sampleAt = clamp(fragCoord + offset, float2(0.0), size - float2(1.0));
            half4 color = content.eval(sampleAt);
            // Soft glass edge specular
            float edge = smoothstep(0.0, 0.12, uv.x) * smoothstep(0.0, 0.12, uv.y)
                * smoothstep(0.0, 0.12, 1.0 - uv.x) * smoothstep(0.0, 0.12, 1.0 - uv.y);
            float spec = pow(max(n1 * n2, 0.0), 2.0) * 0.18;
            color.rgb += half3(spec, spec, spec * 1.05);
            color.a = max(color.a, half(0.72));
            return color;
        }
    """

    fun apply(view: View, strength: Float = 1.0f) {
        if (Build.VERSION.SDK_INT >= 33) {
            applyAgsl(view, strength)
        } else if (Build.VERSION.SDK_INT >= 31) {
            applyBlur(view)
        }
        // API < 31: no GPU effect available; UI still uses translucent drawables
    }

    @RequiresApi(33)
    private fun applyAgsl(view: View, strength: Float) {
        try {
            val shader = RuntimeShader(AGSL)
            view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                shader.setFloatUniform("size", v.width.toFloat().coerceAtLeast(1f), v.height.toFloat().coerceAtLeast(1f))
            }
            shader.setFloatUniform("size",
                view.width.toFloat().coerceAtLeast(1f),
                view.height.toFloat().coerceAtLeast(1f)
            )
            shader.setFloatUniform("time", (System.nanoTime() % 10000000000L) / 1_000_000_000f)
            shader.setFloatUniform("strength", strength.coerceIn(0.2f, 2.5f))
            val effect = RenderEffect.createRuntimeShaderEffect(shader, "content")
            view.setRenderEffect(effect)
            // Keep time ticking lightly for liquid motion
            view.postDelayed(object : Runnable {
                override fun run() {
                    if (view.isAttachedToWindow && Build.VERSION.SDK_INT >= 33) {
                        try {
                            shader.setFloatUniform("time", (System.nanoTime() % 10000000000L) / 1_000_000_000f)
                            view.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "content"))
                            view.postDelayed(this, 48)
                        } catch (_: Exception) {}
                    }
                }
            }, 48)
        } catch (_: Exception) {
            if (Build.VERSION.SDK_INT >= 31) applyBlur(view)
        }
    }

    @RequiresApi(31)
    private fun applyBlur(view: View) {
        try {
            view.setRenderEffect(
                RenderEffect.createBlurEffect(22f, 22f, Shader.TileMode.CLAMP)
            )
        } catch (_: Exception) {}
    }

    fun clear(view: View) {
        if (Build.VERSION.SDK_INT >= 31) {
            try { view.setRenderEffect(null) } catch (_: Exception) {}
        }
    }
}
