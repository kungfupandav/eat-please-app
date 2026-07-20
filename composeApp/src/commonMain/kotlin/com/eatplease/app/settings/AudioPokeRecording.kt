package com.eatplease.app.settings

import kotlinx.coroutines.flow.StateFlow

/** Max length of the on-device reminder clip. */
const val AUDIO_POKE_MAX_DURATION_MS: Long = 5_000L

/**
 * Single on-device reminder recording (replaceable / erasable).
 *
 * Platform implementations use MediaRecorder (Android) / AVAudioRecorder (iOS)
 * and store one fixed file under the app's private storage.
 */
expect class AudioPokeRecording() {
    val isRecording: StateFlow<Boolean>
    val hasRecording: StateFlow<Boolean>

    /**
     * Opaque filesystem path to the reminder, or null if none.
     * Home can pass this to a platform audio player when poking.
     */
    fun recordingPath(): String?

    /**
     * Requests mic permission if needed, then records up to
     * [AUDIO_POKE_MAX_DURATION_MS], replacing any previous clip.
     * Returns false if permission was denied or recording failed to start.
     */
    suspend fun startRecording(): Boolean

    /** Stops an in-progress recording early (file is kept). */
    fun stopRecording()

    /** Deletes the on-device reminder file. */
    fun eraseRecording()
}
