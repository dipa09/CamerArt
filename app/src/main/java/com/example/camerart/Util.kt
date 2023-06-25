package com.example.camerart

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.impl.utils.futures.FutureCallback
import androidx.camera.core.impl.utils.futures.Futures
import androidx.camera.extensions.ExtensionMode
import com.google.common.util.concurrent.ListenableFuture
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executor
import java.util.concurrent.Executors
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

// https://developer.android.com/training/camerax/devices
// https://support.google.com/googleplay/answer/1727131?hl=en-GB
fun deviceHasBeenTested(): Boolean {
    var result = false
    try {
        val source = "https://raw.githubusercontent.com/dipa09/CamerArt/camera/todo.txt"
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
