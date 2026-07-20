package com.eatplease.app.settings

import platform.Foundation.NSUserDefaults

private const val KEY_ENABLED = "audio_poke_enabled"
private const val KEY_MIN_PACE = "audio_poke_min_pace_bites_per_min"

internal actual class AudioPokePrefs actual constructor() {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getEnabled(): Boolean = defaults.boolForKey(KEY_ENABLED)

    actual fun setEnabled(value: Boolean) {
        defaults.setBool(value, KEY_ENABLED)
    }

    actual fun getMinPaceBitesPerMin(): Int {
        val stored = defaults.integerForKey(KEY_MIN_PACE)
        // integerForKey returns 0 when unset; treat 0 as "use default".
        return if (stored == 0L) AUDIO_POKE_DEFAULT_MIN_PACE else stored.toInt()
    }

    actual fun setMinPaceBitesPerMin(value: Int) {
        defaults.setInteger(value.toLong(), KEY_MIN_PACE)
    }
}
