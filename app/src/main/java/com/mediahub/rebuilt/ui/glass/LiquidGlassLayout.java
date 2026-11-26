package com.mediahub.rebuilt.ui.glass;

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
 * GPU-backed, live backdrop glass for Android 13+.
 *
 * The panel samples the actual UI rendered behind it and runs an AGSL shader
 * that adds optical displacement, subtle chromatic separation, a refractive
 * rim and moving specular highlights. This is not a pre-rendered glass image
 * or a translucent gradient. Android versions below 13 use a restrained,
 * legible material fallback because RuntimeShader/AGSL is unavailable there.
 */
public final class LiquidGlassLayout extends LinearLayout {
    private static final String PREFS = "mediahub_settings";
    private static final String AGSL =
        "uniform shader backdrop;\n" +
        "uniform float2 panelSize;\n" +
        "uniform float cornerRadius;\n" +
        "uniform float time;\n" +
        "half4 main(float2 p) {\n" +
        "  float2 halfSize = panelSize * 0.5;\n" +
        "  float2 q = abs(p - halfSize) - (halfSize - float2(cornerRadius));\n" +
        "  float sd = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - cornerRadius;\n" +
        "  float2 radial = (p - halfSize) / max(halfSize, float2(1.0));\n" +
        "  float2 normal = normalize(radial + float2(0.0001));\n" +
        "  float rim = exp(-abs(sd) / 3.2);\n" +
        "  float inner = smoothstep(-cornerRadius, -2.0, sd);\n" +
        "  float wave = sin(p.y * 0.035 + time) * 0.45 + cos(p.x * 0.025 - time * 0.7) * 0.35;\n" +
        "  float2 bend = normal * (1.4 + rim * 5.5) + normal * wave;\n" +
        "  half4 center = backdrop.eval(p + bend);\n" +
        "  half red = backdrop.eval(p + bend + normal * (rim * 1.15)).r;\n" +
        "  half blue = backdrop.eval(p + bend - normal * (rim * 1.15)).b;\n" +
        "  half3 refracted = half3(red, center.g, blue);\n" +
        "  half3 tint = half3(0.055, 0.045, 0.085);\n" +
        "  half3 color = mix(refracted, tint, 0.13);\n" +
        "  half highlight = half(pow(max(0.0, 1.0 - abs((p.y / panelSize.y) - 0.08) * 11.0), 3.0) * 0.18);\n" +
        "  half rimLight = half(rim * 0.23);\n" +
        "  color += half3(highlight + rimLight * 0.88, highlight + rimLight, highlight + rimLight * 1.12);\n" +
        "  color += half3(0.018, 0.008, 0.024) * half(inner);\n" +
        "  return half4(color, 1.0);\n" +
        "}";

    private static WeakReference<View> cachedRoot = new WeakReference<>(null);
    private static Bitmap cachedBackdrop;
    private static long capturedAt;
    private static boolean captureInProgress;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private ViewTreeObserver.OnPreDrawListener preDrawListener;
    private RuntimeShaderHolder shaderHolder;

    public LiquidGlassLayout(Context context) { super(context); init(); }
    public LiquidGlassLayout(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public LiquidGlassLayout(Context context, AttributeSet attrs, int style) { super(context, attrs, style); init(); }

    private void init() {
        setWillNotDraw(false);
        setBackgroundColor(Color.TRANSPARENT);
        setClipToOutline(true);
        if (Build.VERSION.SDK_INT >= 33) setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    private boolean glassEnabled() {
        return getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("liquid_glass_enabled", true);
    }

    @Override protected void onFinishInflate() {
        super.onFinishInflate();
        setBackground(null);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        preDrawListener = () -> {
            if (!captureInProgress && glassEnabled() && isShown()) captureBackdropIfNeeded();
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
        capturedAt = 0L;
    }

    private void captureBackdropIfNeeded() {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        View root = getRootView();
        long now = System.currentTimeMillis();
        if (cachedRoot.get() == root && cachedBackdrop != null &&
            !cachedBackdrop.isRecycled() && now - capturedAt < 180L) return;
        int width = root.getWidth();
        int height = root.getHeight();
        if (width <= 0 || height <= 0) return;

        try {
            captureInProgress = true;
            if (cachedBackdrop == null || cachedBackdrop.isRecycled() ||
                cachedRoot.get() != root || cachedBackdrop.getWidth() != width ||
                cachedBackdrop.getHeight() != height) {
                Bitmap previous = cachedBackdrop;
                cachedBackdrop = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                if (previous != null && !previous.isRecycled()) previous.recycle();
                cachedRoot = new WeakReference<>(root);
            } else {
                cachedBackdrop.eraseColor(Color.TRANSPARENT);
            }
            root.draw(new Canvas(cachedBackdrop));
            capturedAt = now;
        } catch (OutOfMemoryError | RuntimeException ignored) {
            if (cachedBackdrop != null && !cachedBackdrop.isRecycled()) {
                cachedBackdrop.recycle();
                cachedBackdrop = null;
            }
        } finally {
            captureInProgress = false;
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        if (captureInProgress) return;
        if (!glassEnabled()) {
            drawFallback(canvas, 0xE61A1422);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && cachedBackdrop != null && !cachedBackdrop.isRecycled()) {
            try {
                drawRefractedBackdrop(canvas);
                return;
            } catch (RuntimeException ignored) { }
        }
        drawFallback(canvas, 0xC91B1427);
    }

    private void drawRefractedBackdrop(Canvas canvas) {
        if (shaderHolder == null) shaderHolder = new RuntimeShaderHolder(AGSL);
        int[] panel = new int[2];
        int[] root = new int[2];
        getLocationOnScreen(panel);
        getRootView().getLocationOnScreen(root);

        BitmapShader bitmapShader = new BitmapShader(
            cachedBackdrop, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix matrix = new Matrix();
        matrix.setTranslate(-(panel[0] - root[0]), -(panel[1] - root[1]));
        bitmapShader.setLocalMatrix(matrix);

        android.graphics.RuntimeShader shader = shaderHolder.shader;
        shader.setInputShader("backdrop", bitmapShader);
        shader.setFloatUniform("panelSize", (float) getWidth(), (float) getHeight());
        shader.setFloatUniform("cornerRadius", Math.min(getHeight() * 0.48f, dp(30)));
        shader.setFloatUniform("time", (System.currentTimeMillis() % 12000L) / 1200.0f);
        paint.setShader(shader);
        canvas.drawRoundRect(0, 0, getWidth(), getHeight(),
            dp(28), dp(28), paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(0x99FFFFFF);
        canvas.drawRoundRect(dp(0.6f), dp(0.6f), getWidth()-dp(0.6f),
            getHeight()-dp(0.6f), dp(28), dp(28), paint);
        paint.setStyle(Paint.Style.FILL);
        postInvalidateOnAnimation();
    }

    private void drawFallback(Canvas canvas, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawRoundRect(0, 0, getWidth(), getHeight(), dp(28), dp(28), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(0x77FFFFFF);
        canvas.drawRoundRect(dp(0.6f), dp(0.6f), getWidth()-dp(0.6f),
            getHeight()-dp(0.6f), dp(28), dp(28), paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static final class RuntimeShaderHolder {
        final android.graphics.RuntimeShader shader;
        RuntimeShaderHolder(String source) { shader = new android.graphics.RuntimeShader(source); }
    }
}
