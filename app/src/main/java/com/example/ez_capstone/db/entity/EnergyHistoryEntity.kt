package com.example.ez_capstone.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "energy_history",
    indices = [Index("sessionId"), Index("recordedAt")]
)
data class EnergyHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val recordedAt: Long,
    val startBatteryPct: Int,
    val endBatteryPct: Int,
    val distanceM: Int,
    val consumedKwh: Float,
    val regenKwh: Float,           // 회생제동 회수량
    val avgKwhPerKm: Float,
    val isHighway: Boolean,
    val temperatureCelsius: Int? = null
)
