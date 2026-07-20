package com.eatplease.app.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioPokeSettingsTest {

    @Test
    fun clampMinPaceCoercesToOneThroughFive() {
        assertEquals(1, clampMinPaceBitesPerMin(0))
        assertEquals(1, clampMinPaceBitesPerMin(-3))
        assertEquals(1, clampMinPaceBitesPerMin(1))
        assertEquals(3, clampMinPaceBitesPerMin(3))
        assertEquals(5, clampMinPaceBitesPerMin(5))
        assertEquals(5, clampMinPaceBitesPerMin(9))
    }

    @Test
    fun defaultMinPaceIsWithinRange() {
        assertTrue(AUDIO_POKE_DEFAULT_MIN_PACE in AUDIO_POKE_MIN_PACE..AUDIO_POKE_MAX_PACE)
    }

    @Test
    fun cooldownAllowsFirstPlayAndEnforcesOneMinuteGap() {
        assertTrue(AudioPokeCooldown.canPlay(lastPlayedAtEpochMs = null, nowEpochMs = 1_000L))
        assertEquals(60_000L, AudioPokeCooldown.MIN_GAP_MS)

        val last = 10_000L
        assertFalse(AudioPokeCooldown.canPlay(last, nowEpochMs = last + 59_999L))
        assertTrue(AudioPokeCooldown.canPlay(last, nowEpochMs = last + 60_000L))
        assertTrue(AudioPokeCooldown.canPlay(last, nowEpochMs = last + 120_000L))
    }
}
