package com.example.camerart

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaActionSound
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.*
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
import androidx.camera.view.CameraController.IMAGE_ANALYSIS
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
                    add(Manifest.permission.READ_EXTERNAL_STORAGE)
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
        const val MIN_VERSION_FOR_WEBP = Build.VERSION_CODES.R

        private const val ON_FIRST_RUN = "onfirstrun"

        const val MODE_CAPTURE = 0
        const val MODE_VIDEO = 1
        const val MODE_MULTI_CAMERA = 3
        const val MODE_QRCODE_SCANNER = 4

        const val FADING_MESSAGE_DEFAULT_DELAY = 1
        const val FOCUS_AUTO_CANCEL_DEFAULT_DURATION: Long = 3
    }

    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService

    private var cameraController: LifecycleCameraController? = null
    private var barcodeScanner: BarcodeScanner? = null
    private var qrCode: QrCode? = null

    private lateinit var soundManager: SoundManager

    // Gesture
    private lateinit var  commonDetector: GestureDetectorCompat
    private lateinit var scaleDetector: ScaleGestureDetector
    private var scaling: Boolean = false
    //

    private var isBeefy: Boolean = false

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var currCamInfo: CameraInfo? = null
    private var currCamControl: CameraControl? = null

    private var currMode: Int = MODE_CAPTURE
    private var prevMode: Int = MODE_CAPTURE

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
    private var filterType: Int = FILTER_TYPE_NONE

    // Video
    private var audioEnabled: Boolean = true
    private var videoQuality: Quality = Quality.HIGHEST
    private var playing: Boolean = false
    private var videoDuration: Int = 0
    private var showVideoStats: Boolean = false

    // Preview
    private var scaleType: PreviewView.ScaleType = PreviewView.ScaleType.FILL_CENTER

    private var requestedFormat: String = MIME_TYPE_JPEG
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

    private fun requestPermissions() { requestPermissionLauncher.launch(REQUIRED_PERMISSIONS) }

    private fun allPermissionsGranted(): Boolean {
        for (perm in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(baseContext, perm) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    // NOTE(davide): Call startCamera() after this
    private fun setCameraMode(newMode: Int) {
        assert(newMode != currMode)

        if (currMode != MODE_QRCODE_SCANNER)
            prevMode = currMode
        currMode = newMode

        viewBinding.photoButton.isEnabled = (currMode != MODE_QRCODE_SCANNER)
    }

    override fun onPause() {
        super.onPause()

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (currMode != MODE_QRCODE_SCANNER)
            prefs.edit().putInt("lastCameraMode", currMode).apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        barcodeScanner?.close()
    }

    override fun onResume() {
        super.onResume()
        if (loadPreferences(false))
            startCamera()
    }

    private fun initialize() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean(ON_FIRST_RUN, true)) {
            isBeefy = isBeefyDevice()
            val noisy = deviceIsNoisy(applicationContext)
            with (prefs.edit()) {
                putBoolean(ON_FIRST_RUN, false)
                putBoolean("isBeefy", isBeefy)
                putInt(resources.getString(R.string.jpeg_quality_key), JPEG_QUALITY_LATENCY)
                putBoolean(resources.getString(R.string.action_sound_key), noisy)

                apply()
            }

            if (noisy)
                soundManager.enable()

            Thread {
                if (!deviceHasBeenTested()) {
                    runOnUiThread { infoDialog(this) }
                }
            }.start()
        } else {
            isBeefy = prefs.getBoolean("isBeefy", false)
            currMode = prefs.getInt("lastCameraMode", MODE_CAPTURE)
            loadPreferences(prefs, true)
        }

        if (currMode == MODE_VIDEO)
            enableVideoUI()
        else
            enableCaptureUI()

        startCamera()
    }

    private fun enableCaptureUI() {
        viewBinding.btnVideo.setTextColor(Color.WHITE)
        viewBinding.btnFoto.setTextColor(Color.parseColor("#58A0C4"))
        viewBinding.photoButton.setBackgroundResource(R.drawable.ic_shutter)
        viewBinding.muteButton.visibility = View.INVISIBLE
        viewBinding.playButton.visibility = View.INVISIBLE
    }

    private fun enableVideoUI() {
        viewBinding.btnFoto.setTextColor(Color.WHITE)
        viewBinding.btnVideo.setTextColor(Color.parseColor("#58A0C4"))
        viewBinding.photoButton.setBackgroundResource((R.drawable.baseline_play_circle_24))
        viewBinding.muteButton.visibility = View.VISIBLE
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraFeatures = CameraFeatures(getCameraProvider(), packageManager)
        soundManager = SoundManager()

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

        if (allPermissionsGranted())
            initialize()
        else
            requestPermissions()

        viewBinding.photoButton.setOnClickListener { takePhotoOrVideo() }
        viewBinding.muteButton.setOnClickListener { toggleAudio() }
        viewBinding.settingsButton.setOnClickListener { launchSetting() }
        viewBinding.playButton.setOnClickListener { controlVideoRecording() }
        viewBinding.galleryButton.setOnClickListener { launchGallery() }

        viewBinding.btnFoto.setOnClickListener {
            if (currMode != MODE_CAPTURE) {
                enableCaptureUI()
                setCameraMode(MODE_CAPTURE)
                startCamera()
            }
        }

        viewBinding.btnVideo.setOnClickListener {
            if (currMode != MODE_VIDEO) {
                enableVideoUI()
                setCameraMode(MODE_VIDEO)
                startCamera()
            }
        }

        if (cameraFeatures.hasFront) {
            viewBinding.cameraButton.setOnClickListener { toggleCamera() }
        } else {
            flashMode = ImageCapture.FLASH_MODE_OFF
            viewBinding.cameraButton.isEnabled = false
            viewBinding.cameraButton.visibility = View.INVISIBLE
        }

        viewBinding.viewFinder.setOnTouchListener { view: View, motionEvent ->
            scaleDetector.onTouchEvent(motionEvent)
            if (!scaling)
                commonDetector.onTouchEvent(motionEvent)
            if (qrCode != null) {
                qrCode!!.touchCallback(view, motionEvent)
            }

            return@setOnTouchListener true
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun toggleAudio() {
        val rec = recording
        audioEnabled = !audioEnabled
        if (audioEnabled) viewBinding.muteButton.setBackgroundResource(R.drawable.baseline_mic_none_24)
        else viewBinding.muteButton.setBackgroundResource(R.drawable.baseline_mic_off_24)
        if(rec != null){
            rec.mute(!audioEnabled)
        }
    }

    private fun toggleCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        startCamera()
    }

    private fun launchSetting() {
        val intent = Intent(this, SettingActivity::class.java)

        val camInfo = currCamInfo
        var wantFront = false
        if (camInfo != null) {
            wantFront = (camInfo.lensFacing == CameraSelector.LENS_FACING_FRONT)
            intent.putExtra("exposureState", exposureStateToBundle(camInfo.exposureState))
        }
        intent.putExtra("features", cameraFeatures.toBundle(wantFront))
        intent.putExtra("isBeefy", isBeefy)
        intent.putExtra("cameraMode", currMode)

        this.startActivity(intent, ActivityOptions.makeSceneTransitionAnimation(this).toBundle())
    }

    private fun launchGallery() {
        val intent = Intent(this, GalleryActivity::class.java)

        this.startActivity(intent, ActivityOptions.makeSceneTransitionAnimation(this).toBundle())
    }

    private fun updateFlashMode(prefs: SharedPreferences): Int {
        var changed = 0
        val flashModeName = getStringPref(R.string.flash_key, prefs)
        if (flashModeName != null) {
            val newFlashMode = when (flashModeName) {
                resources.getString(R.string.flash_value_on) -> ImageCapture.FLASH_MODE_ON
                resources.getString(R.string.flash_value_off) -> ImageCapture.FLASH_MODE_OFF
                else -> ImageCapture.FLASH_MODE_AUTO
            }

            if (newFlashMode != flashMode) {
                flashMode = newFlashMode
                changed = 1
            }
        }

        return changed
    }

    private fun updateImageFormat(prefs: SharedPreferences) {
        val pref = getStringPref(R.string.image_fmt_key, prefs)
        if (pref != null && pref.isNotEmpty()) {
            requestedFormat = pref
        }
    }

    private fun updateTargetRotation(prefs: SharedPreferences): Int {
        var changed = 0

        val pref = getStringPref(R.string.rotation_key, prefs)
        if (pref != null) {
            val newRot = when (pref) {
                "0" -> Surface.ROTATION_0
                "90" -> Surface.ROTATION_90
                "180" -> Surface.ROTATION_180
                "270" -> Surface.ROTATION_270
                else -> TARGET_ROTATION_UNINITIALIZED
            }

            if (newRot != targetRotation) {
                targetRotation = newRot
                changed = 1
            }
        }

        return changed
    }

    private fun updateCaptureModeAndJPEGQuality(prefs: SharedPreferences): Int {
        var changed = 0

        // NOTE(davide): The capture mode takes precedence
        val newJpegQuality = getInt(R.string.jpeg_quality_key, prefs, 1)
        if (newJpegQuality != jpegQuality) {
            jpegQuality = newJpegQuality
            changed = 1
        }

        val prefCapture = getStringPref(R.string.capture_key, prefs)
        if (prefCapture != null) {
            var newCapMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            if (prefCapture == resources.getString(R.string.capture_value_quality))
                newCapMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY

            if (newCapMode != captureMode) {
                jpegQuality = JPEG_QUALITY_UNINITIALIZED
                captureMode = newCapMode
                changed = 1
            }
        }

        return changed
    }

    private fun updateExtensionMode(prefs: SharedPreferences): Int {
        var changed = 0

        val pref = getStringPref(R.string.extension_key, prefs)
        if (pref != null) {
            val newExtensionMode = extensionFromName(pref)
            if (newExtensionMode != extensionMode) {
                extensionMode = newExtensionMode
                changed = 1
            }
        }

        return changed
    }

    private fun updateScaling(prefs: SharedPreferences): Int {
        var changed = 0

        val pref = getStringPref(R.string.scaling_key, prefs)
        if (pref != null) {
            val newScaleType = when (pref) {
                resources.getString(R.string.scaling_value_center) -> PreviewView.ScaleType.FILL_CENTER
                resources.getString(R.string.scaling_value_start) -> PreviewView.ScaleType.FILL_START
                resources.getString(R.string.scaling_value_end) -> PreviewView.ScaleType.FILL_END
                else -> scaleType
            }

            if (newScaleType != scaleType) {
                scaleType = newScaleType
                changed = 1
            }
        }

        return changed
    }

    private fun updateMetering(prefs: SharedPreferences, onCreate: Boolean) {
        val pref = prefs.getStringSet(resources.getString(R.string.metering_mode_key), null)
        if (pref != null) {
            var newMeteringMode = 0
            for (meterName in pref) {
                newMeteringMode = newMeteringMode or meteringModeFromPreference(meterName)
            }

            if (newMeteringMode != meteringMode) {
                meteringMode = newMeteringMode
                if (!onCreate)
                    showFadingMessage(
                        viewBinding.infoText,
                        describeMeteringMode(meteringMode),
                        2
                    )
            }
        }
    }

    private fun updateAutoCancelDuration(prefs: SharedPreferences) {
        val pref = getStringPref(R.string.auto_cancel_duration_key, prefs)
        if (pref != null) {
            try {
                autoCancelDuration = pref.toLong()
            } catch (_: NumberFormatException) { }
        }
    }

    private fun updateLumus(prefs: SharedPreferences) {
        showLumus = getBool(R.string.lumus_key, prefs)
        if (showLumus)
            viewBinding.statsText.visibility = View.VISIBLE
        else
            viewBinding.statsText.visibility = View.INVISIBLE
    }

    private fun updateFilter(prefs: SharedPreferences) {
        val pref = getStringPref(R.string.filter_key, prefs)
        if (pref != null) {
            filterType = filterTypeFromPreference(pref)
            //Log.d(TAG, "filter is $filterType -- ${pref.value as String}")
        }
    }

    private fun updateVideoQuality(prefs: SharedPreferences): Int {
        var changed = 0

        val pref = getStringPref(R.string.video_quality_key, prefs)
        if (pref != null) {
            val newVideoQuality = videoQualityFromName(pref)
            if (newVideoQuality != videoQuality) {
                videoQuality = newVideoQuality
                changed = 1
            }
        }

        return changed
    }

    private fun updateVideoDuration(prefs: SharedPreferences) {
        val pref = getStringPref(R.string.video_duration_key, prefs)
        if (pref != null)
            videoDuration = stringToIntOr0(pref)
    }

    private fun getStringPref(stringID: Int, prefs: SharedPreferences): String? {
        return prefs.getString(resources.getString(stringID), "")
    }

    private fun getBool(stringID: Int, prefs: SharedPreferences, defaultValue: Boolean = false): Boolean {
        return prefs.getBoolean(resources.getString(stringID), defaultValue)
    }

    private fun getInt(stringID: Int, prefs: SharedPreferences, defaultValue: Int = 0): Int {
        return prefs.getInt(resources.getString(stringID), defaultValue)
    }


    private fun meteringModeFromPreference(name: CharSequence): Int {
        return when (name) {
            resources.getString(R.string.metering_mode_value_auto_focus) ->
                FocusMeteringAction.FLAG_AF
            resources.getString(R.string.metering_mode_value_auto_exposure) ->
                FocusMeteringAction.FLAG_AE
            resources.getString(R.string.metering_mode_value_auto_white_balance) ->
                FocusMeteringAction.FLAG_AWB
            else -> 0
        }
    }

    private fun filterTypeFromPreference(filterValue: String): Int {
        return when (filterValue) {
            resources.getString(R.string.filter_value_nogreen) -> FILTER_TYPE_NO_GREEN
            resources.getString(R.string.filter_value_gray) -> FILTER_TYPE_GREY
            resources.getString(R.string.filter_value_sepia) -> FILTER_TYPE_SEPIA
            resources.getString(R.string.filter_value_sketch) -> FILTER_TYPE_SKETCH
            resources.getString(R.string.filter_value_negative) -> FILTER_TYPE_NEGATIVE
            resources.getString(R.string.filter_value_aqua) -> FILTER_TYPE_AQUA
            resources.getString(R.string.filter_value_faded) -> FILTER_TYPE_FADED
            resources.getString(R.string.filter_value_blur) -> FILTER_TYPE_BLUR
            resources.getString(R.string.filter_value_edge) -> FILTER_TYPE_EDGE
            resources.getString(R.string.filter_value_emboss) -> FILTER_TYPE_EMBOSS
            resources.getString(R.string.filter_value_sharpen_light) -> FILTER_TYPE_SHARPEN_LIGHT
            resources.getString(R.string.filter_value_sharpen_hard) -> FILTER_TYPE_SHARPEN_HARD
            else -> FILTER_TYPE_NONE
        }
    }

    // NOTE(davide): For some reason, sometimes you need to delete app's data if you edit
    // a previous preference from root_preferences.xml or arrays.xml, otherwise you get
    // a random exception.
    private fun loadPreferences(prefs: SharedPreferences, onCreate: Boolean): Boolean {
        var changeCount = 0

        // Capture preferences
        changeCount += updateFlashMode(prefs)
        changeCount += updateCaptureModeAndJPEGQuality(prefs)
        changeCount += updateTargetRotation(prefs)
        changeCount += updateExtensionMode(prefs)
        updateImageFormat(prefs)
        updateMetering(prefs, onCreate)
        updateAutoCancelDuration(prefs)
        updateLumus(prefs)
        updateFilter(prefs)

        // Video preferences
        changeCount += updateVideoQuality(prefs)
        updateVideoDuration(prefs)
        showVideoStats = getBool(R.string.video_stats_key, prefs)

        // Preview preferences
        changeCount += updateScaling(prefs)

        // Misc
        exposureCompensationIndex = getInt(R.string.exposure_key, prefs)
        delayBeforeActionSeconds = getInt(R.string.countdown_key, prefs)
        if (getBool(R.string.action_sound_key, prefs))
            soundManager.enable()
        else
            soundManager.disable()

        if (getBool(R.string.qrcode_key, prefs)) {
            if (currMode != MODE_QRCODE_SCANNER) {
                setCameraMode(MODE_QRCODE_SCANNER)
                ++changeCount
            }
        } else if (currMode == MODE_QRCODE_SCANNER) {
            setCameraMode(prevMode)
            ++changeCount
        }

        return changeCount > 0
    }

    private fun loadPreferences(onCreate: Boolean): Boolean {
        val sharedPreference = PreferenceManager.getDefaultSharedPreferences(this)
        return loadPreferences(sharedPreference, onCreate)
    }

    private fun applySettingsToCurrentCamera(camInfo: CameraInfo, camControl: CameraControl) {
        if (camInfo.exposureState.exposureCompensationIndex != exposureCompensationIndex) {
            camControl.setExposureCompensationIndex(exposureCompensationIndex)
        }

        currCamInfo = camInfo
        currCamControl = camControl
    }

    private fun showToast(message: String) {
        Toast.makeText(baseContext, message, Toast.LENGTH_SHORT).show()
    }

    private fun showToast(stringID: Int) {
        showToast(resources.getString(stringID))
    }

    private fun showPhotoSavedAt(uri: Uri) {
        val msg = resources.getString(R.string.photo_saved_success) + " $uri"
        showToast(msg)

        Log.d(TAG, msg)
    }

    private fun showPhotoError(ex: Exception) {
        showToast(R.string.photo_error)
        Log.e(TAG, "Photo capture failed: ${ex.message}")
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        soundManager.prepare(MediaActionSound.SHUTTER_CLICK)
        showCountdown(viewBinding.infoText, delayBeforeActionSeconds)

        val contentValues = makeContentValues(
            SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()),
            requestedFormat)
        if (filterType == FILTER_TYPE_NONE && requestedFormat == MIME_TYPE_JPEG) {
            val outputOptions = ImageCapture.OutputFileOptions
                .Builder(contentResolver,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues)
                .build()

            soundManager.play()
            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val uri = output.savedUri
                        if (uri != null)
                            showPhotoSavedAt(uri)
                    }

                    override fun onError(ex: ImageCaptureException) { showPhotoError(ex) }
                }
            )
        } else {
            soundManager.play()
            imageCapture.takePicture(
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(imageProxy: ImageProxy) {
                        // NOTE(davide): Apparently there is no way to tell CameraX to NOT compress
                        // the image in JPEG.

                        var uri: Uri? = null
                        try {
                            val sourceBitmap = imageProxy.toBitmap()
                            val destBitmap = filterBitmap(sourceBitmap, filterType)

                            uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: throw IOException("No media store")

                            contentResolver.openOutputStream(uri)?.use {
                                //Log.d(TAG, "Start compressing")
                                destBitmap.compress(bitmapFormatFromMime(requestedFormat), jpegQuality, it)
                            } ?: throw IOException("Failed to open output stream")

                            showPhotoSavedAt(uri)
                        } catch (ex: Exception) {
                            uri?.let { orphanUri ->
                                contentResolver.delete(orphanUri, null, null)
                            }
                            //Log.d(TAG, "Failed to save image $ex")

                            showToast(R.string.photo_error)
                        }

                        super.onCaptureSuccess(imageProxy)
                    }

                    override fun onError(ex: ImageCaptureException) { showPhotoError(ex) }
                })
        }
    }

    private fun controlVideoRecording() {
        val rec = recording
        if (rec != null) {
            if (playing) {
                rec.pause()
                viewBinding.playButton.setBackgroundResource(R.drawable.baseline_play_arrow_24)
                //showToast(R.string.paused)
            } else {
                rec.resume()
                viewBinding.playButton.setBackgroundResource(R.drawable.baseline_pause_24)
                //showToast(R.string.resumed)
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
            viewBinding.photoButton.setBackgroundResource(R.drawable.baseline_play_circle_24)
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

        viewBinding.playButton.visibility = View.VISIBLE
        viewBinding.photoButton.isEnabled = false
        if (stopRecording())
            return

        var recDurationNanos = Long.MAX_VALUE

        recording = videoCapture.output
            .prepareRecording(this, buildVideoOptions(FILENAME_FORMAT, contentResolver))
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

                    soundManager.prepare(MediaActionSound.START_VIDEO_RECORDING)
                    showCountdown(viewBinding.infoText, delayBeforeActionSeconds)
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent -> handleRecordEvent(recordEvent, recDurationNanos) }

        viewBinding.photoButton.setBackgroundResource(R.drawable.baseline_stop_circle_24)
        viewBinding.muteButton.visibility = View.VISIBLE
        viewBinding.playButton.visibility = View.VISIBLE
        playing = true
    }

    private fun handleRecordEvent(event: VideoRecordEvent, recDurationNanos: Long) {
        when (event) {
            is VideoRecordEvent.Start -> {
                soundManager.play()
                viewBinding.photoButton.isEnabled = true
            }

            is VideoRecordEvent.Finalize -> {
                soundManager.playOnce(MediaActionSound.STOP_VIDEO_RECORDING)
                if (!event.hasError()) {
                    val msg = R.string.video_saved_success.toString() + "${event.outputResults.outputUri}"
                    showToast(msg)
                    Log.d(TAG, msg)
                } else {
                    recording?.close()
                    recording = null
                    Log.e(TAG, R.string.video_saved_failed.toString() + "${event.error}")
                }
                viewBinding.photoButton.apply {
                    isEnabled = true
                }
            }

            is VideoRecordEvent.Status -> {
                val stats = event.recordingStats
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
    private fun takePhotoOrVideo() {
        when (currMode) {
            MODE_CAPTURE -> takePhoto()
            MODE_VIDEO   -> captureVideo()
        }
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

                        analysis.setAnalyzer(cameraExecutor) { image ->
                            val result = evalAvgLuminosityAndRotation(image)
                            image.close()
                            val lumStr = String.format("%.2f", result.luminosity)
                            //Log.d(TAG, "rot ${result.rotation}, lum ${result.luminosity}")
                            runOnUiThread {
                                viewBinding.statsText.text =
                                    "Lum: $lumStr\nRot: ${result.rotation}°"
                            }
                        }

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
                } // NOTE(davide): For some reason these two lines can't be indented properly
                                               }, ContextCompat.getMainExecutor(this))
                                         }, ContextCompat.getMainExecutor(this))

        return result
    }

    private fun startQRScanner() {
        val viewFinder = viewBinding.viewFinder
        val controller = LifecycleCameraController(baseContext)

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = BarcodeScanning.getClient(options)

        controller.setImageAnalysisAnalyzer(
            ContextCompat.getMainExecutor(this),
            MlKitAnalyzer(
                listOf(scanner),
                COORDINATE_SYSTEM_VIEW_REFERENCED,
                ContextCompat.getMainExecutor(this)
            ) { result: MlKitAnalyzer.Result? ->
                val barcodes = result?.getValue(scanner)
                if (barcodes == null || barcodes.size == 0 || barcodes.first() == null) {
                    viewFinder.overlay.clear()
                    qrCode = null
                    return@MlKitAnalyzer
                }

                val currQrCode = QrCode(barcodes[0])
                qrCode = currQrCode
                viewFinder.overlay.clear()
                if (barcodeScanner != null)
                    viewFinder.overlay.add(QrCodeDrawable(currQrCode))
            }
        )

        try {
            controller.setEnabledUseCases(IMAGE_ANALYSIS)
            controller.bindToLifecycle(this)

            controller.isTapToFocusEnabled = false
            controller.isPinchToZoomEnabled = false
            controller.enableTorch(false)

            cameraController = controller
            viewFinder.controller = controller
            barcodeScanner = scanner
        } catch (ex: IllegalStateException) {
            Log.e(TAG, "unable to start qrcode sacnner: $ex")
        }
    }

    private fun getCameraProvider(): ProcessCameraProvider {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        val provider = providerFuture.get()
        return provider
    }

    private fun releaseCamera() {
        val provider = getCameraProvider()
        provider.unbindAll()

        currCamControl = null
        currCamInfo = null
    }

    private fun releaseQrCodeScanner() {
        val viewFinder = viewBinding.viewFinder
        viewFinder.overlay.clear()

        val controller = cameraController
        if (controller != null) {
            //showToast("Release QRCODE")

            controller.unbind()
            barcodeScanner?.close()

            viewFinder.controller = null
            cameraController = null
            barcodeScanner = null
            qrCode = null
        }

        viewFinder.overlay.clear()
    }

    private fun startCamera() {
        //Log.d(TAG, "START CAMERA")

        //Log.d("XX", "currMode is $currMode")
        if (currMode == MODE_QRCODE_SCANNER) {
            releaseCamera()
            startQRScanner()
        } else {
            releaseQrCodeScanner()
            if (currMode == MODE_VIDEO) {
                startNormalCamera()
            } else {
                assert(currMode == MODE_CAPTURE)
                if (extensionMode == ExtensionMode.NONE) {
                    startNormalCamera()
                } else {
                    startCameraWithExtensions()
                }
            }
        }

        //Log.d(TAG, "START CAMERA ENDED")
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
        soundManager.play()
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
                soundManager.prepare(MediaActionSound.FOCUS_COMPLETE)

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
                        //Log.d("Gesture", "Left swipe")

                        launchGallery()
                    } else {
                        //Log.d("Gesture", "Right swipe")

                        launchSetting()
                    }
                }
            } else {
                //Log.d("Gesture", "Vertical swipe")
                // TODO(davide): Increase/decrease exposure index?
            }

            return true
        }
    }
}
