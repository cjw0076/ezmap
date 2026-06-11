package com.example.ez_capstone.db.entity

import androidx.room.Entity

@Entity(tableName = "preferences", primaryKeys = ["category", "key"])
data class PreferenceEntity(
    val category: String,
    val key: String,
    val value: Float,
    val updatedAt: Long = System.currentTimeMillis(),
)
