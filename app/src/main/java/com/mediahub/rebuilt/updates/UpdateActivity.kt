package com.mediahub.rebuilt.updates

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.mediahub.rebuilt.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen Liquid Glass update experience with download progress.
 * Users never need to open GitHub in a browser.
 */
class UpdateActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressLabel: TextView
    private lateinit var updateButton: TextView
    private lateinit var laterButton: TextView

    private var downloading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = 0x00000000
        window.navigationBarColor = 0x00000000

        val version = intent.getStringExtra(UpdateChecker.EXTRA_UPDATE_VERSION) ?: ""
        val releaseApkUrl = intent.getStringExtra(UpdateChecker.EXTRA_UPDATE_APK_URL)
        val apkUrl = releaseApkUrl?.takeIf { it.isNotBlank() } ?: "https://github.com/sarahbanaah-jpg/MEDIAHUB-PRO/releases/latest/download/MEDIAHUB-PRO-v$version.apk"
        val notes = intent.getStringExtra(UpdateChecker.EXTRA_UPDATE_NOTES).orEmpty()
        val sizeBytes = intent.getLongExtra(UpdateChecker.EXTRA_UPDATE_SIZE, 0L)
        val mandatory = intent.getBooleanExtra(UpdateChecker.EXTRA_UPDATE_MANDATORY, false)

        val sizeLabel = when {
            sizeBytes <= 0L -> ""
            sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
            else -> String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0))
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF05040A.toInt())
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(56), dp(24), dp(40))
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "MEDIAHUB PRO"
            setTextColor(0xFFFF5C8A.toInt())
            textSize = 11f
            letterSpacing = 0.2f
            setTypeface(null, Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text = if (mandatory) "Critical update required" else "Update available"
            setTextColor(0xFFFFF8FC.toInt())
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(8))
        })

        root.addView(TextView(this).apply {
            text = "Current  v${BuildConfig.VERSION_NAME}\nLatest   v$version${if (sizeLabel.isNotEmpty()) "  ·  $sizeLabel" else ""}"
            setTextColor(0xFFC9B8C8.toInt())
            textSize = 14f
            setPadding(0, 0, 0, dp(20))
        })

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable().apply {
                setColor(0xFF16121F.toInt())
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), 0x33FFFFFF)
            }
        }
        card.addView(TextView(this).apply {
            text = "WHAT'S NEW"
            setTextColor(0xFFFF8FB0.toInt())
            textSize = 11f
            letterSpacing = 0.12f
            setTypeface(null, Typeface.BOLD)
        })
        card.addView(TextView(this).apply {
            text = notes.ifBlank {
                "Premium Liquid Glass UI, smoother playback, performance insights and seamless in-app updates."
            }
            setTextColor(0xFFEFE6F0.toInt())
            textSize = 14f
            setPadding(0, dp(10), 0, 0)
            setLineSpacing(4f, 1f)
        })
        root.addView(card)

        statusText = TextView(this).apply {
            text = "Ready to download and install"
            setTextColor(0xFFFF5C8A.toInt())
            textSize = 13f
            setPadding(0, dp(24), 0, dp(8))
        }
        root.addView(statusText)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            visibility = View.GONE
        }
        root.addView(progressBar)

        progressLabel = TextView(this).apply {
            text = ""
            setTextColor(0xFF8E7F93.toInt())
            textSize = 12f
            visibility = View.GONE
            setPadding(0, dp(6), 0, 0)
        }
        root.addView(progressLabel)

        updateButton = TextView(this).apply {
            text = "DOWNLOAD & INSTALL NOW"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = GradientDrawable().apply {
                setColor(0xFFFF5C8A.toInt())
                cornerRadius = dp(18).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28) }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (downloading) return@setOnClickListener
                if (apkUrl.isNullOrBlank()) {
                    Toast.makeText(this@UpdateActivity, "Update package not available yet.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                startDownload(apkUrl)
            }
        }
        root.addView(updateButton)

        laterButton = TextView(this).apply {
            text = if (mandatory) "You must update to continue" else "REMIND ME LATER"
            setTextColor(0xFFC9B8C8.toInt())
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(16), dp(20), dp(16))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (mandatory) {
                    Toast.makeText(this@UpdateActivity, "This update is required for security.", Toast.LENGTH_SHORT).show()
                } else {
                    finish()
                }
            }
        }
        root.addView(laterButton)

        root.addView(TextView(this).apply {
            text = "Tap DOWNLOAD & INSTALL NOW. Android will ask you to allow MediaHub Pro to install this update if that permission is not enabled. Your existing app data is preserved."
            setTextColor(0xFF8E7F93.toInt())
            textSize = 12f
            setPadding(0, dp(24), 0, 0)
        })
    }

    private fun startDownload(apkUrl: String) {
        downloading = true
        updateButton.isEnabled = false
        updateButton.alpha = 0.5f
        progressBar.visibility = View.VISIBLE
        progressLabel.visibility = View.VISIBLE
        statusText.text = "Downloading update…"

        lifecycleScope.launch {
            val result = UpdateChecker.downloadWithProgress(this@UpdateActivity, apkUrl) { downloaded, total ->
                runOnUiThread {
                    if (total > 0) {
                        val pct = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                        progressBar.isIndeterminate = false
                        progressBar.progress = pct
                        progressLabel.text = String.format(
                            "%d%%  ·  %.1f / %.1f MB",
                            pct,
                            downloaded / (1024.0 * 1024.0),
                            total / (1024.0 * 1024.0)
                        )
                    } else {
                        progressBar.isIndeterminate = true
                        progressLabel.text = String.format("%.1f MB downloaded", downloaded / (1024.0 * 1024.0))
                    }
                }
            }

            withContext(Dispatchers.Main) {
                downloading = false
                result.fold(
                    onSuccess = { file ->
                        statusText.text = "Verified · opening installer"
                        progressBar.progress = 100
                        progressLabel.text = "Download complete"
                        try {
                            UpdateChecker.installApk(this@UpdateActivity, file)
                        } catch (e: Exception) {
                            statusText.text = "Could not open installer"
                            Toast.makeText(this@UpdateActivity, e.message ?: "Install failed", Toast.LENGTH_LONG).show()
                            updateButton.isEnabled = true
                            updateButton.alpha = 1f
                        }
                    },
                    onFailure = { err ->
                        statusText.text = "Download failed"
                        progressLabel.text = err.message ?: "Unknown error"
                        updateButton.isEnabled = true
                        updateButton.alpha = 1f
                        Toast.makeText(this@UpdateActivity, err.message ?: "Download failed", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
