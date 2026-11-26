package com.mediahub.rebuilt.camera

import android.Manifest
import androidx.appcompat.app.AppCompatActivity
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.graphics.Canvas
import android.graphics.Paint
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
import androidx.camera.core.FocusMeteringAction
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
import java.util.concurrent.TimeUnit

/** CameraX camera experience inspired by modern Pro phone cameras; hardware capabilities are detected at runtime. */
class CameraActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var zoomLabel: TextView
    private lateinit var zoom: SeekBar
    private lateinit var shutter: Button
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
    private lateinit var scaleDetector: ScaleGestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        if (hasPermissions()) startCamera() else ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), REQUEST_CAMERA)
    }

    private fun buildUi() {
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)

        val frame = FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()) }
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        frame.addView(previewView, FrameLayout.LayoutParams(-1, -1))

        gridOverlay = CompositionGridView().apply { visibility = View.GONE }
        frame.addView(gridOverlay, FrameLayout.LayoutParams(-1, -1))

        // Native-feeling camera gestures: pinch to zoom and tap to focus.
        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                setZoomRatio(currentZoom * detector.scaleFactor)
                return true
            }
        })
        previewView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP && event.pointerCount == 1 && !scaleDetector.isInProgress) {
                val point = previewView.meteringPointFactory.createPoint(event.x, event.y)
                val action = FocusMeteringAction.Builder(point)
                    .setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                camera?.cameraControl?.startFocusAndMetering(action)
            }
            true
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(8))
        }
        flashButton = textButton("⚡") { toggleFlash() }
        top.addView(flashButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        top.addView(textButton("⌃") {
            advancedPanel.visibility = if (advancedPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { leftMargin = dp(12) })
        top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        top.addView(textButton("↻") { switchCamera() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        frame.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        advancedPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(14))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xD9101015.toInt())
                cornerRadius = dp(22).toFloat()
            }
            visibility = View.GONE
        }
        zoomLabel = TextView(this).apply {
            text = "ZOOM"
            setTextColor(-1); textSize = 11f; gravity = Gravity.CENTER
        }
        advancedPanel.addView(zoomLabel)
        zoom = SeekBar(this).apply {
            max = 100; progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) setZoom(progress)
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }
        advancedPanel.addView(zoom, LinearLayout.LayoutParams(-1, dp(34)))
        advancedPanel.addView(TextView(this).apply {
            text = "EXPOSURE"; setTextColor(0xFFD8D3E2.toInt()); textSize = 10f; gravity = Gravity.CENTER
        })
        exposure = SeekBar(this).apply {
            max = 100; progress = 50
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && exposureMax >= exposureMin) {
                        val index = exposureMin + ((exposureMax - exposureMin) * progress / 100f).toInt()
                        camera?.cameraControl?.setExposureCompensationIndex(index)
                    }
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }
        advancedPanel.addView(exposure, LinearLayout.LayoutParams(-1, dp(34)))
        advancedPanel.addView(textButton(if (gridVisible) "GRID ON" else "GRID OFF") {
            gridVisible = !gridVisible
            gridOverlay.visibility = if (gridVisible) View.VISIBLE else View.GONE
        }, LinearLayout.LayoutParams(-1, dp(42)))
        val advancedParams = FrameLayout.LayoutParams(-1, -2, Gravity.TOP)
        advancedParams.setMargins(dp(18), dp(76), dp(18), 0)
        frame.addView(advancedPanel, advancedParams)

        val bottomPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(14), dp(16), dp(14), dp(22))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xE609090D.toInt())
                cornerRadii = floatArrayOf(dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), 0f, 0f, 0f, 0f)
            }
        }

        zoomPresets = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        bottomPanel.addView(zoomPresets, LinearLayout.LayoutParams(-1, dp(44)))

        val modes = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(12))
        }
        modeVideoButton = textButton("VIDEO") { setMode(true) }
        modePhotoButton = textButton("PHOTO") { setMode(false) }
        modes.addView(modeVideoButton, LinearLayout.LayoutParams(dp(100), dp(38)))
        modes.addView(modePhotoButton, LinearLayout.LayoutParams(dp(100), dp(38)).apply { leftMargin = dp(10) })
        bottomPanel.addView(modes)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        shutter = Button(this).apply {
            text = "●"; textSize = 28f; isAllCaps = false
            setTextColor(0xFF111111.toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0xFFFFFFFF.toInt())
                setStroke(dp(5), 0xFFBEBEC4.toInt())
            }
            setOnClickListener { capture() }
        }
        actions.addView(shutter, LinearLayout.LayoutParams(dp(76), dp(76)))
        actions.addView(textButton("↻") { switchCamera() }, LinearLayout.LayoutParams(dp(52), dp(52)).apply {
            leftMargin = dp(20)
        })
        actions.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        bottomPanel.addView(actions)
        frame.addView(bottomPanel, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        setContentView(frame)
        setMode(false)
    }

    private lateinit var flashButton: Button
    private lateinit var exposure: SeekBar
    private lateinit var advancedPanel: LinearLayout
    private lateinit var zoomPresets: LinearLayout
    private lateinit var modePhotoButton: Button
    private lateinit var modeVideoButton: Button
    private lateinit var gridOverlay: View
    private var exposureMin = 0
    private var exposureMax = 0
    private var gridVisible = false
    private fun textButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label; textSize = 13f; isAllCaps = false; setTextColor(-1)
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(0xAA202027.toInt())
            cornerRadius = dp(24).toFloat()
            setStroke(dp(1), 0x44FFFFFF)
        }
        minWidth = 0; minimumWidth = 0
        setPadding(dp(8), 0, dp(8), 0)
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
                val stillCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
                imageCapture = stillCapture
                val recorder = Recorder.Builder().setQualitySelector(QualitySelector.fromOrderedList(listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD))).build()
                val video = VideoCapture.withOutput(recorder)
                videoCapture = video
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, selector, preview, stillCapture, video)
                val state = camera?.cameraInfo?.zoomState?.value
                minZoom = state?.minZoomRatio ?: 1f
                maxZoom = state?.maxZoomRatio ?: 1f
                currentZoom = 1f.coerceIn(minZoom, maxZoom)
                val exposureRange = camera?.cameraInfo?.exposureState?.exposureCompensationRange
                exposureMin = exposureRange?.lower ?: 0
                exposureMax = exposureRange?.upper ?: 0
                exposure.progress = 50
                exposure.isEnabled = exposureMax > exposureMin
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
        shutter.text = if (video) "●" else "◎"
    }
    private fun toggleFlash() {
        flashEnabled = !flashEnabled
        camera?.cameraControl?.enableTorch(flashEnabled)
        flashButton.text = if (flashEnabled) "⚡ ON" else "⚡ OFF"
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
    private inner class CompositionGridView : View(this@CameraActivity) {
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99FFFFFF.toInt()
            strokeWidth = dp(1).toFloat()
            style = Paint.Style.STROKE
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val thirdW = width / 3f
            val thirdH = height / 3f
            canvas.drawLine(thirdW, 0f, thirdW, height.toFloat(), linePaint)
            canvas.drawLine(thirdW * 2, 0f, thirdW * 2, height.toFloat(), linePaint)
            canvas.drawLine(0f, thirdH, width.toFloat(), thirdH, linePaint)
            canvas.drawLine(0f, thirdH * 2, width.toFloat(), thirdH * 2, linePaint)
        }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    companion object { private const val REQUEST_CAMERA = 7001 }
}
