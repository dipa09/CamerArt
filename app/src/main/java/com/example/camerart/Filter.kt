package com.example.camerart

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

const val FILTER_TYPE_NONE        = 0
const val FILTER_TYPE_NO_GREEN    = 1
const val FILTER_TYPE_GREY        = 2
const val FILTER_TYPE_SEPIA       = 3
const val FILTER_TYPE_SKETCH      = 4
const val FILTER_TYPE_NEGATIVE    = 5
const val FILTER_TYPE_BLUR        = 6
const val FILTER_TYPE_MOTION_BLUR = 7
const val FILTER_TYPE_SHARPEN     = 8
const val FILTER_TYPE_AQUA        = 9

class PixelIterator(private val pixels: IntArray, private val width: Int) {
    fun get(x: Int, y: Int): Int {
        return pixels[x*width + y]
    }

    fun set(x: Int, y: Int, p: Int) {
        pixels[y*width + x] = p
    }
}

fun applyFilterToBitmap(source: Bitmap, filterType: Int): Bitmap {
    if (filterType == FILTER_TYPE_NONE)
        return source

    val dest: Bitmap = if (source.isMutable) {
        source
    } else {
        source.copy(source.config, true)
    }

    if (dest.config == Bitmap.Config.ARGB_8888) {
        val pixels = IntArray(dest.width * dest.height)
        dest.getPixels(pixels, 0, dest.width, 0, 0, dest.width, dest.height)

        when (filterType) {
            FILTER_TYPE_NO_GREEN -> {
                for (i in pixels.indices) {
                    val color = pixels[i]
                    val A = Color.alpha(color)
                    val R = Color.red(color)
                    val B = Color.blue(color)
                    pixels[i] = Color.argb(A, R, 0, B)
                }
            }

            FILTER_TYPE_GREY -> {
                for (i in pixels.indices) {
                    val color = pixels[i]
                    val A = Color.alpha(color)
                    val R = Color.red(color)
                    val G = Color.green(color)
                    val B = Color.blue(color)

                    val I = (R + G + B) / 3
                    pixels[i] = Color.argb(A, I, I, I)
                }
            }

            FILTER_TYPE_SEPIA -> {
                for (i in pixels.indices) {
                    val color = pixels[i]
                    val A = Color.alpha(color)
                    val R = Color.red(color)
                    val G = Color.green(color)
                    val B = Color.blue(color)

                    val destR = (0.393*R + 0.769*G + 0.189*B).toInt()
                    val destG = (0.349*R + 0.686*G + 0.168*B).toInt()
                    val destB = (0.272*R + 0.534*G + 0.131*B).toInt()
                    pixels[i] = Color.argb(A, destR, destG, destB)
                }
            }

            FILTER_TYPE_SKETCH -> {
                val INTENSITY_THRESHOLD = 120

                for (i in pixels.indices) {
                    val color = pixels[i]
                    val A = Color.alpha(color)
                    val R = Color.red(color)
                    val G = Color.green(color)
                    val B = Color.blue(color)

                    val intensity = (R + G + B) / 3
                    pixels[i] = if (intensity > INTENSITY_THRESHOLD) {
                        // white
                        Color.argb(A, 255, 255, 255)
                    } else if (intensity > INTENSITY_THRESHOLD - 20) {
                        // grey
                        Color.argb(A, 150, 150, 150)
                    } else {
                        // black
                        Color.argb(A, 0, 0, 0)
                    }
                }
            }

            FILTER_TYPE_NEGATIVE -> {
                for (i in pixels.indices) {
                    val color = pixels[i]
                    val A = Color.alpha(color)
                    val R = 255 - Color.red(color)
                    val G = 255 - Color.green(color)
                    val B = 255 - Color.blue(color)
                    pixels[i] = Color.argb(A, R, G, B)
                }
            }

            FILTER_TYPE_BLUR -> {
                //val iter = PixelIterator(pixels, dest.width)
                val w = dest.width
                for (x in 0 until dest.width) {
                    for (y in 0 until dest.height) {
                        if (x < 1 || y < 1 || x + 1 == dest.width || y + 1 == dest.height)
                            continue

                        val sum = pixels[(y + 1)*w + x - 1] +
                                  pixels[(y + 1)*w + x] +
                                  pixels[(y + 1)*w + x + 1] +
                                  pixels[y*w + x - 1] +
                                  pixels[y*w + x] +
                                  pixels[y*w + x + 1] +
                                  pixels[(y - 1)*w + x - 1] +
                                  pixels[(y - 1)*w + x] +
                                  pixels[(y - 1)*w + x + 1]
                        pixels[y*w + x] = sum/9
                        /*
                        val sum = iter.get(x - 1, y + 1) + // Top left
                                  iter.get(x + 0, y + 1) + // Top center
                                  iter.get(x + 1, y + 1) + // Top right
                                  iter.get(x - 1, y + 0) + // Mid left
                                  iter.get(x + 0, y + 0) + // Current pixel
                                  iter.get(x + 1, y + 0) + // Mid right
                                  iter.get(x - 1, y - 1) + // Low left
                                  iter.get(x + 0, y - 1) + // Low center
                                  iter.get(x + 1, y - 1)  // Low right
                        iter.set(x, y, sum / 9)
                         */
                    }
                }
            }

            FILTER_TYPE_MOTION_BLUR -> {
                val motionFlt = arrayOf(
                    intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0),
                    intArrayOf(0, 1, 0, 0, 0, 0, 0, 0, 0),
                    intArrayOf(0, 0, 1, 0, 0, 0, 0, 0, 0),
                    intArrayOf(0, 0, 0, 1, 0, 0, 0, 0, 0),
                    intArrayOf(0, 0, 0, 0, 1, 0, 0, 0, 0),
                    intArrayOf(0, 0, 0, 0, 0, 1, 0, 0, 0),
                    intArrayOf(0, 0, 0, 0, 0, 0, 1, 0, 0),
                    intArrayOf(0, 0, 0, 0, 0, 0, 0, 1, 0),
                    intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1),
                )

                val factor = 1.0/9.0
                val bias = 0.0

                val filterWidth = 9
                val filterHeight = 9

                val iter = PixelIterator(pixels, dest.width)
                for (x in 0 until dest.width) {
                    for (y in 0 until dest.height) {

                        var R = 0.0
                        var G = 0.0
                        var B = 0.0

                        for (filterX in 0 until filterWidth) {
                            for (filterY in 0 until filterHeight) {
                                val imgX = (x - filterWidth/2 + filterX + dest.width) % dest.width
                                val imgY = (y - filterHeight/2 + filterY + dest.height) % dest.height
                                val pixel = iter.get(imgX, imgY)

                                R += Color.red(pixel)*motionFlt[filterY][filterX]
                                G += Color.green(pixel)*motionFlt[filterY][filterX]
                                B += Color.blue(pixel)*motionFlt[filterY][filterX]
                            }
                        }

                        val destA = Color.alpha(iter.get(x, y))
                        val destR = min(max((R*factor + bias).toInt(), 0), 255)
                        val destG = min(max((G*factor + bias).toInt(), 0), 255)
                        val destB = min(max((B*factor + bias).toInt(), 0), 255)
                        iter.set(x, y, Color.argb(destA, destR, destG, destB))
                    }
                }
            }

            FILTER_TYPE_SHARPEN -> {
                val filter = arrayOf(
                    intArrayOf(-1, -1, -1),
                    intArrayOf(-1, 9, -1),
                    intArrayOf(-1, -1, -1)
                )
                val filterWidth = 3
                val filterHeight = 3

                for (x in 0 until dest.width) {
                    for (y in 0 until dest.height) {

                        var R = 0.0
                        var G = 0.0
                        var B = 0.0

                        /*
                        for (filterX in 0 until filterWidth) {
                            for (filterY in 0 until filterHeight) {
                                val imgX = (x - filterWidth/2 + filterX + dest.width) % dest.width
                                val imgY = (y - filterHeight/2 + filterY + dest.height) % dest.height
                                val pixel = pixels[imgY*dest.width + imgX]

                                R += Color.red(pixel)*filter[filterY][filterX]
                                G += Color.green(pixel)*filter[filterY][filterX]
                                B += Color.blue(pixel)*filter[filterY][filterX]
                            }
                        }

                        val i = y*dest.width + x
                        val A = Color.alpha(pixels[i])
                        val destR = min(max(R.toInt(), 0), 255)
                        val destG = min(max(G.toInt(), 0), 255)
                        val destB = min(max(B.toInt(), 0), 255)
                        pixels[i] = Color.argb(A, destR, destG, destB)
*/
                        val i = y*dest.width + x
                        val A = Color.alpha(pixels[i])
                        pixels[i] = Color.argb(A, 255, 0, 0)
                    }
                }
            }

            FILTER_TYPE_AQUA -> {
                for (i in pixels.indices) {
                    val color = pixels[i]
                    val A = Color.alpha(color)
                    val R = Color.blue(color)
                    val G = Color.green(color)
                    val B = Color.red(color)
                    pixels[i] = Color.argb(A, R, G, B)
                }
            }
        }

        dest.setPixels(pixels, 0, dest.width, 0, 0, dest.width, dest.height)
    } else {
        assert(false)
    }

    return dest
}
