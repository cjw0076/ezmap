package com.example.ez_capstone.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.ez_capstone.db.entity.ConversationEntity

@Dao
interface ConversationDao {
    @Insert
    suspend fun insertMessage(message: ConversationEntity): Long

    @Query("SELECT * FROM conversations WHERE sessionId = :sessionId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getSession(sessionId: String, limit: Int = 20): List<ConversationEntity>

    @Query("SELECT DISTINCT sessionId FROM conversations ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getSessionIds(limit: Int = 50): List<String>

    @Query("DELETE FROM conversations WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("SELECT COUNT(*) FROM conversations WHERE role = 'user'")
    suspend fun getTotalUserMessageCount(): Int

    @Query("SELECT * FROM conversations ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int = 10): List<ConversationEntity>
}
