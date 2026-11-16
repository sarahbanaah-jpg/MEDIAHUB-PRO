package com.mediahub.rebuilt;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import java.lang.ref.WeakReference;

/**
 * Hardware-gated liquid-glass panel. On Android 13+ it samples the rendered backdrop and
 * displaces those pixels through an AGSL shader, producing actual backdrop refraction rather
 * than merely drawing a translucent blur. Older devices receive a restrained glass fallback.
 */
public final class LiquidGlassLinearLayout extends LinearLayout {
    private static final String PREFS = "mediahub_settings";
    private static final String AGSL =
        "uniform shader backdrop;\n" +
        "uniform float2 panelSize;\n" +
        "half4 main(float2 p) {\n" +
        "  float2 uv = p / panelSize;\n" +
        "  float2 centered = (uv - float2(0.5, 0.5)) * 2.0;\n" +
        "  float radius = length(centered);\n" +
        "  float edge = 1.0 - smoothstep(0.42, 1.05, radius);\n" +
        "  float2 normal = centered / max(radius, 0.001);\n" +
        "  half4 sample = backdrop.eval(p + normal * (7.0 * edge));\n" +
        "  half rim = half(smoothstep(0.62, 1.02, radius));\n" +
        "  half3 tint = half3(0.10, 0.045, 0.075);\n" +
        "  half3 rgb = mix(sample.rgb, tint, 0.17) + half3(0.13, 0.075, 0.11) * rim;\n" +
        "  return half4(rgb, 0.90);\n" +
        "}";

    private static WeakReference<View> cachedRoot = new WeakReference<>(null);
    private static Bitmap cachedBackdrop;
    private static long capturedAt;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private ViewTreeObserver.OnPreDrawListener preDrawListener;
    private Object shaderHolder;
    private boolean capturing;

    public LiquidGlassLinearLayout(Context c) { super(c); init(); }
    public LiquidGlassLinearLayout(Context c, AttributeSet a) { super(c, a); init(); }
    public LiquidGlassLinearLayout(Context c, AttributeSet a, int style) { super(c, a, style); init(); }

    private void init() {
        setWillNotDraw(false);
        setBackgroundColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 33) setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    private boolean glassEnabled() {
        return getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("liquid_glass_enabled", true);
    }

    @Override protected void onFinishInflate() {
        super.onFinishInflate();
        // XML card backgrounds would cover the sampled backdrop; the glass shader owns this surface.
        setBackground(null);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        preDrawListener = () -> {
            if (!capturing && glassEnabled() && isShown()) captureBackdropIfNeeded();
            return true;
        };
        getViewTreeObserver().addOnPreDrawListener(preDrawListener);
    }

    @Override protected void onDetachedFromWindow() {
        if (preDrawListener != null && getViewTreeObserver().isAlive()) {
            getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
        }
        preDrawListener = null;
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        capturedAt = 0;
    }

    private void captureBackdropIfNeeded() {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        View root = getRootView();
        long now = System.currentTimeMillis();
        if (cachedRoot.get() == root && cachedBackdrop != null && now - capturedAt < 350) return;
        int width = root.getWidth(), height = root.getHeight();
        if (width <= 0 || height <= 0) return;
        Bitmap next = null;
        try {
            capturing = true;
            next = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            int oldVisibility = getVisibility();
            setVisibility(INVISIBLE);
            root.draw(new Canvas(next));
            setVisibility(oldVisibility);
            Bitmap previous = cachedBackdrop;
            cachedBackdrop = next;
            cachedRoot = new WeakReference<>(root);
            capturedAt = now;
            if (previous != null && previous != next && !previous.isRecycled()) previous.recycle();
        } catch (OutOfMemoryError | RuntimeException ignored) {
            if (next != null && !next.isRecycled()) next.recycle();
        } finally {
            if (getVisibility() != VISIBLE) setVisibility(VISIBLE);
            capturing = false;
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!glassEnabled()) {
            drawFallback(canvas, 0xff211a22);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && cachedBackdrop != null && !cachedBackdrop.isRecycled()) {
            try {
                drawRefractedBackdrop(canvas);
                return;
            } catch (RuntimeException ignored) { }
        }
        drawFallback(canvas, 0x992d202b);
    }

    private void drawRefractedBackdrop(Canvas canvas) {
        if (shaderHolder == null) shaderHolder = new Api33Shader(AGSL);
        Api33Shader holder = (Api33Shader) shaderHolder;
        BitmapShader bitmap = new BitmapShader(cachedBackdrop, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        int[] panel = new int[2], root = new int[2];
        getLocationOnScreen(panel);
        getRootView().getLocationOnScreen(root);
        Matrix matrix = new Matrix();
        matrix.setTranslate(-(panel[0] - root[0]), -(panel[1] - root[1]));
        bitmap.setLocalMatrix(matrix);
        holder.shader.setInputShader("backdrop", bitmap);
        holder.shader.setFloatUniform("panelSize", (float) getWidth(), (float) getHeight());
        paint.setShader(holder.shader);
        canvas.drawRoundRect(0, 0, getWidth(), getHeight(), 22f, 22f, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f);
        paint.setColor(0x99ffffff);
        canvas.drawRoundRect(0.6f, 0.6f, getWidth()-0.6f, getHeight()-0.6f, 22f, 22f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawFallback(Canvas canvas, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawRoundRect(0, 0, getWidth(), getHeight(), 22f, 22f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f);
        paint.setColor(0x665e5360);
        canvas.drawRoundRect(0.6f, 0.6f, getWidth()-0.6f, getHeight()-0.6f, 22f, 22f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private static final class Api33Shader {
        final android.graphics.RuntimeShader shader;
        Api33Shader(String source) { shader = new android.graphics.RuntimeShader(source); }
    }
}
