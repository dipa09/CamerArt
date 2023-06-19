package com.example.camerart

import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore


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