package com.eatplease.app.detection

import com.eatplease.app.data.DetectionRepository
import com.eatplease.app.data.FakeWatchDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchSessionManagerTest {

    private val eatingIndex = EatingClasses.indices.first()

    private fun eatingProbs() = FloatArray(FrameClassifier.NUM_CLASSES).also { it[eatingIndex] = 0.9f }
    private fun idleProbs() = FloatArray(FrameClassifier.NUM_CLASSES).also { it[0] = 0.9f }

    @Test
    fun startMovesToWatchingAndStopReturnsToIdle() = runTest {
        val repository = DetectionRepository(FakeWatchDao()) { 1_000L }
        val manager = WatchSessionManager(repository, backgroundScope, clock = { 1_000L })

        val watching = manager.start()
        assertIs<WatchState.Watching>(manager.state.value)
        assertEquals(watching.sessionId, (manager.state.value as WatchState.Watching).sessionId)

        manager.stop()
        assertIs<WatchState.Idle>(manager.state.value)
        assertTrue(repository.sessions.first().single().endedAtEpochMs != null)
    }

    @Test
    fun classifiedEatingFramesPersistPerSecondEvents() = runTest {
        val repository = DetectionRepository(FakeWatchDao()) { 0L }
        val manager = WatchSessionManager(repository, backgroundScope, clock = { 0L })

        val session = manager.start()
        manager.onFrameClassified(eatingProbs(), atEpochMillis = 10_100)
        manager.onFrameClassified(eatingProbs(), atEpochMillis = 10_600)
        manager.onFrameClassified(eatingProbs(), atEpochMillis = 11_200)
        runCurrent()

        val events = repository.eventsForSessionOnce(session.sessionId)
        assertEquals(listOf(10L, 11L), events.map { it.atEpochSecond })

        val state = manager.state.value as WatchState.Watching
        assertTrue(state.isEatingNow)
        assertEquals(11_200, state.lastEatingAtEpochMs)
    }

    @Test
    fun framesAreIgnoredWhenIdle() = runTest {
        val repository = DetectionRepository(FakeWatchDao()) { 0L }
        val manager = WatchSessionManager(repository, backgroundScope, clock = { 0L })

        manager.onFrameClassified(eatingProbs(), atEpochMillis = 10_000)
        runCurrent()

        assertTrue(repository.sessions.first().isEmpty())
    }

    @Test
    fun idleFramesUpdateLiveStateWithoutEvents() = runTest {
        val repository = DetectionRepository(FakeWatchDao()) { 0L }
        val manager = WatchSessionManager(repository, backgroundScope, clock = { 0L })

        val session = manager.start()
        manager.onFrameClassified(idleProbs(), atEpochMillis = 10_000)
        runCurrent()

        val state = manager.state.value as WatchState.Watching
        assertEquals(false, state.isEatingNow)
        assertTrue(repository.eventsForSessionOnce(session.sessionId).isEmpty())
    }

    @Test
    fun restartingWithADanglingSessionEndsItAndStaysIdle() = runTest {
        val dao = FakeWatchDao()
        val repository = DetectionRepository(dao) { 5_000L }
        val existing = repository.startOrResumeSession()

        // Fresh manager, as after the app was killed mid-watch: the previous
        // process's session must be closed out, not resumed.
        val manager = WatchSessionManager(repository, backgroundScope, clock = { 6_000L })
        runCurrent()

        assertIs<WatchState.Idle>(manager.state.value)
        val ended = repository.sessions.first().single()
        assertEquals(existing.id, ended.id)
        assertTrue(ended.endedAtEpochMs != null)
        assertNull(repository.activeSession.first())
    }
}
