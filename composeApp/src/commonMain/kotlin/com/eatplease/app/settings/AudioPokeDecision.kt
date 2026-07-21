package com.eatplease.app.settings

/**
 * Pure decision for whether Home should play the audio reminder right now.
 *
 * Rules (all must hold):
 * - Audio poke is [enabled] and a reminder clip exists ([hasRecording]).
 * - Live pace is below the configured minimum ([paceBitesPerMin] < [minPaceBitesPerMin]).
 * - At least [MIN_MONITOR_MS] of monitoring has passed since the last detected
 *   bite — or, if there is no bite yet, since the session started. This gives the
 *   first reminder its "only after 1 minute of watching" grace and re-arms the
 *   gap after every bite.
 * - The per-minute cap holds: at least [AudioPokeCooldown.MIN_GAP_MS] since the
 *   last reminder actually played (see [AudioPokeCooldown]).
 *
 * Kept side-effect free so the home poke loop stays unit-testable.
 */
object AudioPokeDecision {
    /** Minimum monitoring time since the last bite (or session start) before poking. */
    const val MIN_MONITOR_MS: Long = 60_000L

    fun shouldPlay(
        enabled: Boolean,
        hasRecording: Boolean,
        paceBitesPerMin: Double,
        minPaceBitesPerMin: Int,
        sessionStartEpochMs: Long,
        lastBiteAtEpochMs: Long?,
        lastPlayedAtEpochMs: Long?,
        nowEpochMs: Long,
    ): Boolean {
        if (!enabled || !hasRecording) return false
        if (paceBitesPerMin >= minPaceBitesPerMin) return false

        val sinceBiteReference = lastBiteAtEpochMs ?: sessionStartEpochMs
        if (nowEpochMs - sinceBiteReference < MIN_MONITOR_MS) return false

        return AudioPokeCooldown.canPlay(lastPlayedAtEpochMs, nowEpochMs)
    }
}
