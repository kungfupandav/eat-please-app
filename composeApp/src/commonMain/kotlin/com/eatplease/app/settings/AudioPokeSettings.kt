package com.eatplease.app.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val AUDIO_POKE_MIN_PACE: Int = 1
const val AUDIO_POKE_MAX_PACE: Int = 5
const val AUDIO_POKE_DEFAULT_MIN_PACE: Int = 2

fun clampMinPaceBitesPerMin(value: Int): Int =
    value.coerceIn(AUDIO_POKE_MIN_PACE, AUDIO_POKE_MAX_PACE)

/**
 * Audio poke preferences and reminder recording for Settings and (later) Home.
 *
 * **Home usage:** while watching, if [enabled] and [hasRecording], compare live
 * pace (bites/min) to [minPaceBitesPerMin]. When live pace falls below the
 * threshold, play [recordingPath] — but only when
 * [AudioPokeCooldown.canPlay] is true for the last poke time (min gap
 * [AudioPokeCooldown.MIN_GAP_MS] = 1 minute). After playing, record the poke
 * timestamp so the cooldown applies.
 */
class AudioPokeSettings(
    val recording: AudioPokeRecording = AudioPokeRecording(),
) {
    private val prefs = AudioPokePrefs()

    private val _enabled = MutableStateFlow(prefs.getEnabled())
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _minPaceBitesPerMin =
        MutableStateFlow(clampMinPaceBitesPerMin(prefs.getMinPaceBitesPerMin()))
    val minPaceBitesPerMin: StateFlow<Int> = _minPaceBitesPerMin.asStateFlow()

    val hasRecording: StateFlow<Boolean> = recording.hasRecording

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        prefs.setEnabled(value)
    }

    fun setMinPaceBitesPerMin(value: Int) {
        val clamped = clampMinPaceBitesPerMin(value)
        _minPaceBitesPerMin.value = clamped
        prefs.setMinPaceBitesPerMin(clamped)
    }

    /** Opaque path for platform playback, or null if no reminder is stored. */
    fun recordingPath(): String? = recording.recordingPath()

    suspend fun startRecording(): Boolean = recording.startRecording()

    fun stopRecording() {
        recording.stopRecording()
    }

    fun eraseRecording() {
        recording.eraseRecording()
    }
}
