package com.example.camerart

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.camera.core.ImageProxy
import java.io.IOException
import java.io.OutputStream

class Image(imageInit: ImageProxy, formatInit: String) {
    var image: ImageProxy = imageInit
    var format: String = formatInit

    fun write(out: OutputStream) {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())

        Log.d("XX", "Image format is ${image.format}")

        if (format == MainActivity.MIME_TYPE_JPEG) {
            out.write(bytes)
        } else {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val bmpFmt = when (format) {
                MainActivity.MIME_TYPE_JPEG -> Bitmap.CompressFormat.JPEG
                MainActivity.MIME_TYPE_PNG -> Bitmap.CompressFormat.PNG
                MainActivity.MIME_TYPE_WEBP -> {
                    if (Build.VERSION.SDK_INT >= MainActivity.MIN_VERSION_FOR_WEBP) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        throw IllegalArgumentException("Invalid image format")
                    }
                }
                else -> throw IllegalArgumentException("Invalid image format")
            }
            if (!bitmap!!.compress(bmpFmt, 95, out))
                throw IOException("Failed to save bitmap")
        }
    }
}