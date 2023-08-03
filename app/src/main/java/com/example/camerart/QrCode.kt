package com.example.camerart

import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import com.google.mlkit.vision.barcode.common.Barcode

class QrCode(barcode: Barcode) {
    var boundingRect: Rect = barcode.boundingBox!!
    var content: String = ""
    var touchCallback = { v: View, e: MotionEvent -> false}

    init {
        when (barcode.valueType) {
            Barcode.TYPE_URL -> {
                content = barcode.url!!.url!!
                touchCallback = {v: View, e: MotionEvent ->
                    if (e.action == MotionEvent.ACTION_DOWN && boundingRect.contains(e.getX().toInt(), e.getY().toInt())) {
                        val browserIntent = Intent(Intent.ACTION_VIEW)
                        browserIntent.data = Uri.parse(content)
                        v.context.startActivity(browserIntent)
                    }
                    true
                }
            }
            else -> {
                content = "Unsupported data type ${barcode.rawValue.toString()}"
            }
        }
    }
}