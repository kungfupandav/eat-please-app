package com.eatplease.app.settings

import android.content.Context
import com.eatplease.app.EatPleaseApplication

private const val PREFS_NAME = "audio_poke"
private const val KEY_ENABLED = "enabled"
private const val KEY_MIN_PACE = "min_pace_bites_per_min"

internal actual class AudioPokePrefs actual constructor() {
    private val prefs =
        EatPleaseApplication.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    actual fun getEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    actual fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
    }

    actual fun getMinPaceBitesPerMin(): Int =
        prefs.getInt(KEY_MIN_PACE, AUDIO_POKE_DEFAULT_MIN_PACE)

    actual fun setMinPaceBitesPerMin(value: Int) {
        prefs.edit().putInt(KEY_MIN_PACE, value).apply()
    }
}
