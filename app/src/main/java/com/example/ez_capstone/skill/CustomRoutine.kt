package com.example.ez_capstone.skill

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update

/**
 * 사용자 반복 패턴에서 생성된 커스텀 루틴.
 * "매주 금요일 퇴근할 때 이마트 경유 귀가" 같은 자동화.
 */
@Entity(tableName = "custom_routines")
data class CustomRoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                    // "금요 장보기"
    val triggerDayOfWeek: Int? = null,   // 0=일, 1=월, ..., 6=토 (null이면 매일)
    val triggerTimeStart: Int? = null,   // 분 단위 (예: 17*60=1020 = 17:00)
    val triggerTimeEnd: Int? = null,     // 분 단위 (예: 19*60=1140 = 19:00)
    val stepsJson: String,               // JSON: [{"tool":"get_directions","args":{...}}, ...]
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastTriggeredAt: Long? = null,
    val executionCount: Int = 0
)

@Dao
interface CustomRoutineDao {

    @Insert
    suspend fun insert(routine: CustomRoutineEntity): Long

    @Update
    suspend fun update(routine: CustomRoutineEntity)

    @Query("SELECT * FROM custom_routines WHERE isActive = 1 ORDER BY createdAt DESC")
    suspend fun getActive(): List<CustomRoutineEntity>

    @Query("SELECT * FROM custom_routines ORDER BY createdAt DESC")
    suspend fun getAll(): List<CustomRoutineEntity>

    @Query("SELECT * FROM custom_routines WHERE id = :id")
    suspend fun getById(id: Long): CustomRoutineEntity?

    @Query("DELETE FROM custom_routines WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE custom_routines SET isActive = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)

    @Query("UPDATE custom_routines SET lastTriggeredAt = :time, executionCount = executionCount + 1 WHERE id = :id")
    suspend fun markTriggered(id: Long, time: Long = System.currentTimeMillis())
}
