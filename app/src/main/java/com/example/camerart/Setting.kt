package com.example.camerart

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.camera.core.CameraInfo
import androidx.camera.core.ExposureState
import androidx.camera.core.ImageCapture
import androidx.camera.video.Recorder



// NOTE(davide): The "recommended" way is to use Parcel, but it requires API level 33 which is to
// high for our target.
data class SettingExposure(val index: Int, val min: Int, val max: Int, val step: Int)
fun exposureSettingFromBundle(bundle: Bundle): SettingExposure {
    return SettingExposure(
        bundle.getInt("index"),
        bundle.getInt("min"),
        bundle.getInt("max"),
        bundle.getInt("step"))
}

fun exposureStateToBundle(expState: ExposureState): Bundle {
    val B = Bundle()
    B.putBoolean("supported", expState.isExposureCompensationSupported)
    if (expState.isExposureCompensationSupported) {
        B.putInt("index", expState.exposureCompensationIndex)

        val expRange = expState.exposureCompensationRange
        B.putInt("min", expRange.lower)
        B.putInt("max", expRange.upper)

        val step = expState.exposureCompensationStep.toFloat() + 0.5f
        B.putInt("step", step.toInt())
    }

    return B
}

data class CameraFeatures(val hasFront: Boolean, val hasFlash: Boolean, val hasMulti: Boolean)
fun initCameraFeatures(packageManager: PackageManager): CameraFeatures {
    val multi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_CONCURRENT)

    return CameraFeatures(
        packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT),
        packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH),
        multi)
}

fun cameraFeaturesToBundle(features: CameraFeatures): Bundle {
    val bundle = Bundle()
    bundle.putBoolean("hasFront", features.hasFront)
    bundle.putBoolean("hasFlash", features.hasFlash)
    bundle.putBoolean("hasMulti", features.hasMulti)
    return bundle
}

fun cameraFeaturesFromBundle(bundle: Bundle): CameraFeatures {
    return CameraFeatures(
        bundle.getBoolean("hasFront"),
        bundle.getBoolean("hasFlash"),
        bundle.getBoolean("hasMulti"))
}

fun querySupportedVideoQualities(camInfo: CameraInfo): Array<String> {
    val qualities = ArrayList<String>(4)

    val capabilities = Recorder.getVideoCapabilities(camInfo)
    val dynamicRanges = capabilities.supportedDynamicRanges
    for (range in dynamicRanges) {
        //Log.d("??", "Range: $range")
        val supportedQualities = capabilities.getSupportedQualities(range)
        for (quality in supportedQualities) {
            //Log.d("??", "Quality: $quality")
            val name = videoQualityName(quality)
            assert(name.isNotEmpty())
            qualities.add(name)
        }
    }

    return qualities.toTypedArray()
/*
            val values = Array(qualities.size + 2){""}
            val resolutionNames = Array(qualities.size + 2){""}
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
 */
}