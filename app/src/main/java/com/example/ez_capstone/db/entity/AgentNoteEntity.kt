package com.example.ez_capstone.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agent_notes")
data class AgentNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,        // "preference", "pattern", "fact", "reminder"
    val content: String,         // 자유 형식 메모
    val source: String? = null,  // 학습 출처 (사용자 발화 요약)
    val confidence: Float = 0.8f,
    val useCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
