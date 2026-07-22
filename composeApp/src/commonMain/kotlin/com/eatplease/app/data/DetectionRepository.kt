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

    /**
     * Closes any session left active by a killed process. A watch only runs
     * while the app process is alive, so a session still active at startup is
     * always a crash/kill remnant. Ends it at its last recorded eating second
     * (or its start, if nothing was recorded) rather than "now", so a session
     * killed long ago doesn't report a runaway duration. Returns it, or null.
     */
    suspend fun endDanglingSession(): WatchSession? {
        val active = dao.activeSessionOnce() ?: return null
        val lastEventSecond = dao.eventsForSessionOnce(active.id).lastOrNull()?.atEpochSecond
        val endedAt = lastEventSecond?.let { it * 1000 }?.coerceAtLeast(active.startedAtEpochMs)
            ?: active.startedAtEpochMs
        dao.endSession(active.id, endedAt)
        return active.copy(endedAtEpochMs = endedAt)
    }

    /** Records one detected eating second; same-second re-detections overwrite. */
    suspend fun recordEatingSecond(sessionId: Long, atEpochSecond: Long, confidence: Float) {
        dao.insertEvent(EatingEvent(sessionId, atEpochSecond, confidence))
    }
}
