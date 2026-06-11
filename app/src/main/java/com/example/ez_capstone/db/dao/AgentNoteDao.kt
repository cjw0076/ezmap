package com.example.ez_capstone.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.ez_capstone.db.entity.AgentNoteEntity

@Dao
interface AgentNoteDao {
    @Query("SELECT * FROM agent_notes ORDER BY updatedAt DESC")
    suspend fun getAll(): List<AgentNoteEntity>

    @Query("SELECT * FROM agent_notes WHERE category = :category ORDER BY confidence DESC")
    suspend fun getByCategory(category: String): List<AgentNoteEntity>

    @Query("SELECT * FROM agent_notes ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 20): List<AgentNoteEntity>

    @Query("SELECT * FROM agent_notes WHERE content LIKE '%' || :query || '%' ORDER BY confidence DESC LIMIT 10")
    suspend fun search(query: String): List<AgentNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: AgentNoteEntity): Long

    @Update
    suspend fun update(note: AgentNoteEntity)

    @Delete
    suspend fun delete(note: AgentNoteEntity)

    @Query("DELETE FROM agent_notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE agent_notes SET useCount = useCount + 1 WHERE id = :id")
    suspend fun incrementUseCount(id: Long)

    @Query("SELECT COUNT(*) FROM agent_notes")
    suspend fun count(): Int
}
