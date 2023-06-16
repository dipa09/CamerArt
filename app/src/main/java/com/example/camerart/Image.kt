package com.example.camerart

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.camera.core.ImageProxy
import java.io.IOException
import java.io.OutputStream

class Image(imageInit: ImageProxy, formatInit: MainActivity.Mime) {
    var image: ImageProxy = imageInit
    var format: MainActivity.Mime = formatInit

    fun write(out: OutputStream) {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())

        if (format != MainActivity.Mime.JPEG) {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val bmpFmt = when (format) {
                MainActivity.Mime.JPEG -> Bitmap.CompressFormat.JPEG
                MainActivity.Mime.PNG -> Bitmap.CompressFormat.PNG
                MainActivity.Mime.WEBP -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        throw IllegalArgumentException("Invalid image format")
                    }
                }
                else -> throw IllegalArgumentException("Invalid image format")
            }
            if (!bitmap!!.compress(bmpFmt, 95, out))
                throw IOException("Failed to save bitmap")
        } else {
            out.write(bytes)
        }
    }
}