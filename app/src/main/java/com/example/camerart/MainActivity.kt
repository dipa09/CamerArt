package com.example.camerart

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.*
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.view.GestureDetectorCompat
import androidx.preference.PreferenceManager
import com.example.camerart.databinding.ActivityMainBinding
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.math.abs

typealias LumaListener = (luma: Double) -> Unit

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "CamerArt"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()

        const val JPEG_QUALITY_UNINITIALIZED: Int = 0
        const val JPEG_QUALITY_LATENCY: Int = 95
        const val JPEG_QUALITY_MAX: Int = 100
        const val TARGET_ROTATION_UNINITIALIZED = -1

        const val SWIPE_THRESHOLD = 100
        const val SWIPE_VELOCITY_THRESHOLD = 100

        const val MIME_TYPE_JPEG = "image/jpeg"
        const val MIME_TYPE_PNG  = "image/png"
        const val MIME_TYPE_WEBP = "image/webp"
    }

    enum class CameraUseCase(val value: Int) {
        NONE(0),
        PREVIEW(1 shl 0),
        CAPTURE(1 shl 1),
        VIDEO(1 shl 2),

        PREVIEW_CAPTURE(PREVIEW.value or CAPTURE.value),
        PREVIEW_VIDEO(PREVIEW.value or VIDEO.value),
        PREVIEW_CAPTURE_VIDEO(PREVIEW.value or CAPTURE.value or VIDEO.value)
    }

    enum class SupportedQuality { NONE, SD, HD, FHD, UHD, COUNT }

    enum class Mime(private val mime: String) {
        JPEG(MIME_TYPE_JPEG),
        PNG(MIME_TYPE_PNG),
        WEBP(MIME_TYPE_WEBP),

        COUNT("");

        override fun toString(): String {
            return mime
        }
    }

    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService

    // Gesture stuff
    private lateinit var  commonDetector: GestureDetectorCompat
    private lateinit var scaleDetector: ScaleGestureDetector
    private var scaling: Boolean = false
    //

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var currCamInfo: CameraInfo? = null
    private var currCamControl: CameraControl? = null

    private var currUseCase: CameraUseCase = CameraUseCase.PREVIEW_CAPTURE
    // Capture
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var captureMode: Int = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
    private var flashMode: Int = ImageCapture.FLASH_MODE_AUTO
    private var jpegQuality: Int = JPEG_QUALITY_UNINITIALIZED
    private var targetRotation: Int = TARGET_ROTATION_UNINITIALIZED
    private var fancyCapture: Boolean = false

    // Video
    private var audioEnabled: Boolean = true
    private var videoQuality: Quality = Quality.HIGHEST
    private var playing: Boolean = false

    // Preview
    private var scaleType: PreviewView.ScaleType = PreviewView.ScaleType.FIT_CENTER

    // Behavioral
    private var photoOnClickEnabled: Boolean = false
    //

    private lateinit var supportedMimes: IntArray
    private var requestedFormat: Mime = Mime.JPEG
    private var exposureCompensationIndex: Int = 0

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(baseContext, "Permission request denied", Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
            }
        }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*
        // NOTE(davide): This must come before setContentView
        with(window) {
            requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
            enterTransition = Slide()
            exitTransition = Slide()
        }
        */

        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        commonDetector = GestureDetectorCompat(viewBinding.viewFinder.context, commonListener)
        scaleDetector = ScaleGestureDetector(viewBinding.viewFinder.context, scaleListener)

        if (allPermissionsGranted()) {
            getAvailableMimes()
            loadPreferences()
            startCamera()
        } else {
            requestPermissions()
        }

        viewBinding.imageCaptureButton.setOnClickListener { takePhoto() }
        viewBinding.videoCaptureButton.setOnClickListener { captureVideo() }
        viewBinding.muteButton.setOnClickListener { toggleAudio() }
        viewBinding.cameraButton.setOnClickListener { toggleCamera() }
        viewBinding.settingsButton.setOnClickListener { launchSetting() }
        viewBinding.playButton.setOnClickListener { controlVideoRecording() }

        viewBinding.viewFinder.setOnTouchListener { _, motionEvent ->
            scaleDetector.onTouchEvent(motionEvent)
            if (!scaling)
                commonDetector.onTouchEvent(motionEvent)

            return@setOnTouchListener true
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun toggleAudio() {
        audioEnabled = !audioEnabled
        val alpha = if (audioEnabled) 0xff/2 else 0xff
        viewBinding.muteButton.background.alpha = alpha
    }

    private fun toggleCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            viewBinding.cameraButton.background.alpha = 0xff/2
            CameraSelector.LENS_FACING_FRONT
        } else {
            viewBinding.cameraButton.background.alpha = 0xff
            CameraSelector.LENS_FACING_BACK
        }
        startCamera()
    }

    private fun launchSetting() {
        val intent = Intent(this, SettingActivity::class.java)

        val camInfo = currCamInfo
        if (camInfo != null) {
            // TODO(davide): Do this only when the camera has changed. e.g. front/back
            val qualities = QualitySelector.getSupportedQualities(camInfo)

            val values = Array<String>(qualities.size + 2){""}
            val resolutionNames = Array<String>(qualities.size + 2){""}

            for (i in qualities.indices) {
                val quality = qualities[i]
                var prefix: String = ""
                var p: Int

                when (quality) {
                    Quality.SD -> {
                        prefix = "SD"
                        p = 480
                        values[i] = SupportedQuality.SD.name
                    }
                    Quality.HD -> {
                        prefix = "HD"
                        p = 720
                        values[i] = SupportedQuality.HD.name
                    }
                    Quality.FHD -> {
                        prefix = "Full HD"
                        p = 1080
                        values[i] = SupportedQuality.FHD.name
                    }
                    Quality.UHD -> {
                        prefix = "4K ultra HD"
                        p = 2160
                        values[i] = SupportedQuality.UHD.name
                    }
                    else -> continue
                }

                val size = QualitySelector.getResolution(camInfo, quality)
                resolutionNames[i] = prefix + " " + size.toString() + " (${p}p)"
            }
            values[values.size - 2] = "Highest"
            values[values.size - 1] = "Lowest"
            resolutionNames[resolutionNames.size - 2] = QualitySelector.getResolution(camInfo, Quality.HIGHEST).toString()
            resolutionNames[resolutionNames.size - 1] = QualitySelector.getResolution(camInfo, Quality.LOWEST).toString()

            intent.putExtra("supportedQualities", values)
            intent.putExtra("supportedResolutions", resolutionNames)

            intent.putExtra("supportedImageFormats", supportedMimes)

            intent.putExtra("exposureState", exposureStateToBundle(camInfo.exposureState))
        }

        this.startActivity(intent, ActivityOptions.makeSceneTransitionAnimation(this).toBundle())
    }

    private fun getAvailableMimes() {
        val availMimes = ArrayList<Int>(Mime.COUNT.ordinal)

        // TODO(davide): Check API level for WEBP
        val mimes = Mime.values()
        for (i in 0 until Mime.COUNT.ordinal) {
            val mime = mimes[i].toString()
            if (MimeTypeMap.getSingleton().hasMimeType(mime))
                availMimes.add(i)
        }

        assert(availMimes.size > 0)
        supportedMimes = availMimes.toIntArray()
    }

    // NOTE(davide): For some reason, sometimes you need to delete app's data if you edit
    // a previous preference from root_preferences.xml or arrays.xml, otherwise you get
    // a random exception.
    private fun loadPreferences(): Boolean {
        // TODO(davide): Set this
        var restartCamera = true

        val sharedPreference = PreferenceManager.getDefaultSharedPreferences(this)

        var newCaptureMode: Int = captureMode

        val prefs = sharedPreference.all
        for (pref in prefs.iterator()) {
            //Log.i(TAG, "preference ${pref.key}, ${pref.value}")

            when (pref.key) {
                "pref_flash" -> {
                    flashMode = when(pref.value) {
                        "on" -> ImageCapture.FLASH_MODE_ON
                        "off" -> ImageCapture.FLASH_MODE_OFF
                        else -> ImageCapture.FLASH_MODE_AUTO
                    }
                }

                "pref_capture" -> {
                    /*
                    if (currCameraInfo != null && currCameraInfo.isZslSupported) {
                    }*/
                    newCaptureMode = when(pref.value) {
                        "quality" -> ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                        //"zero" -> ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG
                        else -> ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                    }
                }

                "pref_image_format" -> {
                    requestedFormat = try {
                        enumValueOf(pref.value as String)
                    } catch (ecx: IllegalArgumentException) {
                        Mime.JPEG
                    }
                }

                "pref_jpeg_quality" -> {
                    jpegQuality = pref.value as Int
                }

                "pref_rotation" -> {
                    when (pref.value) {
                        "0" -> targetRotation = Surface.ROTATION_0
                        "90" -> targetRotation = Surface.ROTATION_90
                        "180" -> targetRotation = Surface.ROTATION_180
                        "270" -> targetRotation = Surface.ROTATION_270
                    }
                }

                "pref_photo_on_click" -> {
                    photoOnClickEnabled = pref.value as Boolean
                }

                "pref_scale" -> {
                    when (pref.value) {
                        "center" -> scaleType = PreviewView.ScaleType.FIT_CENTER
                        "start" -> scaleType = PreviewView.ScaleType.FIT_START
                        "end" -> scaleType = PreviewView.ScaleType.FIT_END
                    }
                }

                // TODO(davide): Temporary. The user shouldn't be aware of this
                "pref_use_video_temp" -> {
                    currUseCase = if (pref.value as Boolean)
                        CameraUseCase.PREVIEW_VIDEO
                    else
                        CameraUseCase.PREVIEW_CAPTURE
                }

                "pref_video_quality" -> {
                    videoQuality = when (pref.value) {
                        "SD" -> Quality.SD
                        "HD" -> Quality.HD
                        "FHD" -> Quality.FHD
                        "UHD" -> Quality.UHD
                        "Lowest" -> Quality.LOWEST
                        else -> Quality.HIGHEST
                    }
                }

                "pref_exposure" -> {
                    exposureCompensationIndex = pref.value as Int
                }

            }
        }

        // NOTE(davide): Change JPEG quality unless the user changed the capture mode
        if (newCaptureMode != captureMode) {
            jpegQuality = JPEG_QUALITY_UNINITIALIZED
        }

        return restartCamera
    }

    override fun onResume() {
        super.onResume()

        if (loadPreferences())
            startCamera()
        Log.i(TAG, "ON RESUME ENDED")
    }

    private fun makeContentValues(displayName: String, mimeType: String): ContentValues {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                //put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        return values
    }

    // TODO(davide): Add more metadata. Location, producer, ...
    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
        if (fancyCapture || requestedFormat != Mime.JPEG) {
            val contentValues = makeContentValues(name, requestedFormat.toString())
            // TODO(davide): Launch another executor for png, since it's very slow
            imageCapture.takePicture(
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        // NOTE(davide): Apparently there is no way to tell CameraX to NOT compress
                        // the image in JPEG
                        val img = Image(image, requestedFormat)
                        val uri = saveImage(contentResolver, contentValues, img)
                        if (uri != null) {
                            Toast.makeText(baseContext, "Saved photo in $uri", Toast.LENGTH_SHORT).show()
                        }
                        super.onCaptureSuccess(image)
                    }

                    override fun onError(exc: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    }
                })
        } else {
            val outputOptions = ImageCapture.OutputFileOptions
                .Builder(contentResolver,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    makeContentValues(name, "image/jpeg"))
                .build()

            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val msg = "Photo capture succeeded: ${output.savedUri}"
                        Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                        Log.d(TAG, msg)
                    }

                    override fun onError(exc: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    }
                }
            )
        }
    }

    private fun saveImage(resolver: ContentResolver, values: ContentValues, img: Image): Uri? {
        var uri: Uri? = null
        try {
            uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Failed to create MediaStore record")

            resolver.openOutputStream(uri)?.use {
                img.write(it)
            } ?: throw IOException("Failed to open output stream")

        } catch (exc: IOException) {
            uri?.let { orphanUri ->
                resolver.delete(orphanUri, null, null)
            }
        }
        return uri
    }

    private fun controlVideoRecording() {
        val rec = recording
        if (rec != null) {
            if (playing) {
                rec.pause()
                Toast.makeText(baseContext, "Paused", Toast.LENGTH_SHORT).show()
            } else {
                rec.resume()
                Toast.makeText(baseContext, "Resumed", Toast.LENGTH_SHORT).show()
            }
            playing = !playing
        }
    }

    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return

        viewBinding.videoCaptureButton.isEnabled = false

        val curRecording = recording
        if (curRecording != null) {
            // Stop the current recording session.
            curRecording.stop()
            recording = null
            viewBinding.playButton.visibility = View.INVISIBLE
            viewBinding.muteButton.visibility = View.VISIBLE
            playing = false
            return
        }

        viewBinding.muteButton.visibility = View.INVISIBLE

        // create and start a new recording session
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()
        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(this@MainActivity,
                        Manifest.permission.RECORD_AUDIO) ==
                    PermissionChecker.PERMISSION_GRANTED)
                {
                    if (audioEnabled)
                        withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        viewBinding.videoCaptureButton.apply {
                            text = getString(R.string.stop_capture)
                            isEnabled = true
                        }
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val msg = "Video capture succeeded: " + "${recordEvent.outputResults.outputUri}"
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                            Log.d(TAG, msg)
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(TAG, "Video capture ends with error: " + "${recordEvent.error}")
                        }
                        viewBinding.videoCaptureButton.apply {
                            text = getString(R.string.start_capture)
                            isEnabled = true
                        }
                    }
                }
            }

        viewBinding.playButton.visibility = View.VISIBLE
        playing = true
    }

    @SuppressLint("WrongConstant")
    private fun startCamera() {
        Log.i(TAG, "START CAMERA")

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .apply {
                    if (currUseCase == CameraUseCase.PREVIEW_CAPTURE &&
                        targetRotation != TARGET_ROTATION_UNINITIALIZED) {
                        setTargetRotation(targetRotation)
                    }
                }
                .build()
                .also {
                    viewBinding.viewFinder.scaleType = scaleType
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            when (currUseCase) {
                CameraUseCase.PREVIEW_VIDEO -> {
                    val recorder = Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(videoQuality))
                        .build()
                    videoCapture = VideoCapture.withOutput(recorder)
                } else -> {
                    imageCapture = ImageCapture.Builder()
                        .setCaptureMode(captureMode)
                        .setFlashMode(flashMode)
                        .apply {
                            if (jpegQuality != JPEG_QUALITY_UNINITIALIZED)
                                setJpegQuality(jpegQuality)
                            if (targetRotation != TARGET_ROTATION_UNINITIALIZED)
                                setTargetRotation(targetRotation)
                        }
                        .build()
                }
            }

            try {
                cameraProvider.unbindAll()
                // NOTE(davide): Does anyone know how to pass the array of UseCase directly to
                // avoid the if? It keeps complaining about impossible casts...
                val camera = if (currUseCase == CameraUseCase.PREVIEW_VIDEO)
                    cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture)
                else
                    cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)

                currCamInfo = camera.cameraInfo
                currCamControl = camera.cameraControl

                // NOTE(davide): Apply settings requiring a CameraControl instance
                Log.d("Exposure", "index is $exposureCompensationIndex")
                if (camera.cameraInfo.exposureState.exposureCompensationIndex != exposureCompensationIndex) {
                    camera.cameraControl.setExposureCompensationIndex(exposureCompensationIndex)
                }
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private val scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val camControl = currCamControl
            val state = currCamInfo?.zoomState?.value
            if (camControl != null && state != null) {
                val zoomRatio = state.zoomRatio*detector.scaleFactor
                if (zoomRatio >= state.minZoomRatio && zoomRatio <= state.maxZoomRatio) {
                    viewBinding.zoomRatioText.text = "${Math.round(state.linearZoom*100)}%"
                    camControl.setZoomRatio(zoomRatio)
                }
            }
            return true
        }

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            scaling = true
            viewBinding.zoomRatioText.visibility = View.VISIBLE
            return super.onScaleBegin(detector)
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            scaling = false
            viewBinding.zoomRatioText.visibility = View.INVISIBLE
            super.onScaleEnd(detector)
        }
    }

    private val commonListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            //Log.d("Gesture", "Single tap $e")

            val camControl = currCamControl
            if (camControl != null) {
                val pointFactory = viewBinding.viewFinder.meteringPointFactory
                val p1 = pointFactory.createPoint(e.x, e.y)
                //val p2 = pointFactory.createPoint(e.x)
                val action = FocusMeteringAction.Builder(p1)
                    .setAutoCancelDuration(2, TimeUnit.SECONDS)
                    .build()

                // TODO(davide): Draw a circle to let the user know
                camControl.startFocusAndMetering(action)
            }
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            //Log.d("Gesture", "Double tap")

            val camControl = currCamControl
            val camInfo = currCamInfo
            if (flashMode != ImageCapture.FLASH_MODE_OFF && camControl != null && camInfo != null) {
                val newState = (camInfo.torchState.value == TorchState.OFF)
                camControl.enableTorch(newState)
            }
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            Log.d("Gesture", "long press $e")
        }

        override fun onFling(
            start: MotionEvent,
            end: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            // TODO(davide): Ignore diagonal swipes

            //Log.d("Gesture", "Horizontal swipe start=(${e1.x}, ${e1.y}) end=(${e2.x}, ${e2.y}) vX=$velocityX Vy=$velocityY")
            val diffX = end.x - start.x
            val diffY = end.y - start.y
            if (abs(diffX) >= abs(diffY)) {
                if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX < 0) {
                        Log.d("Gesture", "Left swipe")

                        // TODO(davide): Start gallery activity
                    } else {
                        Log.d("Gesture", "Right swipe")

                        launchSetting()
                    }
                }
            } else {
                Log.d("Gesture", "Vertical swipe")
                // TODO(davide): Increase/decrease exposure index?
            }

            return true
        }
    }
}
