package com.eatplease.app.detection

import com.eatplease.app.data.DetectionRepository
import com.eatplease.app.platform.currentEpochMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface WatchState {
    data object Idle : WatchState

    data class Watching(
        val sessionId: Long,
        val startedAtEpochMs: Long,
        val isEatingNow: Boolean = false,
        val lastEatingAtEpochMs: Long? = null,
        val currentScore: Float = 0f,
    ) : WatchState
}

/**
 * Owns the watch lifecycle: session start/stop, turning classified frames into
 * persisted per-second events, and the live state the home screen renders.
 *
 * Platform detection pipelines push frames via [onFrameClassified]; this class
 * stays platform-free so the whole flow is unit-testable.
 */
class WatchSessionManager(
    private val repository: DetectionRepository,
    private val scope: CoroutineScope,
    private val aggregator: EatingScoreAggregator = EatingScoreAggregator(),
    private val clock: () -> Long = ::currentEpochMillis,
) {

    private val _state = MutableStateFlow<WatchState>(WatchState.Idle)
    val state: StateFlow<WatchState> = _state.asStateFlow()

    init {
        // Reflect the persisted active session, so reopening the app during a
        // watch restores the Watching state (and its Stop button).
        scope.launch {
            repository.activeSession.collect { active ->
                _state.update { current ->
                    when {
                        active == null -> WatchState.Idle
                        current is WatchState.Watching && current.sessionId == active.id -> current
                        else -> WatchState.Watching(
                            sessionId = active.id,
                            startedAtEpochMs = active.startedAtEpochMs,
                        )
                    }
                }
            }
        }
    }

    suspend fun start(): WatchState.Watching {
        val session = repository.startOrResumeSession()
        aggregator.reset()
        val watching = WatchState.Watching(session.id, session.startedAtEpochMs)
        _state.value = watching
        return watching
    }

    suspend fun stop() {
        repository.endActiveSession()
        aggregator.reset()
        _state.value = WatchState.Idle
    }

    /** Feed one frame's 600-class probabilities; no-op unless watching. */
    fun onFrameClassified(probabilities: FloatArray, atEpochMillis: Long = clock()) {
        val current = _state.value as? WatchState.Watching ?: return
        val result = aggregator.onFrame(probabilities, atEpochMillis)
        for (sample in result.newSamples) {
            scope.launch {
                repository.recordEatingSecond(current.sessionId, sample.atEpochSecond, sample.confidence)
            }
        }
        _state.value = current.copy(
            isEatingNow = result.isEating,
            lastEatingAtEpochMs = if (result.isEating) atEpochMillis else current.lastEatingAtEpochMs,
            currentScore = result.smoothedScore,
        )
    }
}
