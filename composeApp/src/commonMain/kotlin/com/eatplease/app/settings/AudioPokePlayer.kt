package com.eatplease.app.settings

import kotlinx.coroutines.flow.StateFlow

/**
 * Plays the single on-device reminder clip recorded by [AudioPokeRecording].
 *
 * Used by Settings (preview the saved reminder) and Home (poke when pace drops).
 * Platform implementations wrap MediaPlayer (Android) / AVAudioPlayer (iOS) and
 * play one file at a time, flipping [isPlaying] back to false when playback ends.
 */
expect class AudioPokePlayer() {
    val isPlaying: StateFlow<Boolean>

    /** Plays the clip at [path]; no-op if [path] is null or the file is missing. */
    fun play(path: String?)

    /** Stops any in-progress playback. */
    fun stop()
}
