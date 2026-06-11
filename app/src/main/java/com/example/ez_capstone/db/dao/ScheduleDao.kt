package com.example.ez_capstone.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.ez_capstone.db.entity.ScheduleEntity

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY createdAt DESC")
    suspend fun getAll(): List<ScheduleEntity>

    @Query("SELECT * FROM schedules WHERE isActive = 1 ORDER BY createdAt DESC")
    suspend fun getActive(): List<ScheduleEntity>

    @Insert
    suspend fun insert(schedule: ScheduleEntity): Long

    @Update
    suspend fun update(schedule: ScheduleEntity)

    @Delete
    suspend fun delete(schedule: ScheduleEntity)

    @Query("UPDATE schedules SET isActive = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)
}
