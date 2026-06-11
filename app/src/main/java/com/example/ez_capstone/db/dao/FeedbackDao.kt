package com.example.ez_capstone.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.ez_capstone.db.entity.FeedbackEntity

@Dao
interface FeedbackDao {
    @Insert
    suspend fun insert(feedback: FeedbackEntity)

    @Query("SELECT * FROM feedback ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 20): List<FeedbackEntity>

    @Query("SELECT AVG(CAST(rating AS REAL)) FROM feedback")
    suspend fun getAverageRating(): Float?
}
