package com.example.camerart

import android.app.AlertDialog
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageProxy
import androidx.camera.extensions.ExtensionMode
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import javax.net.ssl.HttpsURLConnection

fun makeContentValues(displayName: String, mimeType: String): ContentValues {
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

fun buildVideoOptions(filenameFormat: String, contentResolver: ContentResolver): MediaStoreOutputOptions {
    val name = SimpleDateFormat(filenameFormat, Locale.US).format(System.currentTimeMillis())

    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
        }
    }

    return MediaStoreOutputOptions
        .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        .setContentValues(contentValues)
        .build()
}

fun humanizeTime(timeNanos: Long): String {
    val time = timeNanos / 1_000_000_000
    val result = if (time < 60) {
        time.toString() + "\""
    } else {
        val mins = time / 60
        val secs = time % 60
        "${mins}' ${secs}\""
    }

    return result
}

fun humanizeSize(size: Long): String {
    var result = size
    val suffix = if (size < 1024*1024) {
        result /= 1024
        "KB"
    } else if (size < 1024*1024*1024) {
        result /= 1024*1024
        "MB"
    } else {
        result /= 1024*1024*1024
        "GB"
    }

    return result.toString() + suffix
}

fun extensionName(extensionMode: Int): String {
    val name = when (extensionMode) {
        ExtensionMode.AUTO -> "Auto"
        ExtensionMode.BOKEH -> "Bokeh"
        ExtensionMode.FACE_RETOUCH -> "Pretty Face"
        ExtensionMode.HDR -> "HDR"
        ExtensionMode.NIGHT -> "Night"
        else -> ""
    }
    return name
}

fun extensionFromName(name: String): Int {
    val mode = when (name) {
        "Auto" -> ExtensionMode.AUTO
        "Bokeh" -> ExtensionMode.BOKEH
        "Pretty Face" -> ExtensionMode.FACE_RETOUCH
        "HDR" -> ExtensionMode.HDR
        "Night" -> ExtensionMode.NIGHT
        else -> ExtensionMode.NONE
    }
    return mode
}

fun describeMeteringMode(mode: Int): String {
    val sb = StringBuilder()
    if ((mode and FocusMeteringAction.FLAG_AE) != 0)
        sb.append("E")
    if ((mode and FocusMeteringAction.FLAG_AF) != 0)
        sb.append("F")
    if ((mode and FocusMeteringAction.FLAG_AWB) != 0)
        sb.append("WB")

    return sb.toString()
}

// https://developer.android.com/training/camerax/devices
// https://support.google.com/googleplay/answer/1727131?hl=en-GB
fun deviceHasBeenTested(): Boolean {
    var result = false
    try {
        // TODO(davide): Remember to change this to the main branch
        val source = "https://raw.githubusercontent.com/dipa09/CamerArt/camera/supported_devices.txt"
        val url = URL(source)
        val conn: HttpsURLConnection = url.openConnection() as HttpsURLConnection
        val br = BufferedReader(InputStreamReader(conn.inputStream))
        var line: String?

        val models = MutableList(1) {""}
        while (br.readLine().also { line = it } != null) {
            models.add(line!!)
        }
        //models.add("ONE E1003")

        result = models.binarySearch(Build.MODEL) >= 0
    } catch (e: Exception) {
        result = false
    }

    return result
}

fun infoDialog(context: Context) {
    val doNothing = { _: DialogInterface, _: Int -> }

    val B = AlertDialog.Builder(context)
    B.setTitle("Warning")
    B.setMessage("Your device may not be fully supported")
    B.setNeutralButton("OK", doNothing)
    B.show()
}

fun dumpCameraFeatures(packageManager: PackageManager) {
    val features = mutableListOf(
        PackageManager.FEATURE_CAMERA_ANY,
        PackageManager.FEATURE_CAMERA_AUTOFOCUS,
        PackageManager.FEATURE_CAMERA_CAPABILITY_MANUAL_POST_PROCESSING,
        PackageManager.FEATURE_CAMERA_CAPABILITY_MANUAL_SENSOR,
        PackageManager.FEATURE_CAMERA_EXTERNAL,
        PackageManager.FEATURE_CAMERA_FLASH,
        PackageManager.FEATURE_CAMERA_FRONT,
        PackageManager.FEATURE_CAMERA_LEVEL_FULL
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            PackageManager.FEATURE_CAMERA_AR
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            add(PackageManager.FEATURE_CAMERA_CONCURRENT)
    }

    for (feature in features) {
        val result = packageManager.hasSystemFeature(feature)
        Log.d("Camera Feature", "$feature -- $result")
    }
}

data class LumusInfo(val luminosity: Double, val rotation: Int)
fun evalAvgLuminosityAndRotation(image: ImageProxy): LumusInfo {
    val buffer = image.planes[0].buffer
    buffer.rewind()
    val data = ByteArray(buffer.remaining())
    buffer.get(data)
    val pixels = data.map { it.toInt() and 0xFF }
    return LumusInfo(pixels.average(), image.imageInfo.rotationDegrees)
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

fun videoQualityFromName(name: String): Quality {
    return when (name) {
        "SD" -> Quality.SD
        "HD" -> Quality.HD
        "FHD" -> Quality.FHD
        "UHD" -> Quality.UHD
        else -> Quality.HIGHEST
    }
}

fun stringToIntOr0(s: String): Int {
    return try {
        s.toInt()
    } catch (exc: NumberFormatException) {
        0
    }
}
