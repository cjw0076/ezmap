package com.example.ez_capstone.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.example.ez_capstone.db.entity.FrequentPlaceEntity

@Dao
interface FrequentPlaceDao {
    @Query("SELECT * FROM frequent_places ORDER BY visitCount DESC LIMIT :limit")
    suspend fun getTopPlaces(limit: Int = 10): List<FrequentPlaceEntity>

    @Upsert
    suspend fun upsert(place: FrequentPlaceEntity)

    @Query("SELECT * FROM frequent_places WHERE name LIKE '%' || :name || '%' LIMIT 5")
    suspend fun findByName(name: String): List<FrequentPlaceEntity>

    @Query("UPDATE frequent_places SET visitCount = visitCount + 1, lastVisited = :now WHERE id = :id")
    suspend fun incrementVisit(id: Long, now: Long = System.currentTimeMillis())

    @Delete
    suspend fun delete(place: FrequentPlaceEntity)
}
