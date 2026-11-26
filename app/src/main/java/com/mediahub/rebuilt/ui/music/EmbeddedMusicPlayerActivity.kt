package com.mediahub.rebuilt.ui.music

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mediahub.rebuilt.service.EmbeddedPlaybackService

/**
 * Dedicated host for the official provider embed.
 * Keeping the WebView in an Activity (rather than a transient dialog) allows
 * Android to retain its lifecycle while the user switches apps or locks the screen.
 * Provider-side background/DRM/subscription rules still apply.
 */
class EmbeddedMusicPlayerActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_EMBED_URL = "com.mediahub.rebuilt.extra.EMBED_URL"
    }

    private var playerWebView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val embedUrl = intent.getStringExtra(EXTRA_EMBED_URL)
        if (embedUrl.isNullOrBlank() || !embedUrl.startsWith("https://")) {
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 8, 13))
            setPadding(dp(16), dp(22), dp(16), dp(12))
        }
        root.addView(TextView(this).apply {
            text = "NOW PLAYING"
            textSize = 21f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Official music provider player"
            textSize = 12f
            setTextColor(Color.rgb(255, 160, 184))
            setPadding(0, dp(4), 0, dp(12))
        })
        val web = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
        }
        playerWebView = web
        root.addView(web)

        root.addView(TextView(this).apply {
            text = "Keep this player open in the background. Screen-off playback depends on the provider and its account rules."
            textSize = 11f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(10), dp(4), dp(10))
        })
        root.addView(TextView(this).apply {
            text = "CLOSE PLAYER"
            textSize = 12f
            setTextColor(Color.rgb(8, 8, 13))
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(12), dp(18), dp(12))
            setBackgroundColor(Color.rgb(255, 77, 141))
            setOnClickListener { finish() }
        })
        setContentView(root)

        ContextCompat.startForegroundService(
            this,
            android.content.Intent(this, EmbeddedPlaybackService::class.java)
                .setAction(EmbeddedPlaybackService.ACTION_START)
        )
        web.loadUrl(embedUrl)
    }

    override fun onDestroy() {
        playerWebView?.apply {
            stopLoading()
            loadUrl("about:blank")
            destroy()
        }
        playerWebView = null
        stopService(
            android.content.Intent(this, EmbeddedPlaybackService::class.java)
                .setAction(EmbeddedPlaybackService.ACTION_STOP)
        )
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
