package com.example.ez_capstone.predictive

data class ContextSnapshot(
    val hourOfDay: Int,       // 0-23
    val dayOfWeek: Int,       // Calendar.MONDAY=2 … Calendar.SUNDAY=1
    val isCharging: Boolean,
    val isWifi: Boolean,
    val latitude: Double,
    val longitude: Double
)
