package com.mediahub.rebuilt.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
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
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.mediahub.rebuilt.R
import com.mediahub.rebuilt.service.PlaybackService
import com.mediahub.rebuilt.ui.home.HomeFragment
import com.mediahub.rebuilt.ui.music.MusicFragment
import com.mediahub.rebuilt.ui.music.NowPlayingActivity
import com.mediahub.rebuilt.ui.photos.PhotosFragment
import com.mediahub.rebuilt.ui.settings.SettingsFragment
import com.mediahub.rebuilt.ui.videos.VideosFragment
import com.mediahub.rebuilt.updates.UpdateChecker

class MainActivity : AppCompatActivity() {

    private lateinit var contentContainer: FrameLayout
    private lateinit var bottomNav: LinearLayout
    private lateinit var miniPlayer: LinearLayout
    private lateinit var miniPlayerLabel: TextView
    private lateinit var miniPlayerPlay: TextView
    private var mandatoryUpdate = false
    private var dynamicIsland: DynamicIslandOverlay? = null
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasMediaPermissions()) showMainUi() else showPermissionDenied()
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = refreshMiniPlayer()
        override fun onMediaItemTransition(
            mediaItem: androidx.media3.common.MediaItem?,
            reason: Int
        ) = refreshMiniPlayer()
        override fun onPlaybackStateChanged(playbackState: Int) = refreshMiniPlayer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = 0x00000000
        window.navigationBarColor = 0x00000000

        setContentView(R.layout.activity_main)

        contentContainer = findViewById(R.id.contentContainer)
        bottomNav = findViewById(R.id.bottomNav)
        miniPlayer = findViewById(R.id.miniPlayer)
        miniPlayerLabel = findViewById(R.id.miniPlayerLabel)
        miniPlayerPlay = findViewById(R.id.miniPlayerPlay)
        dynamicIsland = DynamicIslandOverlay(this, findViewById(android.R.id.content))

        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val lp = v.layoutParams as FrameLayout.LayoutParams
            lp.bottomMargin = bars.bottom + dp(12)
            v.layoutParams = lp
            val miniLp = miniPlayer.layoutParams as FrameLayout.LayoutParams
            miniLp.bottomMargin = bars.bottom + dp(80)
            miniPlayer.layoutParams = miniLp
            insets
        }

        miniPlayer.setOnClickListener {
            startActivity(Intent(this, NowPlayingActivity::class.java))
        }
        miniPlayerPlay.setOnClickListener {
            val p = controller ?: return@setOnClickListener
            if (p.isPlaying) p.pause() else p.play()
        }

        if (!isOfflineModeEnabled()) {
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
        }

        if (hasMediaPermissions()) {
            showMainUi()
        } else {
            requestMediaPermissions()
        }
    }

    override fun onStart() {
        super.onStart()
        connectPlayer()
    }

    override fun onResume() {
        super.onResume()
        dynamicIsland?.refresh()
        dynamicIsland?.start()
        refreshMiniPlayer()
        if (mandatoryUpdate && !isOfflineModeEnabled()) {
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
        controller?.removeListener(playerListener)
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    fun refreshDynamicIsland() { dynamicIsland?.refresh() }

    private fun connectPlayer() {
        if (controllerFuture != null) return
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                controller = future.get()
                controller?.addListener(playerListener)
                refreshMiniPlayer()
            } catch (_: Exception) { }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun refreshMiniPlayer() {
        val p = controller
        val item = p?.currentMediaItem
        if (p == null || item == null) {
            miniPlayer.visibility = View.GONE
            return
        }
        miniPlayer.visibility = View.VISIBLE
        val title = item.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() } ?: "Now Playing"
        val artist = item.mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() }
        miniPlayerLabel.text = if (artist != null) "$title · $artist" else title
        miniPlayerPlay.text = if (p.isPlaying) "Ⅱ" else "▶"
    }

    private fun isOfflineModeEnabled(): Boolean =
        getSharedPreferences("mediahub_settings", MODE_PRIVATE).getBoolean("offline_mode", false)

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
        setupBottomNav()
        switchTo(HomeFragment(), R.id.navHome)
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
        val inactiveColor = 0xFF8E8E9A.toInt()
        listOf(R.id.navHome, R.id.navMusic, R.id.navVideos, R.id.navPhotos, R.id.navSettings).forEach { id ->
            val view = findViewById<View>(id)
            val label = view.findViewWithTag<TextView>("label")
            val icon = view.findViewWithTag<TextView>("icon")
            val active = id == activeId
            label?.setTextColor(if (active) activeColor else inactiveColor)
            icon?.setTextColor(if (active) activeColor else inactiveColor)
            label?.typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            // Soft rounded highlight only on active — no giant oval
            view.setBackgroundResource(if (active) R.drawable.bg_nav_item_active else 0)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
