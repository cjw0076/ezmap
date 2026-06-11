package com.example.ez_capstone.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val homeAddress: String? = null,
    val homeLat: Double? = null,
    val homeLng: Double? = null,
    val workAddress: String? = null,
    val workLat: Double? = null,
    val workLng: Double? = null,
    val vehicleType: String = "sedan",
    val fuelType: String = "gasoline",
    val hasHipass: Boolean = false,
    val ttsStyle: String = "polite",
    val detailLevel: String = "normal",
    val savedPlaces: String? = null,   // JSON array
    // Phase 7: EV 배터리 필드
    val batteryCapacityKwh: Float = 0f,
    val currentBatteryPct: Int = 80,
    val chargingSpeedKw: Float = 50f
)
