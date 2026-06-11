package com.example.ez_capstone.trace

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface DecisionTraceDao {

    @Insert
    suspend fun insert(trace: DecisionTraceEntity): Long

    @Query("SELECT * FROM decision_traces ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<DecisionTraceEntity>

    @Query("SELECT * FROM decision_traces WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    suspend fun getBySession(sessionId: String): List<DecisionTraceEntity>

    @Query("DELETE FROM decision_traces WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("SELECT COUNT(*) FROM decision_traces")
    suspend fun count(): Int
}
