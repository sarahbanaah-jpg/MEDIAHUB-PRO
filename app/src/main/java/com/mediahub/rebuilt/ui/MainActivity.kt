package com.mediahub.rebuilt.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mediahub.rebuilt.R
import com.mediahub.rebuilt.ui.home.HomeFragment
import com.mediahub.rebuilt.ui.music.MusicFragment
import com.mediahub.rebuilt.ui.photos.PhotosFragment
import com.mediahub.rebuilt.ui.settings.SettingsFragment
import com.mediahub.rebuilt.ui.videos.VideosFragment
import com.mediahub.rebuilt.updates.UpdateChecker
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var contentContainer: FrameLayout
    private lateinit var bottomNav: LinearLayout
    private var updateDialogShown = false

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

        setContentView(R.layout.activity_main)

        contentContainer = findViewById(R.id.contentContainer)
        bottomNav = findViewById(R.id.bottomNav)

        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, bars.bottom + dp(6))
            insets
        }

        // Silent check on launch (may post notification + show dialog)
        UpdateChecker.check(this, notify = true) { info ->
            if (info != null && !isFinishing) showUpdateDialog(info.version, info.apkUrl, info.releaseUrl)
        }

        handleUpdateIntent(intent)

        if (hasMediaPermissions()) {
            showMainUi()
        } else {
            requestMediaPermissions()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUpdateIntent(intent)
    }

    private fun handleUpdateIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(UpdateChecker.EXTRA_UPDATE_AVAILABLE, false) != true) return
        val version = intent.getStringExtra(UpdateChecker.EXTRA_UPDATE_VERSION) ?: return
        val apkUrl = intent.getStringExtra(UpdateChecker.EXTRA_UPDATE_APK_URL)
        val releaseUrl = intent.getStringExtra(UpdateChecker.EXTRA_UPDATE_RELEASE_URL)
        showUpdateDialog(version, apkUrl, releaseUrl)
    }

    private fun showUpdateDialog(version: String, apkUrl: String?, releaseUrl: String?) {
        if (updateDialogShown || isFinishing) return
        updateDialogShown = true

        val canInstall = !apkUrl.isNullOrBlank()
        val message = if (canInstall) {
            "MediaHub Pro $version is available.\n\nTap Update now to download and install inside the app."
        } else {
            "MediaHub Pro $version is available.\n\nOpen the release page to download the APK."
        }

        val builder = AlertDialog.Builder(this)
            .setTitle("Update available")
            .setMessage(message)
            .setNegativeButton("Later") { d, _ ->
                updateDialogShown = false
                d.dismiss()
            }

        if (canInstall) {
            builder.setPositiveButton("Update now") { _, _ ->
                Toast.makeText(this, "Downloading update…", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    val error = UpdateChecker.downloadAndInstall(this@MainActivity, apkUrl!!)
                    updateDialogShown = false
                    if (error != null) {
                        Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
                        if (!releaseUrl.isNullOrBlank()) {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl)))
                        }
                    }
                }
            }
        } else if (!releaseUrl.isNullOrBlank()) {
            builder.setPositiveButton("Open download") { _, _ ->
                updateDialogShown = false
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl)))
            }
        }

        builder.setOnDismissListener { updateDialogShown = false }
        builder.show()
    }

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
            if (Build.VERSION.SDK_INT >= 34) {
                needed += Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            }
            needed += Manifest.permission.POST_NOTIFICATIONS
        } else {
            needed += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        permissionLauncher.launch(needed.toTypedArray())
    }

    private fun showPermissionDenied() {
        AlertDialog.Builder(this)
            .setTitle("Permissions required")
            .setMessage("MediaHub Pro needs access to your music, videos and photos to work.\n\nYou can grant them in Settings.")
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
        setupBottomNav()
        switchTo(HomeFragment(), R.id.navHome)
    }

    private fun setupBottomNav() {
        val tabs: List<Pair<Int, () -> Fragment>> = listOf(
            R.id.navHome to { HomeFragment() },
            R.id.navVideos to { VideosFragment() },
            R.id.navMusic to { MusicFragment() },
            R.id.navPhotos to { PhotosFragment() },
            R.id.navSettings to { SettingsFragment() }
        )
        tabs.forEach { (id, createFragment) ->
            findViewById<View>(id).setOnClickListener {
                switchTo(createFragment(), id)
            }
        }
    }

    private fun switchTo(fragment: Fragment, activeId: Int) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.contentContainer, fragment)
            .commit()

        val activeColor = 0xFFFF5C8A.toInt()
        val inactiveColor = 0xFF8E7F93.toInt()

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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
