package com.mediahub.rebuilt.camera

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale

/** CameraX camera experience inspired by modern Pro phone cameras; hardware capabilities are detected at runtime. */
class CameraActivity : Activity() {
    private lateinit var previewView: PreviewView
    private lateinit var zoomLabel: TextView
    private lateinit var zoom: SeekBar
    private lateinit var shutter: Button
    private lateinit var mode: Button
    private lateinit var flash: Button
    private lateinit var lens: Button
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var videoMode = false
    private var frontCamera = false
    private var flashEnabled = false
    private var maxZoom = 1f
    private var minZoom = 1f
    private var currentZoom = 1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        if (hasPermissions()) startCamera() else ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), REQUEST_CAMERA)
    }

    private fun buildUi() {
        val frame = FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()) }
        previewView = PreviewView(this).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType = PreviewView.ScaleType.FILL_CENTER }
        frame.addView(previewView, FrameLayout.LayoutParams(-1, -1))
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(22), dp(16), dp(26)); setBackgroundColor(0xCC08080D.toInt())
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(textButton("×") { finish() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        top.addView(textButton("PHOTO") { setMode(false) }, LinearLayout.LayoutParams(0, dp(44), 1f))
        top.addView(textButton("VIDEO") { setMode(true) }, LinearLayout.LayoutParams(0, dp(44), 1f))
        top.addView(textButton("⚡ OFF") { toggleFlash() }, LinearLayout.LayoutParams(0, dp(44), 1f).also { flash = topButtonRef })
        controls.addView(top)
        zoomLabel = TextView(this).apply { text = "1.0× · Detecting camera"; setTextColor(-1); textSize = 13f; gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0) }
        controls.addView(zoomLabel)
        zoom = SeekBar(this).apply { max = 100; progress = 0; setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) setZoom(progress) }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        }) }
        controls.addView(zoom, LinearLayout.LayoutParams(-1, dp(38)))
        val presets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf(0.5f, 1f, 2f, 3f, 5f, 10f).forEach { value ->
            presets.addView(textButton(if (value == 0.5f) ".5×" else "${value.toInt()}×") { setZoomRatio(value) },
                LinearLayout.LayoutParams(0, dp(42), 1f))
        }
        controls.addView(presets)
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(14), 0, 0) }
        bottom.addView(textButton("↻") { switchCamera() }, LinearLayout.LayoutParams(0, dp(58), 1f))
        shutter = Button(this).apply { text = "●"; textSize = 27f; setTextColor(-1); setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF2D55.toInt())); setOnClickListener { capture() } }
        bottom.addView(shutter, LinearLayout.LayoutParams(dp(82), dp(58)))
        bottom.addView(textButton("GRID") { Toast.makeText(this, "Use the framing guides on your device; grid overlay is coming in a later camera update.", Toast.LENGTH_SHORT).show() }, LinearLayout.LayoutParams(0, dp(58), 1f))
        controls.addView(bottom)
        val params = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
        frame.addView(controls, params)
        setContentView(frame)
    }

    private lateinit var topButtonRef: Button
    private fun textButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label; textSize = 12f; isAllCaps = false; setTextColor(-1)
        setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF24212B.toInt()))
        setOnClickListener { action() }
    }
    private fun hasPermissions() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startCamera()
        else { Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_LONG).show(); finish() }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val selector = if (frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
                val recorder = Recorder.Builder().setQualitySelector(QualitySelector.fromOrderedList(listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD))).build()
                videoCapture = VideoCapture.withOutput(recorder)
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, selector, preview, imageCapture, videoCapture)
                val state = camera?.cameraInfo?.zoomState?.value
                minZoom = state?.minZoomRatio ?: 1f
                maxZoom = state?.maxZoomRatio ?: 1f
                currentZoom = 1f.coerceIn(minZoom, maxZoom)
                zoomLabel.text = "1.0× · ${if (maxZoom > 1.05f) "Zoom range ${"%.1f".format(maxZoom)}×" else "No optical zoom reported; digital crop only"}"
                zoom.progress = 0
                camera?.cameraControl?.setZoomRatio(currentZoom)
            } catch (error: Exception) { Toast.makeText(this, "Camera unavailable: ${error.localizedMessage}", Toast.LENGTH_LONG).show() }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setZoom(progress: Int) {
        val ratio = minZoom + (maxZoom - minZoom) * progress / 100f
        setZoomRatio(ratio)
    }
    private fun setZoomRatio(requested: Float) {
        val ratio = requested.coerceIn(minZoom, maxZoom)
        currentZoom = ratio
        camera?.cameraControl?.setZoomRatio(ratio)
        zoomLabel.text = "${"%.1f".format(ratio)}× · ${if (maxZoom > 1.05f) "Available camera zoom" else "Digital crop"}"
        zoom.progress = if (maxZoom > minZoom) (((ratio - minZoom) / (maxZoom - minZoom)) * 100).toInt() else 0
    }
    private fun setMode(video: Boolean) {
        if (recording != null) return
        videoMode = video
        mode.text = if (video) "VIDEO" else "PHOTO"
        shutter.text = if (video) "●" else "◎"
    }
    private fun toggleFlash() {
        flashEnabled = !flashEnabled
        camera?.cameraControl?.enableTorch(flashEnabled)
        Toast.makeText(this, if (flashEnabled) "Torch on" else "Torch off", Toast.LENGTH_SHORT).show()
    }
    private fun switchCamera() {
        if (recording != null) return
        frontCamera = !frontCamera
        startCamera()
    }
    private fun capture() {
        if (videoMode) {
            if (recording != null) { recording?.stop(); recording = null; shutter.text = "●"; return }
            val name = "MEDIAHUB_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.mp4"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/MediaHub Pro")
            }
            val output = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values).build()
            try {
                var pending = videoCapture?.output?.prepareRecording(this, output) ?: return
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) pending = pending.withAudioEnabled()
                recording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
                    if (event is VideoRecordEvent.Finalize) {
                        recording = null; shutter.text = "●"
                        Toast.makeText(this, if (event.hasError()) "Video save failed." else "Video saved to Movies/MediaHub Pro.", Toast.LENGTH_LONG).show()
                    } else if (event is VideoRecordEvent.Start) shutter.text = "■"
                }
            } catch (error: Exception) { Toast.makeText(this, "Could not start video: ${error.localizedMessage}", Toast.LENGTH_LONG).show() }
        } else {
            val name = "MEDIAHUB_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/MediaHub Pro")
            }
            val options = ImageCapture.OutputFileOptions.Builder(contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build()
            imageCapture?.takePicture(options, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) { Toast.makeText(this@CameraActivity, "Photo saved to Pictures/MediaHub Pro.", Toast.LENGTH_SHORT).show() }
                override fun onError(error: ImageCaptureException) { Toast.makeText(this@CameraActivity, "Photo failed: ${error.localizedMessage}", Toast.LENGTH_LONG).show() }
            })
        }
    }
    override fun onDestroy() { recording?.stop(); super.onDestroy() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    companion object { private const val REQUEST_CAMERA = 7001 }
}
