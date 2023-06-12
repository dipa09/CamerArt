package com.example.camerart

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
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
import androidx.preference.PreferenceManager
import com.example.camerart.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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

    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var currCamInfo: CameraInfo? = null

    // Settings
    private var currUseCase: CameraUseCase = CameraUseCase.PREVIEW_CAPTURE
    // Capture
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var captureMode: Int = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
    private var flashMode: Int = ImageCapture.FLASH_MODE_AUTO
    private var jpegQuality: Int = JPEG_QUALITY_UNINITIALIZED
    private var targetRotation: Int = TARGET_ROTATION_UNINITIALIZED

    // Video
    private var audioEnabled: Boolean = true
    private var videoQuality: Quality = Quality.HIGHEST

    // Preview
    private var scaleType: PreviewView.ScaleType = PreviewView.ScaleType.FIT_CENTER

    // Behavioral
    private var photoOnClickEnabled: Boolean = false
    //

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*
        // NOTE(davide): This must come before setContentView
        with(window) {
            requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
            enterTransition = Slide()
            exitTransition = Explode()
        }
         */

        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        if (allPermissionsGranted()) {
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

        viewBinding.viewFinder.setOnClickListener {
            if (photoOnClickEnabled)
                takePhoto()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun toggleAudio() {
        audioEnabled = !audioEnabled
        if (audioEnabled)
            viewBinding.muteButton.background.alpha = 0xff/2
        else
            viewBinding.muteButton.background.alpha = 0xff
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
            val qualities = QualitySelector.getSupportedQualities(camInfo)
            val values = Array<String>(qualities.size + 2){""}
            for (i in qualities.indices) {
                when (qualities[i]) {
                    Quality.SD -> values[i] = SupportedQuality.SD.name
                    Quality.HD -> values[i] = SupportedQuality.HD.name
                    Quality.FHD -> values[i] = SupportedQuality.FHD.name
                    Quality.UHD -> values[i] = SupportedQuality.UHD.name
                }
            }
            values[values.size - 2] = "Highest"
            values[values.size - 1] = "Lowest"
            intent.putExtra("supportedQualities", values)
        }

        this.startActivity(intent, ActivityOptions.makeSceneTransitionAnimation(this).toBundle())
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

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            }
        )
    }

    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return

        viewBinding.videoCaptureButton.isEnabled = false

        val curRecording = recording
        if (curRecording != null) {
            // Stop the current recording session.
            curRecording.stop()
            recording = null
            return
        }

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
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        viewBinding.videoCaptureButton.apply {
                            text = getString(R.string.stop_capture)
                            isEnabled = true
                        }
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val msg = "Video capture succeeded: " + "${recordEvent.outputResults.outputUri}"
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT)
                                .show()
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
    }

    @SuppressLint("WrongConstant")
    private fun startCamera() {
        Log.i(TAG, "START CAMERA")

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .apply {
                    if (targetRotation != TARGET_ROTATION_UNINITIALIZED)
                        setTargetRotation(targetRotation)
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
}
