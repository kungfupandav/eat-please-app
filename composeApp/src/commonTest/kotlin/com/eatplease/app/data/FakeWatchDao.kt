package com.eatplease.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory WatchDao mirroring the Room semantics the repository relies on:
 * auto-generated ids, "active = endedAtEpochMs IS NULL", and REPLACE conflict
 * behaviour on the (sessionId, atEpochSecond) key.
 */
class FakeWatchDao : WatchDao {

    private val sessionsState = MutableStateFlow<List<WatchSession>>(emptyList())
    private val eventsState = MutableStateFlow<Map<Pair<Long, Long>, EatingEvent>>(emptyMap())
    private var nextId = 1L

    override suspend fun insertSession(session: WatchSession): Long {
        val id = nextId++
        sessionsState.value += session.copy(id = id)
        return id
    }

    override suspend fun endSession(sessionId: Long, endedAtEpochMs: Long) {
        sessionsState.value = sessionsState.value.map {
            if (it.id == sessionId) it.copy(endedAtEpochMs = endedAtEpochMs) else it
        }
    }

    override fun activeSession(): Flow<WatchSession?> = sessionsState.map { list ->
        list.filter { it.endedAtEpochMs == null }.maxByOrNull { it.startedAtEpochMs }
    }

    override suspend fun activeSessionOnce(): WatchSession? =
        sessionsState.value.filter { it.endedAtEpochMs == null }.maxByOrNull { it.startedAtEpochMs }

    override fun sessions(): Flow<List<WatchSession>> =
        sessionsState.map { list -> list.sortedByDescending { it.startedAtEpochMs } }

    override suspend fun insertEvent(event: EatingEvent) {
        eventsState.value += (event.sessionId to event.atEpochSecond) to event
    }

    override fun eventsForSession(sessionId: Long): Flow<List<EatingEvent>> =
        eventsState.map { map ->
            map.values.filter { it.sessionId == sessionId }.sortedBy { it.atEpochSecond }
        }

    override suspend fun eventsForSessionOnce(sessionId: Long): List<EatingEvent> =
        eventsState.value.values.filter { it.sessionId == sessionId }.sortedBy { it.atEpochSecond }
}
