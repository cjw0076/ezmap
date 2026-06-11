package com.example.ez_capstone.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feedback")
data class FeedbackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rating: Int,             // 1-5
    val comment: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
