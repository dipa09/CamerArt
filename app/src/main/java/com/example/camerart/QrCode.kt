package com.example.camerart

import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.provider.CalendarContract
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.common.Barcode.CalendarDateTime
import com.google.mlkit.vision.barcode.common.Barcode.Phone.*
import com.google.mlkit.vision.barcode.common.Barcode.WiFi.*
import java.text.SimpleDateFormat
import java.util.*

// Docs: https://developers.google.com/android/reference/com/google/mlkit/vision/barcode/common/Barcode
class QrCode(barcode: Barcode) {
    var boundingRect: Rect = barcode.boundingBox!!
    var content: String = ""
    var touchCallback = { _: View, _: MotionEvent -> false}

    init {
        when (barcode.valueType) {
            Barcode.TYPE_CALENDAR_EVENT -> {
                val calendarEvent = barcode.calendarEvent!!
                val start = calendarEvent.start
                if (start != null) {
                    val begin = calendarDateTimeToEpoch(start)
                    if (begin.valid) {
                        var title = ""
                        if (calendarEvent.summary != null)
                            title = calendarEvent.summary!!

                        content = "Event: $title from $start"
                        val end = calendarEvent.end
                        if (end != null)
                            content += " to $end"
                        if (calendarEvent.location != null)
                            content += " at ${calendarEvent.location}"
                        if (calendarEvent.organizer != null)
                            content += " by ${calendarEvent.organizer}"
                        if (calendarEvent.status != null)
                            content += " ${calendarEvent.status}"

                        touchCallback = { v: View, me: MotionEvent ->
                            if (boundingBoxClicked(boundingRect, me)) {
                                val intent = Intent(Intent.ACTION_INSERT).apply {
                                    data = CalendarContract.Events.CONTENT_URI

                                    if (title.isNotEmpty())
                                        putExtra(CalendarContract.Events.TITLE, title)

                                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin.time)

                                    if (end != null) {
                                        val endEpoch = calendarDateTimeToEpoch(end)
                                        if (endEpoch.valid) {
                                            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endEpoch.time)
                                        }
                                    }

                                    val desc = calendarEvent.description
                                    if (desc != null)
                                        putExtra(CalendarContract.Events.DESCRIPTION, desc)
                                }
                                startResolvedActivity(v, intent)
                            }
                            true
                        }
                    }
                }
            }

            Barcode.TYPE_EMAIL -> {
                val email = barcode.email!!
                val address = email.address
                if (address != null) {
                    content = "Email: ${email.address}"
                    touchCallback = { v: View, me: MotionEvent ->
                        if (boundingBoxClicked(boundingRect, me)) {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:")

                                val addresses = arrayOf(address)
                                putExtra(Intent.EXTRA_EMAIL, addresses)

                                val subject = email.subject
                                if (subject != null)
                                    putExtra(Intent.EXTRA_SUBJECT, subject)

                                val body = email.body
                                if (body != null)
                                    putExtra(Intent.EXTRA_TEXT, body)
                            }
                            startResolvedActivity(v, intent)
                        }
                        true
                    }
                }
            }

            // TODO(davide): Handle missing barcodes
            Barcode.TYPE_GEO -> {

            }
            Barcode.TYPE_PHONE -> {
                val phone = barcode.phone!!

                content = "${phoneTypeName(phone.type)}: ${phone.number}"
                touchCallback = { _: View, _: MotionEvent ->

                    true
                }
            }

            Barcode.TYPE_SMS -> {
                val sms = barcode.sms!!
                content = "SMS: ${sms.phoneNumber}"
                touchCallback = { v: View, me: MotionEvent ->
                    if (boundingBoxClicked(boundingRect, me)) {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("smsto:${sms.phoneNumber}")
                            putExtra("sms_body", sms.message)
                        }
                        startResolvedActivity(v, intent)
                    }
                    true
                }
            }

            Barcode.TYPE_URL -> {
                content = barcode.url!!.url!!
                touchCallback = { v: View, me: MotionEvent ->
                    if (boundingBoxClicked(boundingRect, me)) {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse(content)
                        }
                        startResolvedActivity(v, intent)
                    }
                    true
                }
            }

            Barcode.TYPE_WIFI -> {
                val wifi = barcode.wifi!!
                content = "SSID: ${wifi.ssid.toString()}"
                if (wifi.encryptionType != TYPE_OPEN)
                    content += " ${wifiEncryptionTypeName(wifi.encryptionType)} Passwd: ${wifi.password}"
                touchCallback = { v: View, me: MotionEvent ->
                    if (boundingBoxClicked(boundingRect, me)) {
                        val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                        startResolvedActivity(v, intent)
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

data class EpochFromCalendarDateTime(val valid: Boolean, val time: Long)
fun calendarDateTimeToEpoch(dt: CalendarDateTime): EpochFromCalendarDateTime {
    val year = dt.year
    val month = dt.month
    val day = dt.day
    val hours = dt.hours

    val result = try {
        var datetime = "$year-$month-$day"
        var pattern = "yyyy-MM-dd"
        if (hours >= 0) {
            datetime += "-$hours"
            pattern += "-hh"
        }

        //Log.d("QRCODE", "pattern: $pattern, datetime: $datetime")
        val epoch = SimpleDateFormat(pattern, Locale.getDefault()).parse(datetime)
        EpochFromCalendarDateTime(true, epoch.time)
    } catch (e: Exception) {
        EpochFromCalendarDateTime(false, -1)
    }

    return result
}

fun startResolvedActivity(v: View, intent: Intent) {
    if (intent.resolveActivity(v.context.packageManager) != null) {
        v.context.startActivity(intent)
    }
}
fun boundingBoxClicked(bb: Rect, me: MotionEvent): Boolean {
    return (me.action == MotionEvent.ACTION_DOWN &&
            bb.contains(me.x.toInt(), me.y.toInt()))
}
fun wifiEncryptionTypeName(encryptionType: Int): String {
    return when (encryptionType) {
        TYPE_OPEN -> "Open"
        TYPE_WEP -> "WEP"
        TYPE_WPA -> "WPA"
        else -> ""
    }
}

fun phoneTypeName(phoneType: Int): String {
    return when (phoneType) {
        TYPE_FAX -> "Fax"
        TYPE_HOME -> "Home"
        TYPE_MOBILE -> "Mobile"
        TYPE_WORK -> "Work"
        else -> "Unknown phone"
    }
}