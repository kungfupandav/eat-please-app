package com.eatplease.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchDao {

    @Insert
    suspend fun insertSession(session: WatchSession): Long

    @Query("UPDATE WatchSession SET endedAtEpochMs = :endedAtEpochMs WHERE id = :sessionId")
    suspend fun endSession(sessionId: Long, endedAtEpochMs: Long)

    @Query("SELECT * FROM WatchSession WHERE endedAtEpochMs IS NULL ORDER BY startedAtEpochMs DESC LIMIT 1")
    fun activeSession(): Flow<WatchSession?>

    @Query("SELECT * FROM WatchSession WHERE endedAtEpochMs IS NULL ORDER BY startedAtEpochMs DESC LIMIT 1")
    suspend fun activeSessionOnce(): WatchSession?

    @Query("SELECT * FROM WatchSession ORDER BY startedAtEpochMs DESC")
    fun sessions(): Flow<List<WatchSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EatingEvent)

    @Query("SELECT * FROM EatingEvent WHERE sessionId = :sessionId ORDER BY atEpochSecond")
    fun eventsForSession(sessionId: Long): Flow<List<EatingEvent>>

    @Query("SELECT * FROM EatingEvent WHERE sessionId = :sessionId ORDER BY atEpochSecond")
    suspend fun eventsForSessionOnce(sessionId: Long): List<EatingEvent>
}
