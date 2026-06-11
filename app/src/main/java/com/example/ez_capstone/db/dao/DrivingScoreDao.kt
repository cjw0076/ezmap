package com.example.ez_capstone.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.ez_capstone.db.entity.DrivingScoreEntity

@Dao
interface DrivingScoreDao {
    @Insert
    suspend fun insert(score: DrivingScoreEntity): Long

    @Query("SELECT * FROM driving_scores ORDER BY startedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 20): List<DrivingScoreEntity>

    @Query("SELECT AVG(finalScore) FROM driving_scores")
    suspend fun getAverageScore(): Float?

    @Query("SELECT COUNT(*) FROM driving_scores")
    suspend fun getCount(): Int
}
