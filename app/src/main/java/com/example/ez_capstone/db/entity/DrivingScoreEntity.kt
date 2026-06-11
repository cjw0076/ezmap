package com.example.ez_capstone.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "driving_scores",
    indices = [Index("sessionId")]
)
data class DrivingScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val startedAt: Long,
    val endedAt: Long,
    val totalDistanceM: Int,
    val finalScore: Int,           // 0~100
    val speedingCount: Int,
    val hardBrakeCount: Int,
    val sharpTurnCount: Int,
    val speedingDeductions: Int,
    val hardBrakeDeductions: Int,
    val sharpTurnDeductions: Int,
    val routeSummary: String? = null   // JSON: origin→dest
)
