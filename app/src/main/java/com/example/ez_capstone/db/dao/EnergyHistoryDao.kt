package com.example.ez_capstone.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.ez_capstone.db.entity.EnergyHistoryEntity

@Dao
interface EnergyHistoryDao {
    @Insert
    suspend fun insert(entry: EnergyHistoryEntity): Long

    @Query("SELECT * FROM energy_history ORDER BY recordedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<EnergyHistoryEntity>

    @Query("SELECT AVG(avgKwhPerKm) FROM energy_history WHERE isHighway = 1")
    suspend fun getAvgHighwayEfficiency(): Float?

    @Query("SELECT AVG(avgKwhPerKm) FROM energy_history WHERE isHighway = 0")
    suspend fun getAvgCityEfficiency(): Float?

    @Query("SELECT SUM(consumedKwh) FROM energy_history WHERE recordedAt >= :sinceMs")
    suspend fun getTotalConsumedSince(sinceMs: Long): Float?
}
