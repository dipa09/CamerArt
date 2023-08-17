package com.example.camerart

import android.graphics.*


const val FILTER_TYPE_NONE        = 0

const val FILTER_TYPE_NO_GREEN    = 1
const val FILTER_TYPE_GREY        = 2
const val FILTER_TYPE_SEPIA       = 3
const val FILTER_TYPE_NEGATIVE    = 4
const val FILTER_TYPE_AQUA        = 5
const val FILTER_TYPE_FADED       = 6

const val FILTER_TYPE_SKETCH      = 7
const val FILTER_TYPE_BLUR        = 8

const val FILTER_TYPE_MOTION_BLUR = 12
const val FILTER_TYPE_SHARPEN     = 13


/*
[ a, b, c, d, e,
  f, g, h, i, j,
  k, l, m, n, o,
  p, q, r, s, t ]

R' = a*R + b*G + c*B + d*A + e;
G' = f*R + g*G + h*B + i*A + j;
B' = k*R + l*G + m*B + n*A + o;
A' = p*R + q*G + r*B + s*A + t;
*/

fun filterBitmap(source: Bitmap, filterType: Int): Bitmap
{
    var dest = source

    if (source.config == Bitmap.Config.ARGB_8888)
    {
        if (filterType <= FILTER_TYPE_FADED)
        {
            val values = when (filterType)
            {
                FILTER_TYPE_NO_GREEN ->
                    floatArrayOf(
                        1f, 0f, 0f, 0f, 0f,
                        0f, 0f, 0f, 0f, 0f,
                        0f, 0f, 1f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )

                FILTER_TYPE_GREY -> {
                    // R',G',B' = (R + G + B)/3
                    // A'       = A
                    val c = 1f / 3f
                    floatArrayOf(
                        c, c, c, 0f, 0f,
                        c, c, c, 0f, 0f,
                        c, c, c, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                }

                FILTER_TYPE_SEPIA ->
                    // R' = 0.393*R + 0.769*G + 0.189*B
                    // G' = 0.349*R + 0.686*G + 0.168*B
                    // B' = 0.272*R + 0.534*G + 0.131*B
                    // A' = A
                    floatArrayOf(
                        0.393f, 0.769f, 0.189f, 0f, 0f,
                        0.349f, 0.686f, 0.168f, 0f, 0f,
                        0.272f, 0.534f, 0.131f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )

                FILTER_TYPE_NEGATIVE ->
                    // R' = 255 - R
                    // G' = 255 - G
                    // B' = 255 - B
                    // A' = A
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )

                FILTER_TYPE_AQUA ->
                    // R' = B
                    // G' = G
                    // B' = R
                    floatArrayOf(
                        0f, 0f, 1f, 0f, 0f,
                        0f, 1f, 0f, 0f, 0f,
                        1f, 0f, 0f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )

                FILTER_TYPE_FADED ->
                    floatArrayOf(
                        0.66f, 0.33f, 0.33f, 0f, 0f,
                        0.33f, 0.66f, 0.33f, 0f, 0f,
                        0.33f, 0.33f, 0.66f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )

                else -> return source
            }

            val filter = ColorMatrixColorFilter(ColorMatrix(values))
            dest = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(dest)
            val paint = Paint()
            paint.colorFilter = filter
            canvas.drawBitmap(source, 0f, 0f, paint)
        }
        else if (filterType == FILTER_TYPE_SKETCH)
        {
            dest = if (source.isMutable) source else source.copy(source.config, true)
            val pixels = IntArray(dest.width * dest.height)
            dest.getPixels(pixels, 0, dest.width, 0, 0, dest.width, dest.height)

            val INTENSITY_THRESHOLD = 120

            for (i in pixels.indices)
            {
                val color = pixels[i]
                val R = Color.red(color)
                val G = Color.green(color)
                val B = Color.blue(color)

                val intensity = (R + G + B) / 3
                pixels[i] = if (intensity > INTENSITY_THRESHOLD)
                    Color.WHITE
                else if (intensity > INTENSITY_THRESHOLD - 20)
                    Color.GRAY
                else
                    Color.BLACK
            }

            dest.setPixels(pixels, 0, dest.width, 0, 0, dest.width, dest.height)
        }
        else if (filterType == FILTER_TYPE_BLUR)
        {
            dest = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(dest)
            val paint = Paint()
            paint.maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)
            paint.isFilterBitmap = true
            canvas.drawBitmap(source, 0f, 0f, paint)
        }
    }

    return dest
}

class FilterThread : Thread()
{
    var sourceBitmap: Bitmap? = null
    var destBitmap: Bitmap? = null
    var filterType: Int = FILTER_TYPE_NONE

    override fun run()
    {
        assert(sourceBitmap != null)
        destBitmap = filterBitmap(sourceBitmap!!, filterType)
    }

}