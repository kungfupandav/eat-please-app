package com.eatplease.app.data

import com.eatplease.app.platform.currentEpochMillis
import kotlinx.coroutines.flow.Flow

/**
 * Single entry point for everything the app records and reads back:
 * watch sessions and their per-second eating events.
 */
class DetectionRepository(
    private val dao: WatchDao,
    private val clock: () -> Long = ::currentEpochMillis,
) {

    /** The still-running session, or null when not watching. Drives the Start/Stop button. */
    val activeSession: Flow<WatchSession?> = dao.activeSession()

    /** All sessions, newest first. Drives the log screen. */
    val sessions: Flow<List<WatchSession>> = dao.sessions()

    fun eventsForSession(sessionId: Long): Flow<List<EatingEvent>> = dao.eventsForSession(sessionId)

    suspend fun eventsForSessionOnce(sessionId: Long): List<EatingEvent> = dao.eventsForSessionOnce(sessionId)

    /**
     * Starts a new session, or returns the already-active one so that reopening
     * the app while watching never spawns a second session.
     */
    suspend fun startOrResumeSession(): WatchSession {
        dao.activeSessionOnce()?.let { return it }
        val startedAt = clock()
        val id = dao.insertSession(WatchSession(startedAtEpochMs = startedAt))
        return WatchSession(id = id, startedAtEpochMs = startedAt)
    }

    /** Ends the active session if there is one; returns it, or null if not watching. */
    suspend fun endActiveSession(): WatchSession? {
        val active = dao.activeSessionOnce() ?: return null
        val endedAt = clock()
        dao.endSession(active.id, endedAt)
        return active.copy(endedAtEpochMs = endedAt)
    }

    /** Records one detected eating second; same-second re-detections overwrite. */
    suspend fun recordEatingSecond(sessionId: Long, atEpochSecond: Long, confidence: Float) {
        dao.insertEvent(EatingEvent(sessionId, atEpochSecond, confidence))
    }
}
