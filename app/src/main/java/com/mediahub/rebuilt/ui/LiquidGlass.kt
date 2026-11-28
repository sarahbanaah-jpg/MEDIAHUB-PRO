package com.mediahub.rebuilt.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi

/**
 * Smooth continuous liquid glass.
 * - apply(): general UI panels (music cards etc.)
 * - applySubtle(): keyboard background only — low strength, no heavy blur so overlay text stays sharp
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
            float2 flow = float2(time * 0.05, time * 0.032);
            float n1 = fbm(uv * 1.6 + flow);
            float n2 = fbm(uv * 2.2 - flow * 1.2 + float2(4.1, 1.3));

            float e = 0.014;
            float dx = fbm(uv * 1.6 + flow + float2(e, 0.0)) - fbm(uv * 1.6 + flow - float2(e, 0.0));
            float dy = fbm(uv * 1.6 + flow + float2(0.0, e)) - fbm(uv * 1.6 + flow - float2(0.0, e));

            float amp = strength * 10.0;
            float2 offset = float2(dx, dy) * amp + float2(n1 - 0.5, n2 - 0.5) * amp * 0.3;

            float2 sampleAt = clamp(fragCoord + offset, float2(1.0), size - float2(1.0));
            half4 color = content.eval(sampleAt);

            float caustic = smoothstep(0.4, 0.78, n1) * smoothstep(0.35, 0.72, n2);
            color.rgb += half3(caustic * 0.08, caustic * 0.09, caustic * 0.11);

            float edge = smoothstep(0.0, 0.1, uv.x) * smoothstep(0.0, 0.1, uv.y)
                * smoothstep(0.0, 0.1, 1.0 - uv.x) * smoothstep(0.0, 0.1, 1.0 - uv.y);
            color.rgb += half3(0.03, 0.035, 0.045) * (1.0 - edge);

            color.a = max(color.a, half(0.85));
            return color;
        }
    """

    fun apply(view: View, strength: Float = 1.0f) {
        if (Build.VERSION.SDK_INT >= 33) {
            applyAgsl(view, strength, withBlur = true)
        } else if (Build.VERSION.SDK_INT >= 31) {
            applyBlur(view, 18f)
        }
    }

    /** Keyboard background: continuous bend, no heavy blur so keys on top stay sharp. */
    fun applySubtle(view: View) {
        if (Build.VERSION.SDK_INT >= 33) {
            applyAgsl(view, 0.55f, withBlur = false)
        } else if (Build.VERSION.SDK_INT >= 31) {
            applyBlur(view, 6f)
        }
    }

    @RequiresApi(33)
    private fun applyAgsl(view: View, strength: Float, withBlur: Boolean) {
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
            shader.setFloatUniform("strength", strength.coerceIn(0.2f, 2.0f))

            fun buildEffect(): RenderEffect {
                val glass = RenderEffect.createRuntimeShaderEffect(shader, "content")
                return if (withBlur) {
                    RenderEffect.createChainEffect(
                        glass,
                        RenderEffect.createBlurEffect(6f, 6f, Shader.TileMode.CLAMP)
                    )
                } else {
                    glass
                }
            }
            view.setRenderEffect(buildEffect())

            view.postDelayed(object : Runnable {
                override fun run() {
                    if (!view.isAttachedToWindow || Build.VERSION.SDK_INT < 33) return
                    try {
                        shader.setFloatUniform(
                            "time",
                            (System.nanoTime() % 100_000_000_000L) / 1_000_000_000f
                        )
                        updateSize()
                        view.setRenderEffect(buildEffect())
                        view.postDelayed(this, 40)
                    } catch (_: Exception) {
                    }
                }
            }, 40)
        } catch (_: Exception) {
            if (Build.VERSION.SDK_INT >= 31) applyBlur(view, if (withBlur) 18f else 6f)
        }
    }

    @RequiresApi(31)
    private fun applyBlur(view: View, radius: Float) {
        try {
            view.setRenderEffect(
                RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
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
