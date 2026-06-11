package com.example.ez_capstone.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.ez_capstone.db.entity.RouteHistoryEntity

@Dao
interface RouteHistoryDao {
    @Insert
    suspend fun insert(route: RouteHistoryEntity): Long

    @Query("SELECT * FROM route_history ORDER BY departedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 20): List<RouteHistoryEntity>

    @Query("SELECT * FROM route_history WHERE dayOfWeek = :dayOfWeek ORDER BY departedAt DESC")
    suspend fun getByDayOfWeek(dayOfWeek: Int): List<RouteHistoryEntity>

    @Query("UPDATE route_history SET arrivedAt = :arrivedAt WHERE id = :id")
    suspend fun markArrived(id: Long, arrivedAt: Long = System.currentTimeMillis())
}
