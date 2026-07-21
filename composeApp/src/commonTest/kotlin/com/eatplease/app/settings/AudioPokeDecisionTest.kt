package com.eatplease.app.settings

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioPokeDecisionTest {

    private fun decide(
        enabled: Boolean = true,
        hasRecording: Boolean = true,
        paceBitesPerMin: Double = 0.0,
        minPaceBitesPerMin: Int = 2,
        sessionStartEpochMs: Long = 0L,
        lastBiteAtEpochMs: Long? = null,
        lastPlayedAtEpochMs: Long? = null,
        nowEpochMs: Long,
    ): Boolean = AudioPokeDecision.shouldPlay(
        enabled = enabled,
        hasRecording = hasRecording,
        paceBitesPerMin = paceBitesPerMin,
        minPaceBitesPerMin = minPaceBitesPerMin,
        sessionStartEpochMs = sessionStartEpochMs,
        lastBiteAtEpochMs = lastBiteAtEpochMs,
        lastPlayedAtEpochMs = lastPlayedAtEpochMs,
        nowEpochMs = nowEpochMs,
    )

    @Test
    fun doesNotPlayWhenDisabledOrNoRecording() {
        assertFalse(decide(enabled = false, nowEpochMs = 120_000L))
        assertFalse(decide(hasRecording = false, nowEpochMs = 120_000L))
    }

    @Test
    fun doesNotPlayWhenPaceMeetsMinimum() {
        // Pace equal to or above the threshold means the user is doing fine.
        assertFalse(decide(paceBitesPerMin = 2.0, nowEpochMs = 120_000L))
        assertFalse(decide(paceBitesPerMin = 3.5, nowEpochMs = 120_000L))
    }

    @Test
    fun firstReminderOnlyAfterOneMinuteOfWatching() {
        // No bite yet, so the grace runs from session start.
        assertFalse(decide(sessionStartEpochMs = 0L, nowEpochMs = 59_999L))
        assertTrue(decide(sessionStartEpochMs = 0L, nowEpochMs = 60_000L))
    }

    @Test
    fun gracePeriodRunsFromTheLastBite() {
        // A bite at 90s pushes the next eligible poke to 150s, not 60s.
        assertFalse(decide(lastBiteAtEpochMs = 90_000L, nowEpochMs = 149_999L))
        assertTrue(decide(lastBiteAtEpochMs = 90_000L, nowEpochMs = 150_000L))
    }

    @Test
    fun neverMoreThanOneReminderPerMinute() {
        // Eligible on bite-grace, but a reminder played 30s ago blocks it.
        assertFalse(
            decide(
                lastBiteAtEpochMs = 0L,
                lastPlayedAtEpochMs = 120_000L,
                nowEpochMs = 150_000L,
            ),
        )
        // A full minute after the last reminder, it may play again.
        assertTrue(
            decide(
                lastBiteAtEpochMs = 0L,
                lastPlayedAtEpochMs = 120_000L,
                nowEpochMs = 180_000L,
            ),
        )
    }
}
