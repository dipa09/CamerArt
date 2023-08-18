package com.example.camerart

import android.content.Context
import android.media.AudioManager
import android.media.MediaActionSound

class SoundManager {
    private var lastLoadedSound: Int = -1
    private var actionSound: MediaActionSound? = null

    fun enable() {
        if (actionSound == null) {
            lastLoadedSound = -1
            actionSound = MediaActionSound()
        }
    }

    fun disable() {
        val action = actionSound
        if (action != null) {
            action.release()
            actionSound = null
        }
    }

    fun prepare(sound: Int) {
        val action = actionSound
        if (action != null && sound != lastLoadedSound) {
            action.load(sound)
            lastLoadedSound = sound
        }
    }

    fun play() {
        val action = actionSound
        if (action != null)
            action.play(lastLoadedSound)
    }

    fun play(sound: Int) {
        prepare(sound)
        play()
    }

    fun playOnce(sound: Int) {
        val action = actionSound
        if (action != null) {
            action.play(sound)
        }
    }
}

fun deviceIsNoisy(context: Context): Boolean {
    var result = false

    try {
        val service = context.getSystemService(Context.AUDIO_SERVICE)
        if (service != null) {
            val audio = service as AudioManager
            result = (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL)
        }
    } catch (_: Exception) { }

    return result
}