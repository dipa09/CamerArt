package com.example.camerart

import android.graphics.*
import java.lang.Integer.max
import java.lang.Integer.min


const val FILTER_TYPE_NONE        = 0

const val FILTER_TYPE_NO_GREEN    = 1
const val FILTER_TYPE_GREY        = 2
const val FILTER_TYPE_SEPIA       = 3
const val FILTER_TYPE_NEGATIVE    = 4
const val FILTER_TYPE_AQUA        = 5
const val FILTER_TYPE_FADED       = 6

const val FILTER_TYPE_SKETCH      = 7

// NOTE(davide): Expensive filters
const val FILTER_TYPE_BLUR          = 8
const val FILTER_TYPE_EDGE          = 9
const val FILTER_TYPE_SHARPEN_LIGHT = 10
const val FILTER_TYPE_SHARPEN_HARD  = 11
const val FILTER_TYPE_EMBOSS        = 12
//
const val EXPENSIVE_FILTER_COUNT = 5

private data class FilterKernel(
    val values: FloatArray,
    val width: Int = 3,
    val height: Int = 3,
    val factor: Float = 1.0f,
    val bias: Float = 0.0f
)

private fun gaussianBlur(): FilterKernel {
    return FilterKernel(
        floatArrayOf(
            1f, 2f, 1f,
            2f, 4f, 2f,
            1f, 2f, 1f
        ), factor = 1f/16f)
}

private fun edgeDetector(): FilterKernel {
    return FilterKernel(
        floatArrayOf(
            -1f, -1f, -1f,
            -1f,  8f, -1f,
            -1f, -1f, -1f
        )
    )
}

private fun sharpenLight(): FilterKernel {
    return FilterKernel(
        floatArrayOf(
            -1f, -1f, -1f,
            -1f,  9f, -1f,
            -1f, -1f, -1f
        )
    )
}

private fun sharpenHard(): FilterKernel {
    return FilterKernel(
        floatArrayOf(
            1f,  1f,  1f,
            1f, -7f,  1f,
            1f,  1f,  1f
        )
    )
}

private fun emboss(): FilterKernel {
    return FilterKernel(
        floatArrayOf(
            -1f, -1f,  0f,
            -1f,  0f,  1f,
            0f,  1f,  1f
        ), bias = 128.0f
    )
}

private fun applyFilter(pixels: IntArray, w: Int, h: Int, flt: FilterKernel) {
    val filterWidth = flt.width
    val filterHeight = flt.height
    val factor = flt.factor
    val bias = flt.bias
    val filter = flt.values

    for (x in 0 until w) {
        for (y in 0 until h) {
            var R = 0.0f
            var G = 0.0f
            var B = 0.0f

            for (filterY in 0 until filterHeight) {
                for (filterX in 0 until filterWidth) {
                    val imageX: Int = (x - filterWidth/2 + filterX + w) % w
                    val imageY: Int = (y - filterHeight/2 + filterY + h) % h
                    val pixel = pixels[imageY*w + imageX]
                    val c = filter[filterY*filterWidth + filterX]
                    R += Color.red(pixel) * c
                    G += Color.green(pixel) * c
                    B += Color.blue(pixel) * c
                }
            }

            //Log.d("XX", "R=$R, G=$G, B=$B")
            val destR = min(max((factor*R + bias).toInt(), 0), 255)
            val destG = min(max((factor*G + bias).toInt(), 0), 255)
            val destB = min(max((factor*B + bias).toInt(), 0), 255)
            //Log.d("XX", "R=$destR, G=$destG, B=$destB")
            pixels[y*w + x] = Color.rgb(destR, destG, destB)
        }
    }
}

private fun getMatrixFor(filterType: Int): FloatArray {
    // [ a, b, c, d, e,       R' = a*R + b*G + c*B + d*A + e
    //   f, g, h, i, j,       G' = f*R + g*G + h*B + i*A + j
    //   k, l, m, n, o,       B' = k*R + l*G + m*B + n*A + o
    //   p, q, r, s, t ]      A' = p*R + q*G + r*B + s*A + t

    return when (filterType) {
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

        else -> throw UnreachableCodePath()
    }
}

private fun getKernelFor(filterType: Int): FilterKernel {
    return when (filterType) {
        FILTER_TYPE_BLUR -> gaussianBlur()
        FILTER_TYPE_EDGE -> edgeDetector()
        FILTER_TYPE_SHARPEN_LIGHT -> sharpenLight()
        FILTER_TYPE_SHARPEN_HARD -> sharpenHard()
        FILTER_TYPE_EMBOSS -> emboss()
        else -> throw UnreachableCodePath()
    }
}

fun filterBitmap(source: Bitmap, filterType: Int, beefyDevice: Boolean = false): Bitmap {
    var dest = source

    if (source.config == Bitmap.Config.ARGB_8888) {
        if (filterType <= FILTER_TYPE_FADED) {
            val filter = ColorMatrixColorFilter(ColorMatrix(getMatrixFor(filterType)))
            dest = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(dest)
            val paint = Paint()
            paint.colorFilter = filter
            canvas.drawBitmap(source, 0f, 0f, paint)
        } else if (filterType == FILTER_TYPE_SKETCH) {
            dest = if (source.isMutable) source else source.copy(source.config, true)
            val pixels = IntArray(dest.width * dest.height)
            dest.getPixels(pixels, 0, dest.width, 0, 0, dest.width, dest.height)

            val INTENSITY_THRESHOLD = 120

            for (i in pixels.indices) {
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
        } else if (filterType <= FILTER_TYPE_EMBOSS) {
            if (beefyDevice) {
                dest = if (source.isMutable) source else source.copy(source.config, true)
                val pixels = IntArray(dest.width * dest.height)
                dest.getPixels(pixels, 0, dest.width, 0, 0, dest.width, dest.height)

                applyFilter(pixels, dest.width, dest.height, getKernelFor(filterType))

                dest.setPixels(pixels, 0, dest.width, 0, 0, dest.width, dest.height)
            } else if (filterType == FILTER_TYPE_BLUR) {
                dest = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(dest)
                val paint = Paint()
                paint.maskFilter = BlurMaskFilter(150f, BlurMaskFilter.Blur.INNER)
                paint.isFilterBitmap = true
                canvas.drawBitmap(source, 0f, 0f, paint)
            } else {
                throw UnreachableCodePath()
            }
        }
    }

    return dest
}