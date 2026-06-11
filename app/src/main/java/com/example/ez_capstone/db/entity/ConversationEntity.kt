package com.example.ez_capstone.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversations",
    indices = [Index(value = ["sessionId", "createdAt"])]
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val role: String,
    val text: String,
    val uiAction: String? = null,
    val toolsUsed: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
