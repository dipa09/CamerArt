package com.example.camerart

import android.graphics.*
import android.graphics.drawable.Drawable

class QrCodeDrawable(qrCode: QrCode) : Drawable() {
    private val boundingRectPaint = Paint().apply {
        style = Paint.Style.STROKE
        color = Color.YELLOW
        strokeWidth = 5F
        alpha = 200
    }

    private val contentRectPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.YELLOW
        alpha = 255
    }

    private val contentTextPaint = Paint().apply {
        color = Color.DKGRAY
        alpha = 255
        textSize = 36F
    }

    private val qrCode = qrCode
    private val contentPadding = 25
    private var textWidth = contentTextPaint.measureText(qrCode.content).toInt()

    override fun draw(canvas: Canvas) {
        canvas.drawRect(qrCode.boundingRect, boundingRectPaint)
        canvas.drawRect(
            Rect(
                qrCode.boundingRect.left,
                qrCode.boundingRect.bottom + contentPadding/2,
                qrCode.boundingRect.left + textWidth + contentPadding*2,
                qrCode.boundingRect.bottom + contentTextPaint.textSize.toInt() + contentPadding),
            contentRectPaint
        )

        // TODO(davide): Draw the text accordingly to phone orientation
        canvas.drawText(
            qrCode.content,
            (qrCode.boundingRect.left + contentPadding).toFloat(),
            (qrCode.boundingRect.bottom + contentPadding*2).toFloat(),
            contentTextPaint
        )
    }

    override fun setAlpha(alpha: Int) {
        boundingRectPaint.alpha = alpha
        contentRectPaint.alpha = alpha
        contentTextPaint.alpha = alpha
    }

    override fun setColorFilter(colorFiter: ColorFilter?) {
        boundingRectPaint.colorFilter = colorFilter
        contentRectPaint.colorFilter = colorFilter
        contentTextPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

}