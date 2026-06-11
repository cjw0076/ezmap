package com.example.ez_capstone.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.ez_capstone.db.entity.ContactEntity

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY name ASC")
    suspend fun getAll(): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE name LIKE '%' || :name || '%'")
    suspend fun findByName(name: String): List<ContactEntity>

    @Insert
    suspend fun insert(contact: ContactEntity): Long

    @Update
    suspend fun update(contact: ContactEntity)

    @Delete
    suspend fun delete(contact: ContactEntity)
}
