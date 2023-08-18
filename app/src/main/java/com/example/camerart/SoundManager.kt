package com.example.camerart

import android.media.MediaActionSound

class SoundManager {
    private var lastLoadedSound: Int = -1
    private var action: MediaActionSound = MediaActionSound()

    fun prepare(sound: Int) {
        if (sound != lastLoadedSound) {
            action.load(sound)
            lastLoadedSound = sound
        }
    }

    fun play() { action.play(lastLoadedSound) }

    fun play(sound: Int) {
        prepare(sound)
        play()
    }

    fun playOnce(sound: Int) { action.play(sound) }
}