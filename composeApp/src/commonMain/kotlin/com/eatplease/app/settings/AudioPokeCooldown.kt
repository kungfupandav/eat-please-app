package com.eatplease.app.settings

/**
 * Minimum gap between audio pokes on the home screen.
 *
 * Home usage: when live pace falls below [AudioPokeSettings.minPaceBitesPerMin],
 * call [canPlay] with the last poke timestamp before playing the reminder.
 * After a successful play, store `nowEpochMs` as the new last-poke time.
 */
object AudioPokeCooldown {
    /** At least one minute between consecutive audio pokes. */
    const val MIN_GAP_MS: Long = 60_000L

    fun canPlay(lastPlayedAtEpochMs: Long?, nowEpochMs: Long): Boolean {
        if (lastPlayedAtEpochMs == null) return true
        return nowEpochMs - lastPlayedAtEpochMs >= MIN_GAP_MS
    }
}
