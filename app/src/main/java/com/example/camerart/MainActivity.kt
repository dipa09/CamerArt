package com.example.camerart

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.MediaStore
import android.util.Log
import android.view.*
import android.webkit.MimeTypeMap
import android.widget.SimpleAdapter.ViewBinder
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.view.CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.view.GestureDetectorCompat
import androidx.preference.PreferenceManager
import com.example.camerart.databinding.ActivityMainBinding
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "CamerArt"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
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

        private const val ON_FIRST_RUN = "onfirstrun"

        const val MODE_CAPTURE = 0
        const val MODE_VIDEO = 1
        const val MODE_MULTI_CAMERA = 3
        const val MODE_QRCODE_SCANNER = 4

        const val FADING_MESSAGE_DEFAULT_DELAY = 1
        const val FOCUS_AUTO_CANCEL_DEFAULT_DURATION: Long = 3
    }
    enum class SupportedQuality { NONE, SD, HD, FHD, UHD, COUNT }

    enum class Mime(private val mime: String) {
        JPEG(MIME_TYPE_JPEG),
        PNG(MIME_TYPE_PNG),
        WEBP(MIME_TYPE_WEBP),

        COUNT("");

        override fun toString(): String { return mime }
    }

    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var barcodeScanner: BarcodeScanner

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

    private var currMode: Int = MODE_CAPTURE

    // Capture
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var captureMode: Int = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
    private var flashMode: Int = ImageCapture.FLASH_MODE_AUTO
    private var jpegQuality: Int = JPEG_QUALITY_UNINITIALIZED
    private var targetRotation: Int = TARGET_ROTATION_UNINITIALIZED
    private var focusing: Boolean = false
    private var extensionMode: Int = ExtensionMode.NONE
    private var meteringMode: Int = (FocusMeteringAction.FLAG_AF or
                                     FocusMeteringAction.FLAG_AE or
                                     FocusMeteringAction.FLAG_AWB)
    private var autoCancelDuration = FOCUS_AUTO_CANCEL_DEFAULT_DURATION
    private var showLumus = false

    // Video
    private var audioEnabled: Boolean = true
    private var videoQuality: Quality = Quality.HIGHEST
    private var playing: Boolean = false
    private var videoDuration: Int = 0
    private var showVideoStats: Boolean = false

    // Preview
    private var scaleType: PreviewView.ScaleType = PreviewView.ScaleType.FIT_CENTER

    private var supportedMimes = IntArray(1){Mime.JPEG.ordinal}
    private var requestedFormat: Mime = Mime.JPEG
    private var exposureCompensationIndex: Int = 0
    private var delayBeforeActionSeconds: Int = 0

    private lateinit var cameraFeatures: CameraFeatures

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value)
                    permissionGranted = false
            }

            if (!permissionGranted) {
                Toast.makeText(baseContext, "Permission request denied", Toast.LENGTH_SHORT).show()
            } else {
                initialize()
            }
        }

    private fun requestPermissions() {
        requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted(): Boolean {
        for (perm in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(baseContext, perm) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onResume() {
        super.onResume()

        if (loadPreferences(false)) {
            Log.d(TAG, "RESTART CAMERA")
            startCamera()
        }
        Log.d(TAG, "ON RESUME ENDED")
    }

    private fun firstRunCheck() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean(ON_FIRST_RUN, true)) {
            prefs.edit().putBoolean(ON_FIRST_RUN, false).apply()

            Thread {
                if (!deviceHasBeenTested()) {
                    runOnUiThread { infoDialog(this) }
                }
            }.start()
        }
    }

    private fun initialize() {
        firstRunCheck()
        getAvailableMimes()
        loadPreferences(true)
        startCamera()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "ON CREATE START")

        //dumpCameraFeatures(packageManager)
        cameraFeatures = initCameraFeatures(packageManager)

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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        commonDetector = GestureDetectorCompat(viewBinding.viewFinder.context, commonListener)
        scaleDetector = ScaleGestureDetector(viewBinding.viewFinder.context, scaleListener)

        if (allPermissionsGranted()) {
            initialize()
        } else {
            requestPermissions()
        }

        viewBinding.imageCaptureButton.setOnClickListener { takePhoto() }
        viewBinding.videoCaptureButton.setOnClickListener { captureVideo() }
        viewBinding.muteButton.setOnClickListener { toggleAudio() }
        viewBinding.settingsButton.setOnClickListener { launchSetting() }
        viewBinding.playButton.setOnClickListener { controlVideoRecording() }
        if (cameraFeatures.hasFront) {
            viewBinding.cameraButton.setOnClickListener { toggleCamera() }
        } else {
            flashMode = ImageCapture.FLASH_MODE_OFF
            viewBinding.cameraButton.isEnabled = false
            viewBinding.cameraButton.visibility = View.INVISIBLE
        }

        viewBinding.viewFinder.setOnTouchListener { _, motionEvent ->
            scaleDetector.onTouchEvent(motionEvent)
            if (!scaling)
                commonDetector.onTouchEvent(motionEvent)

            return@setOnTouchListener true
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        Log.d(TAG, "ON CREATE END")
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
            intent.putExtra("supportedQualities", querySupportedVideoQualities(camInfo))
            intent.putExtra("exposureState", exposureStateToBundle(camInfo.exposureState))
        }
        intent.putExtra("supportedImageFormats", supportedMimes)
        intent.putExtra("features", cameraFeaturesToBundle(cameraFeatures))

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

    private fun toggleMode(prefValue: Boolean, newState: Int, changeCount: Int): Int {
        val newMode = if (prefValue)
            newState
        else
            currMode

        var newChangeCount = changeCount
        if (newMode != currMode) {
            currMode = newMode
            ++newChangeCount
        }

        return newChangeCount
    }

    // NOTE(davide): For some reason, sometimes you need to delete app's data if you edit
    // a previous preference from root_preferences.xml or arrays.xml, otherwise you get
    // a random exception.
    private fun loadPreferences(onCreate: Boolean): Boolean {
        var changeCount = 0

        val sharedPreference = PreferenceManager.getDefaultSharedPreferences(this)
        var newCaptureMode: Int = captureMode
        for (pref in sharedPreference.all.iterator()) {
            //Log.i(TAG, "preference ${pref.key}, ${pref.value}")

            when (pref.key) {
                "pref_flash" -> {
                    val newFlashMode = when(pref.value) {
                        "on" -> ImageCapture.FLASH_MODE_ON
                        "off" -> ImageCapture.FLASH_MODE_OFF
                        else -> ImageCapture.FLASH_MODE_AUTO
                    }

                    if (newFlashMode != flashMode) {
                        flashMode = newFlashMode
                        ++changeCount
                    }
                }

                "pref_capture" -> {
                    newCaptureMode = captureModeFromName(pref.value as String, currCamInfo)
                    if (newCaptureMode != captureMode) {
                        captureMode = newCaptureMode
                        ++changeCount
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
                    val newJpegQuality = pref.value as Int

                    if (newJpegQuality != jpegQuality) {
                        jpegQuality = newJpegQuality
                        ++changeCount
                    }
                }

                "pref_rotation" -> {
                    val newTargetRotation = when (pref.value) {
                        "0" -> Surface.ROTATION_0
                        "90" -> Surface.ROTATION_90
                        "180" -> Surface.ROTATION_180
                        "270" -> Surface.ROTATION_270
                        else -> TARGET_ROTATION_UNINITIALIZED
                    }

                    if (newTargetRotation != targetRotation) {
                        targetRotation = newTargetRotation
                        ++changeCount
                    }
                }

                "pref_extension" -> {
                    val newExtensionMode = extensionFromName(pref.value as String)

                    if (newExtensionMode != extensionMode) {
                        extensionMode = newExtensionMode
                        ++changeCount
                    }
                }

                "pref_scale" -> {
                    val newScaleType = when (pref.value) {
                        "center" -> PreviewView.ScaleType.FIT_CENTER
                        "start" -> PreviewView.ScaleType.FIT_START
                        "end" -> PreviewView.ScaleType.FIT_END
                        else -> scaleType
                    }

                    if (newScaleType != scaleType) {
                        scaleType = newScaleType
                        ++changeCount
                    }
                }

                "pref_metering_mode" -> {
                    //Log.d("YYY", "${pref.value}")
                    try {
                        var newMeteringMode = 0
                        for (meterName in pref.value as HashSet<String>) {
                            newMeteringMode = newMeteringMode or meteringModeFromName(meterName)
                        }

                        if (newMeteringMode != meteringMode) {
                            meteringMode = newMeteringMode
                            if (!onCreate)
                                fadingMessage(describeMeteringMode(meteringMode), 2)
                        }
                    } catch (_: Exception) { }
                }

                "pref_auto_cancel_duration" -> {
                    try {
                        autoCancelDuration = (pref.value as String).toLong()
                    } catch (_: NumberFormatException) { }
                }

                "pref_lumus" -> {
                    val newShowLumus = pref.value as Boolean

                    if (newShowLumus != showLumus) {
                        showLumus = newShowLumus
                        currMode = MODE_CAPTURE
                        ++changeCount
                    }

                    if (showLumus)
                        viewBinding.statsText.visibility = View.VISIBLE
                    else
                        viewBinding.statsText.visibility = View.INVISIBLE
                }

                // TODO(davide): Temporary UI
                "pref_use_video_temp" -> {
                    changeCount = toggleMode(pref.value as Boolean, MODE_VIDEO, changeCount)
                }

                "pref_video_quality" -> {
                    val newVideoQuality = videoQualityFromName(pref.value as String)
                    if (newVideoQuality != videoQuality) {
                        videoQuality = newVideoQuality
                        ++changeCount
                    }
                }

                "pref_video_duration" -> {
                    var duration = pref.value as String
                    var factor = 1
                    if (!duration.last().isDigit()) {
                        factor = when (duration.last().lowercaseChar()) {
                            'm' -> 60
                            'h' -> 60*60
                            else -> 1
                        }
                        duration = duration.substring(0, duration.length - 1)
                    }

                    videoDuration = try {
                        duration.toInt()*factor
                    } catch (exc: NumberFormatException) {
                        0
                    }
                }

                "pref_show_video_stats" -> {
                    showVideoStats = (pref.value as Boolean)
                }

                "pref_exposure" -> {
                    exposureCompensationIndex = pref.value as Int
                }

                "pref_countdown" -> {
                    delayBeforeActionSeconds = pref.value as Int
                }

                "pref_multi_camera" -> {
                    changeCount = toggleMode(pref.value as Boolean, MODE_MULTI_CAMERA, changeCount)
                }

                "pref_qrcode" -> {
                    changeCount = toggleMode(pref.value as Boolean, MODE_QRCODE_SCANNER, changeCount)
                }
            }
        }

        // NOTE(davide): Change JPEG quality unless the user changed the capture mode
        if (newCaptureMode != captureMode) {
            jpegQuality = JPEG_QUALITY_UNINITIALIZED
            captureMode = newCaptureMode
            ++changeCount
        }

        return changeCount > 0
    }

    private fun applySettingsToCurrentCamera(camInfo: CameraInfo, camControl: CameraControl) {
        if (camInfo.exposureState.exposureCompensationIndex != exposureCompensationIndex) {
            camControl.setExposureCompensationIndex(exposureCompensationIndex)
        }

        currCamInfo = camInfo
        currCamControl = camControl
    }

    // TODO(davide): Add more metadata. Location, producer, ...
    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
        if (requestedFormat != Mime.JPEG && extensionMode == ExtensionMode.NONE) {
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

            countdown(delayBeforeActionSeconds)

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

    private fun stopRecording(): Boolean {
        var stopped = false
        val rec = recording
        if (rec != null) {
            rec.stop()
            recording = null
            viewBinding.playButton.visibility = View.INVISIBLE
            viewBinding.muteButton.visibility = View.VISIBLE
            viewBinding.statsText.apply {
                text = ""
                visibility = View.INVISIBLE
            }
            playing = false

            stopped = true
        }

        return stopped
    }

    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return

        viewBinding.videoCaptureButton.isEnabled = false
        if (stopRecording())
            return

        // NOTE(davide): The docs say that Recording.mute can be used to mute/unmute a running
        // recording, but the method can't be resolved...
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

        var recDurationNanos = Long.MAX_VALUE

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
                    if (videoDuration > 0)
                        recDurationNanos = videoDuration.toLong()*1_000_000_000
                    if (showVideoStats)
                        viewBinding.statsText.visibility = View.VISIBLE
                    countdown(delayBeforeActionSeconds)
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

                    is VideoRecordEvent.Status -> {
                        // TODO(davide): Display Location info as well?
                        val stats = recordEvent.recordingStats
                        if (stats.recordedDurationNanos < recDurationNanos) {
                            if (showVideoStats) {
                                val audioDesc = when (stats.audioStats.audioState) {
                                    AudioStats.AUDIO_STATE_ACTIVE -> "On"
                                    AudioStats.AUDIO_STATE_DISABLED -> "Off"
                                    AudioStats.AUDIO_STATE_SOURCE_SILENCED -> "Silenced"
                                    else -> "Bad"
                                }

                                viewBinding.statsText.text =
                                    "Time: ${humanizeTime(stats.recordedDurationNanos)}\n" +
                                            "Size: ${humanizeSize(stats.numBytesRecorded)}\n" +
                                            "Audio: $audioDesc"
                            }
                        } else {
                            stopRecording()
                        }
                    }
                }
            }

        viewBinding.playButton.visibility = View.VISIBLE
        playing = true
    }

    private fun buildSelector(): CameraSelector {
        return CameraSelector.Builder().requireLensFacing(lensFacing).build()
    }

    @SuppressLint("WrongConstant")
    private fun buildPreview(): Preview {
        val preview = Preview.Builder()
            .apply {
                if (targetRotation != TARGET_ROTATION_UNINITIALIZED || currMode == MODE_VIDEO)
                    setTargetRotation(targetRotation)
            }.build()
            .also {
                viewBinding.viewFinder.scaleType = scaleType
                it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
            }
        return preview
    }

    @SuppressLint("WrongConstant")
    private fun buildImageCapture(): ImageCapture {
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(captureMode)
            .setFlashMode(flashMode)
            .apply {
                if (jpegQuality != JPEG_QUALITY_UNINITIALIZED)
                    setJpegQuality(jpegQuality)
                if (targetRotation != TARGET_ROTATION_UNINITIALIZED)
                    setTargetRotation(targetRotation)
            }
            .build()
        return imageCapture
    }

    private fun startNormalCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            try {
                cameraProvider.unbindAll()

                val selector = buildSelector()
                val preview = buildPreview()
                val camera = if (currMode == MODE_VIDEO) {
                    val recorder = Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(videoQuality))
                        .build()
                    videoCapture = VideoCapture.withOutput(recorder)
                    cameraProvider.bindToLifecycle(this, selector, preview, videoCapture)
                } else {
                    assert(currMode == MODE_CAPTURE)
                    imageCapture = buildImageCapture()
                    if (showLumus) {
                        val analysis = ImageAnalysis.Builder()
                            //.setOutputImageFormat(OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        analysis.setAnalyzer(cameraExecutor, ImageAnalysis.Analyzer { image ->
                            val result = evalAvgLuminosityAndRotation(image)
                            image.close()
                            val lumStr = String.format("%.2f", result.luminosity)
                            //Log.d(TAG, "rot ${result.rotation}, lum ${result.luminosity}")
                            runOnUiThread {
                                viewBinding.statsText.text = "Lum: $lumStr\nRot: ${result.rotation}°"
                            }
                        })
                        cameraProvider.bindToLifecycle(this, selector, preview, imageCapture, analysis)
                    } else {
                        cameraProvider.bindToLifecycle(this, selector, preview, imageCapture)
                    }
                }
                applySettingsToCurrentCamera(camera.cameraInfo, camera.cameraControl)
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startMultiCamera() {
        // TODO(davide): Multi camera support won't be available until CameraX 1.3...
        startNormalCamera()
    }

    private fun startCameraWithExtensions(): Boolean {
        var result = true

        val cameraProviderFuture = ProcessCameraProvider.getInstance(applicationContext)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            // TODO(davide): This throws a NoClassDefFoundError, which can't be caught...
            // How can we detect when an extension is available then?
            val extensionManagerFuture = ExtensionsManager.getInstanceAsync(applicationContext, cameraProvider)
            //
            extensionManagerFuture.addListener({
                val extensionManager = extensionManagerFuture.get()
                val selector = buildSelector()
                if (extensionManager.isExtensionAvailable(selector, extensionMode)) {
                    try {
                        cameraProvider.unbindAll()

                        val selectorX = extensionManager.getExtensionEnabledCameraSelector(selector, extensionMode)
                        val preview = buildPreview()
                        imageCapture = buildImageCapture()
                        val camera = cameraProvider.bindToLifecycle(this, selectorX, preview, imageCapture)
                        applySettingsToCurrentCamera(camera.cameraInfo, camera.cameraControl)
                    } catch (e: Exception) {
                        Log.e(TAG, "Use case binding failed", e)
                        result = false
                    }
                }
                                               }, ContextCompat.getMainExecutor(this))
                                         }, ContextCompat.getMainExecutor(this))

        return result
    }

    private fun startQRScanner() {
        val cameraController = LifecycleCameraController(baseContext)
        val preview = viewBinding.viewFinder

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        barcodeScanner = BarcodeScanning.getClient(options)

        cameraController.setImageAnalysisAnalyzer(
            ContextCompat.getMainExecutor(this),
            MlKitAnalyzer(
                listOf(barcodeScanner),
                COORDINATE_SYSTEM_VIEW_REFERENCED,
                ContextCompat.getMainExecutor(this)
            ) { result: MlKitAnalyzer.Result? ->
                val barcodes = result?.getValue(barcodeScanner)
                if (barcodes == null || barcodes.size == 0 || barcodes.first() == null) {
                    preview.overlay.clear()
                    preview.setOnTouchListener { _, _ -> false } // nop
                    return@MlKitAnalyzer
                }

                val qrCode = QrCode(barcodes[0])
                val qrCodeDrawable = QrCodeDrawable(qrCode)

                // TODO(davide): This overwrites the gesture detector...
                preview.setOnTouchListener(qrCode.touchCallback)
                preview.overlay.clear()
                preview.overlay.add(qrCodeDrawable)
            }
        )

        cameraController.bindToLifecycle(this)
        preview.controller = cameraController
    }

    private fun startCamera() {
        Log.d(TAG, "START CAMERA")

        //Log.d(TAG, "Current mode is $currMode")
        if (currMode == MODE_CAPTURE || currMode == MODE_VIDEO) {
            if (currMode == MODE_CAPTURE && extensionMode != ExtensionMode.NONE) {
                startCameraWithExtensions()
            } else {
                startNormalCamera()
            }
        } else if (currMode == MODE_MULTI_CAMERA) {
            startMultiCamera()
        } else if (currMode == MODE_QRCODE_SCANNER) {
            startQRScanner()
        } else {
            assert(false)
        }

        Log.d(TAG, "START CAMERA ENDED")
    }

    private fun enableInfoTextView(mesg: String) {
        viewBinding.infoText.apply {
            text = mesg
            visibility = View.VISIBLE
        }
    }

    private fun disableInfoTextView() {
        viewBinding.infoText.apply {
            text = ""
            visibility = View.INVISIBLE
        }
    }
    private fun fadingMessage(mesg: String, seconds: Int = FADING_MESSAGE_DEFAULT_DELAY) {
        enableInfoTextView(mesg)
        object : CountDownTimer(seconds.toLong()*1000, 1000) {
            override fun onFinish() { disableInfoTextView() }
            override fun onTick(p0: Long) { }
        }.start()
    }

    private fun countdown(seconds: Int) {
        if (seconds > 0) {
            enableInfoTextView("$seconds")

            object : CountDownTimer(delayBeforeActionSeconds.toLong()*1000, 1000) {
                override fun onTick(reamaining_ms: Long) {
                    viewBinding.infoText.text = "${reamaining_ms / 1000}"
                }

                override fun onFinish() { disableInfoTextView() }
            }.start()
        }
    }

    private val scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val camControl = currCamControl
            val state = currCamInfo?.zoomState?.value
            if (camControl != null && state != null) {
                val zoomRatio = state.zoomRatio*detector.scaleFactor
                if (zoomRatio >= state.minZoomRatio && zoomRatio <= state.maxZoomRatio) {
                    viewBinding.infoText.text = "${Math.round(state.linearZoom*100)}%"
                    camControl.setZoomRatio(zoomRatio)
                }
            }
            return true
        }

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            scaling = true
            viewBinding.infoText.visibility = View.VISIBLE
            return super.onScaleBegin(detector)
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            scaling = false
            viewBinding.infoText.visibility = View.INVISIBLE
            super.onScaleEnd(detector)
        }
    }

    private fun finishFocus() {
        viewBinding.focusRing.visibility = View.INVISIBLE
        focusing = false
    }

    private fun focus(posX: Float, posY: Float) {
        val camControl = currCamControl
        if (camControl != null) {
            if (focusing) {
                val result = camControl.cancelFocusAndMetering()
                result.addListener({
                    finishFocus()
                }, ContextCompat.getMainExecutor(baseContext))
            } else {
                val pointFactory = viewBinding.viewFinder.meteringPointFactory
                val p1 = pointFactory.createPoint(posX, posY)
                val action = FocusMeteringAction.Builder(p1, meteringMode)
                    .setAutoCancelDuration(autoCancelDuration, TimeUnit.SECONDS)
                    .build()

                viewBinding.focusRing.apply {
                    x = posX - width / 2
                    y = posY - height / 2
                    visibility = View.VISIBLE
                }
                focusing = true

                val result = camControl.startFocusAndMetering(action)
                result.addListener({
                    //if (!result.get().isFocusSuccessful)
                    //    Toast.makeText(baseContext, "Unable to focus", Toast.LENGTH_SHORT).show()
                    finishFocus()
                }, ContextCompat.getMainExecutor(baseContext))
            }
        }
    }

    private val commonListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            focus(e.x, e.y)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
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
