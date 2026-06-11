package com.example.ez_capstone.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.ez_capstone.db.entity.PreferenceEntity

@Dao
interface PreferenceDao {
    @Query("SELECT * FROM preferences")
    suspend fun getAll(): List<PreferenceEntity>

    @Query("SELECT * FROM preferences WHERE category = :category")
    suspend fun getByCategory(category: String): List<PreferenceEntity>

    @Upsert
    suspend fun upsert(preference: PreferenceEntity)

    @Query("DELETE FROM preferences WHERE category = :category AND `key` = :key")
    suspend fun delete(category: String, key: String)
}
