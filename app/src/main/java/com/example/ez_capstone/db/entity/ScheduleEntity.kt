package com.example.ez_capstone.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val date: String? = null,
    val time: String? = null,
    val cronExpression: String? = null,
    val destinationName: String? = null,
    val destinationLat: Double? = null,
    val destinationLng: Double? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)
