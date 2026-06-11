package com.example.ez_capstone.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "frequent_places")
data class FrequentPlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val address: String? = null,
    val lat: Double,
    val lng: Double,
    val visitCount: Int = 1,
    val lastVisited: Long = System.currentTimeMillis(),
    val avgVisitHour: Float? = null,
)
