package com.eatplease.app.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DetectionRepositoryTest {

    private fun repository(clockMillis: () -> Long = { 1_000_000L }) =
        DetectionRepository(FakeWatchDao(), clockMillis)

    @Test
    fun startCreatesAnActiveSession() = runTest {
        val repo = repository { 42_000L }

        val session = repo.startOrResumeSession()

        assertEquals(42_000L, session.startedAtEpochMs)
        assertNull(session.endedAtEpochMs)
        assertEquals(session.id, repo.activeSession.first()?.id)
    }

    @Test
    fun startWhileActiveResumesTheSameSession() = runTest {
        val repo = repository()

        val first = repo.startOrResumeSession()
        val second = repo.startOrResumeSession()

        assertEquals(first.id, second.id)
        assertEquals(1, repo.sessions.first().size)
    }

    @Test
    fun endClosesTheActiveSession() = runTest {
        var now = 10_000L
        val repo = repository { now }

        val started = repo.startOrResumeSession()
        now = 25_000L
        val ended = repo.endActiveSession()

        assertNotNull(ended)
        assertEquals(started.id, ended.id)
        assertEquals(25_000L, ended.endedAtEpochMs)
        assertNull(repo.activeSession.first())
    }

    @Test
    fun endWithoutActiveSessionReturnsNull() = runTest {
        assertNull(repository().endActiveSession())
    }

    @Test
    fun endedSessionsRemainInTheLog() = runTest {
        val repo = repository()

        repo.startOrResumeSession()
        repo.endActiveSession()
        repo.startOrResumeSession()
        repo.endActiveSession()

        assertEquals(2, repo.sessions.first().size)
    }

    @Test
    fun endDanglingSessionWithoutActiveSessionReturnsNull() = runTest {
        assertNull(repository().endDanglingSession())
    }

    @Test
    fun endDanglingSessionEndsAtLastRecordedSecond() = runTest {
        var now = 10_000L
        val repo = repository { now }
        val session = repo.startOrResumeSession()
        repo.recordEatingSecond(session.id, atEpochSecond = 42, confidence = 0.5f)
        repo.recordEatingSecond(session.id, atEpochSecond = 57, confidence = 0.6f)

        // Simulate a later restart: the end time reflects the last event, not "now".
        now = 9_999_000L
        val ended = repo.endDanglingSession()

        assertNotNull(ended)
        assertEquals(session.id, ended.id)
        assertEquals(57_000L, ended.endedAtEpochMs)
        assertNull(repo.activeSession.first())
    }

    @Test
    fun endDanglingSessionWithNoEventsEndsAtStart() = runTest {
        val repo = repository { 10_000L }
        val session = repo.startOrResumeSession()

        val ended = repo.endDanglingSession()

        assertNotNull(ended)
        assertEquals(session.startedAtEpochMs, ended.endedAtEpochMs)
        assertNull(repo.activeSession.first())
    }

    @Test
    fun eatingSecondsAreRecordedPerSecond() = runTest {
        val repo = repository()
        val session = repo.startOrResumeSession()

        repo.recordEatingSecond(session.id, atEpochSecond = 100, confidence = 0.5f)
        repo.recordEatingSecond(session.id, atEpochSecond = 101, confidence = 0.6f)

        val events = repo.eventsForSession(session.id).first()
        assertEquals(listOf(100L, 101L), events.map { it.atEpochSecond })
    }

    @Test
    fun sameSecondRedetectionOverwritesInsteadOfDuplicating() = runTest {
        val repo = repository()
        val session = repo.startOrResumeSession()

        repo.recordEatingSecond(session.id, atEpochSecond = 100, confidence = 0.4f)
        repo.recordEatingSecond(session.id, atEpochSecond = 100, confidence = 0.9f)

        val events = repo.eventsForSessionOnce(session.id)
        assertEquals(1, events.size)
        assertEquals(0.9f, events.single().confidence)
    }
}
