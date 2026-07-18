package com.eatplease.app.data

import androidx.room.Entity

/**
 * One second of detected eating within a session. The (sessionId, atEpochSecond)
 * key gives the log its per-second resolution: re-detections within the same
 * second overwrite rather than duplicate.
 */
@Entity(primaryKeys = ["sessionId", "atEpochSecond"])
data class EatingEvent(
    val sessionId: Long,
    val atEpochSecond: Long,
    val confidence: Float,
)
