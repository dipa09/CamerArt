package com.example.camerart

import android.os.Build
import android.util.Log
import java.io.BufferedReader
import java.io.FileReader
import java.io.InputStreamReader
import java.net.URL
import javax.net.ssl.HttpsURLConnection


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

        val models = MutableList(1) {""}
        while (true) {
            val line = br.readLine()
            if (line == null)
                break
            models.add(line)
        }
        //models.add("ONE E1003")

        result = models.binarySearch(Build.MODEL) >= 0
    }
    catch (_: Exception) { }

    return result
}

fun peekToken(tok: String, startAt: Int): Pair<Int, Int>
{
    var start = startAt
    while (start < tok.length)
    {
        if (!tok[start].isWhitespace())
            break
        ++start
    }

    var end = start
    while (end < tok.length)
    {
        if (tok[end].isWhitespace())
            break
        ++end
    }

    return Pair(start, end)
}

fun isBeefyDevice(): Boolean {
    val MIN_CORES = 7
    val MIN_MEMORY_SIZE_GB: Long = 7

    var result = false
    val numCores = Runtime.getRuntime().availableProcessors()
    if (numCores >= MIN_CORES) {
        try {
            val br = BufferedReader(FileReader("/proc/meminfo"))
            while (true) {
                val line = br.readLine()
                if (line == null)
                    break

                if (line.startsWith("MemTotal:", false)) {
                    // 2940592 kB

                    var r = peekToken(line, 9)
                    var maxSize = line.substring(r.first, r.second).toLong()

                    r = peekToken(line, r.second)
                    if (r.second - r.first > 0) {
                        val factor = line.substring(r.first, r.second).uppercase()
                        when (factor) {
                            "KB" -> maxSize = kilobytes(maxSize)
                            "MB" -> maxSize = megabytes(maxSize)
                            "GB" -> maxSize = gigabytes(maxSize)
                        }
                    }

                    result = (maxSize >= gigabytes(MIN_MEMORY_SIZE_GB))
                    //Log.d("XX", "$maxSize")
                    break
                }
            }
        } catch (_: Exception) { }
    }

    return result
}