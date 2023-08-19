package com.example.camerart

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.Recorder


data class CameraFeaturesFromBundle(
    val hasFlash: Boolean,
    val hasMulti: Boolean,
    val qualityNames: Array<String>
)

class CameraFeatures(cameraProvider: ProcessCameraProvider, packageManager: PackageManager) {
    val hasFront: Boolean
    val hasFlash: Boolean
    val hasMulti: Boolean
    val frontVideoQualities: Array<Quality>
    val backVideoQualities: Array<Quality>

    init {
        hasMulti = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_CONCURRENT))

        var gotFront = false
        var gotFlash = false
        val frontQualities = ArrayList<Quality>(4)
        val backQualities = ArrayList<Quality>(4)

        for (camInfo in cameraProvider.availableCameraInfos) {
            val lens = camInfo.lensFacing
            if (lens == CameraSelector.LENS_FACING_FRONT ||
                lens == CameraSelector.LENS_FACING_BACK) {

                if (!gotFlash) {
                    gotFlash = camInfo.hasFlashUnit()
                }

                if (lens == CameraSelector.LENS_FACING_FRONT) {
                    queryVideoQualities(camInfo, frontQualities)
                    gotFront = true
                } else {
                    queryVideoQualities(camInfo, backQualities)
                }
            }
        }

        hasFront = gotFront
        hasFlash = gotFlash
        frontVideoQualities = frontQualities.toTypedArray()
        backVideoQualities = backQualities.toTypedArray()
    }

    private fun queryVideoQualities(camInfo: CameraInfo, qualities: ArrayList<Quality>) {
        val capabilities = Recorder.getVideoCapabilities(camInfo)
        val dynamicRange = capabilities.supportedDynamicRanges
        for (range in dynamicRange) {
            val supportedQualities = capabilities.getSupportedQualities(range)
            for (quality in supportedQualities) {
                qualities.add(quality)
            }
        }
    }

    fun toBundle(wantedFront: Boolean): Bundle {
        val bundle = Bundle()
        bundle.putBoolean("hasFlash", hasFlash)
        bundle.putBoolean("hasMulti", hasMulti)

        val qualities = if (wantedFront)
            frontVideoQualities
        else
            backVideoQualities
        bundle.putStringArray("qualityNames", getQualityNames(qualities))
        return bundle
    }

    companion object {
        fun fromBundle(bundle: Bundle?): CameraFeaturesFromBundle {
            return if (bundle != null) {
                var names = bundle.getStringArray("qualityNames")
                if (names == null)
                    names = arrayOf("")

                CameraFeaturesFromBundle(
                    bundle.getBoolean("hasFlash"),
                    bundle.getBoolean("hasMulti"),
                    names
                )
            } else {
                CameraFeaturesFromBundle(false, false, arrayOf(""))
            }
        }
    }
}

fun videoQualityName(quality: Quality): String {
    return when (quality) {
        Quality.SD -> "SD"
        Quality.HD -> "HD"
        Quality.FHD -> "FHD"
        Quality.UHD -> "UHD"
        else -> ""
    }
}

private fun getQualityNames(qualities: Array<Quality>): Array<String> {
    val names = Array(qualities.size) {""}
    for (i in qualities.indices) {
        val quality = qualities[i]
        names[i] = videoQualityName(quality)
    }

    return names
}

fun videoQualityFromName(name: String): Quality {
    return when (name) {
        "SD" -> Quality.SD
        "HD" -> Quality.HD
        "FHD" -> Quality.FHD
        "UHD" -> Quality.UHD
        else -> Quality.HIGHEST
    }
}

/*
fun cameraFeaturesFromBundle(bundle: Bundle): CameraFeatures {
    return CameraFeatures(
        bundle.getBoolean("hasFront"),
        bundle.getBoolean("hasFlash"),
        bundle.getBoolean("hasMulti"))
}
 */