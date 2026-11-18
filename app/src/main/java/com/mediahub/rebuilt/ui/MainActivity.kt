package com.mediahub.rebuilt.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Outline
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.mediahub.rebuilt.R
import com.mediahub.rebuilt.ui.home.HomeFragment
import com.mediahub.rebuilt.ui.music.MusicFragment
import com.mediahub.rebuilt.ui.photos.PhotosFragment
import com.mediahub.rebuilt.ui.settings.SettingsFragment
import com.mediahub.rebuilt.ui.videos.VideosFragment
import com.mediahub.rebuilt.ui.welcome.WelcomeVoice
import com.mediahub.rebuilt.updates.UpdateChecker

class MainActivity : AppCompatActivity() {

    private lateinit var contentContainer: FrameLayout
    private lateinit var bottomNav: LinearLayout
    private var mandatoryUpdate = false
    private var dynamicIsland: DynamicIslandOverlay? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasMediaPermissions()) showMainUi() else showPermissionDenied()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = 0x00000000
        window.navigationBarColor = 0x00000000

        // Real system blur behind windows where supported (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                window.setBackgroundBlurRadius(40)
            } catch (_: Throwable) { }
        }

        setContentView(R.layout.activity_main)

        contentContainer = findViewById(R.id.contentContainer)
        bottomNav = findViewById(R.id.bottomNav)
        dynamicIsland = DynamicIslandOverlay(this, findViewById(android.R.id.content))

        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val lp = v.layoutParams as FrameLayout.LayoutParams
            lp.bottomMargin = bars.bottom + dp(12)
            v.layoutParams = lp
            insets
        }

        WelcomeVoice.speakOnce(this)

        UpdateChecker.check(this, notify = true) { info ->
            if (info != null && !isFinishing) {
                if (info.mandatory) {
                    mandatoryUpdate = true
                    UpdateChecker.openUpdateScreen(this, info)
                } else {
                    UpdateChecker.openUpdateScreen(this, info)
                }
            }
        }

        if (hasMediaPermissions()) {
            showMainUi()
        } else {
            requestMediaPermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        dynamicIsland?.refresh()
        dynamicIsland?.start()
        if (mandatoryUpdate) {
            UpdateChecker.check(this, notify = false) { info ->
                if (info != null && info.mandatory && !isFinishing) {
                    UpdateChecker.openUpdateScreen(this, info)
                } else {
                    mandatoryUpdate = false
                }
            }
        }
    }

    override fun onStop() {
        dynamicIsland?.stop()
        super.onStop()
    }

    fun refreshDynamicIsland() { dynamicIsland?.refresh() }

    private fun hasMediaPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < 33) {
            return ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
        val audio = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        val video = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        val images = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        val selected = Build.VERSION.SDK_INT >= 34 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
        return audio || video || images || selected
    }

    private fun requestMediaPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            needed += Manifest.permission.READ_MEDIA_AUDIO
            needed += Manifest.permission.READ_MEDIA_VIDEO
            needed += Manifest.permission.READ_MEDIA_IMAGES
            if (Build.VERSION.SDK_INT >= 34) needed += Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            needed += Manifest.permission.POST_NOTIFICATIONS
        } else {
            needed += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        permissionLauncher.launch(needed.toTypedArray())
    }

    private fun showPermissionDenied() {
        AlertDialog.Builder(this)
            .setTitle("Permissions required")
            .setMessage("MediaHub Pro needs access to your music, videos and photos to work.")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                })
            }
            .setNegativeButton("Try again") { _, _ -> requestMediaPermissions() }
            .setCancelable(false)
            .show()
    }

    private fun showMainUi() {
        bottomNav.visibility = View.VISIBLE
        applyRealLiquidGlass(bottomNav)
        setupBottomNav()
        switchTo(HomeFragment(), R.id.navHome)
    }

    /**
     * Real translucent liquid glass:
     * - Content under the bar is visible through the translucent fill
     * - Rounded clip + elevation for physical glass depth
     * - Android 12+ RenderEffect soft frosted polish where supported
     * - System window background blur on S+
     */
    private fun applyRealLiquidGlass(bar: View) {
        bar.clipToOutline = true
        bar.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), dp(32).toFloat())
            }
        }
        bar.post {
            bar.invalidateOutline()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    // Soft native frosted polish (does not replace translucency — enhances it)
                    bar.setRenderEffect(
                        RenderEffect.createBlurEffect(1.5f, 1.5f, Shader.TileMode.CLAMP)
                    )
                } catch (_: Throwable) { }
            }
        }
    }

    private fun setupBottomNav() {
        val tabs: List<Pair<Int, () -> Fragment>> = listOf(
            R.id.navHome to { HomeFragment() },
            R.id.navMusic to { MusicFragment() },
            R.id.navVideos to { VideosFragment() },
            R.id.navPhotos to { PhotosFragment() },
            R.id.navSettings to { SettingsFragment() }
        )
        tabs.forEach { (id, createFragment) ->
            findViewById<View>(id).setOnClickListener { switchTo(createFragment(), id) }
        }
    }

    private fun switchTo(fragment: Fragment, activeId: Int) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.contentContainer, fragment)
            .commit()

        val activeColor = 0xFFFF2D95.toInt()
        val inactiveColor = 0xFF9A8BB0.toInt()
        listOf(R.id.navHome, R.id.navVideos, R.id.navMusic, R.id.navPhotos, R.id.navSettings).forEach { id ->
            val view = findViewById<View>(id)
            val label = view.findViewWithTag<TextView>("label")
            val icon = view.findViewWithTag<TextView>("icon")
            val active = id == activeId
            label?.setTextColor(if (active) activeColor else inactiveColor)
            icon?.setTextColor(if (active) activeColor else inactiveColor)
            label?.typeface = if (active) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
            view.setBackgroundResource(if (active) R.drawable.bg_nav_pill_active else 0)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
