package com.eatplease.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One "watch" period: from the parent pressing Start until Stop. */
@Entity
data class WatchSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null,
)
