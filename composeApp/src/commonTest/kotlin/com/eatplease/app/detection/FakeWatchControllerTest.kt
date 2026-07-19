package com.eatplease.app.detection

import com.eatplease.app.data.DetectionRepository
import com.eatplease.app.data.FakeWatchDao
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FakeWatchControllerTest {

    @Test
    fun startPumpsFramesAndRecordsEatingSeconds() = runTest {
        val repository = DetectionRepository(FakeWatchDao()) { testScheduler.currentTime }
        val manager = WatchSessionManager(
            repository,
            backgroundScope,
            clock = { testScheduler.currentTime },
        )
        val controller = FakeWatchController(manager, FakeFrameClassifier(), backgroundScope)

        controller.startWatching()
        advanceTimeBy(3_000) // ~18 frames, all within the fake's eating phase
        runCurrent()

        val state = manager.state.value
        assertIs<WatchState.Watching>(state)
        assertTrue(state.isEatingNow)
        val events = repository.eventsForSessionOnce(state.sessionId)
        assertTrue(events.isNotEmpty(), "eating seconds should be recorded while pumping")

        controller.stopWatching()
        runCurrent()
        assertIs<WatchState.Idle>(manager.state.value)
    }

    @Test
    fun stopEndsTheSessionAndStopsThePump() = runTest {
        val repository = DetectionRepository(FakeWatchDao()) { testScheduler.currentTime }
        val manager = WatchSessionManager(
            repository,
            backgroundScope,
            clock = { testScheduler.currentTime },
        )
        val controller = FakeWatchController(manager, FakeFrameClassifier(), backgroundScope)

        controller.startWatching()
        advanceTimeBy(1_000)
        runCurrent()
        controller.stopWatching()
        runCurrent()

        val countAfterStop = repository.eventsForSessionOnce(1).size

        advanceTimeBy(5_000) // pump must not produce more events after stop
        runCurrent()

        assertTrue(repository.eventsForSessionOnce(1).size == countAfterStop)
        assertIs<WatchState.Idle>(manager.state.value)
    }
}
