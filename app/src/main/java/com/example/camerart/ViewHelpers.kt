package com.example.camerart

import android.os.CountDownTimer
import android.view.View
import android.widget.TextView

fun enableTextView(view: TextView, message: String) {
    view.apply {
        text = message
        visibility = View.VISIBLE
    }
}

fun disableTextView(view: TextView) {
    view.apply {
        text = ""
        visibility = View.INVISIBLE
    }
}

fun showFadingMessage(view: TextView, message: String,
                      seconds: Int = MainActivity.FADING_MESSAGE_DEFAULT_DELAY) {
    enableTextView(view, message)

    object : CountDownTimer(seconds.toLong()*1000, 1000) {
        override fun onTick(p0: Long) {
            // NOTE(davide): Do nothing
        }
        override fun onFinish() { disableTextView(view) }
    }.start()
}

fun showCountdown(view: TextView, seconds: Int) {
    if (seconds > 0) {
        enableTextView(view, "$seconds")

        object : CountDownTimer(seconds.toLong()*1000, 1000) {
            override fun onTick(reamainingMillis: Long) {
                view.text = "${reamainingMillis / 1000}"
            }
            override fun onFinish() { disableTextView(view) }
        }.start()
    }
}