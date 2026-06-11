package com.example.ez_capstone.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "route_history",
    indices = [Index(value = ["dayOfWeek", "departedAt"])]
)
data class RouteHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originName: String?,
    val originLat: Double,
    val originLng: Double,
    val destName: String?,
    val destLat: Double,
    val destLng: Double,
    val waypointsJson: String? = null,
    val distanceM: Int,
    val durationS: Int,
    val departedAt: Long,
    val arrivedAt: Long? = null,
    val dayOfWeek: Int,
)
