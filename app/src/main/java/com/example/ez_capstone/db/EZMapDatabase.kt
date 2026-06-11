package com.example.ez_capstone.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.ez_capstone.db.dao.*
import com.example.ez_capstone.db.entity.*
import com.example.ez_capstone.skill.CustomRoutineDao
import com.example.ez_capstone.skill.CustomRoutineEntity
import com.example.ez_capstone.skill.LearnedSkillDao
import com.example.ez_capstone.skill.LearnedSkillEntity
import com.example.ez_capstone.trace.DecisionTraceDao
import com.example.ez_capstone.trace.DecisionTraceEntity

@Database(
    entities = [
        ProfileEntity::class,
        PreferenceEntity::class,
        RouteHistoryEntity::class,
        ConversationEntity::class,
        FrequentPlaceEntity::class,
        ContactEntity::class,
        ScheduleEntity::class,
        DecisionTraceEntity::class,
        CustomRoutineEntity::class,
        DrivingScoreEntity::class,
        EnergyHistoryEntity::class,
        FeedbackEntity::class,
        AgentNoteEntity::class,
        LearnedSkillEntity::class,
    ],
    version = 12,
    exportSchema = false
)
abstract class EZMapDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun preferenceDao(): PreferenceDao
    abstract fun routeHistoryDao(): RouteHistoryDao
    abstract fun conversationDao(): ConversationDao
    abstract fun frequentPlaceDao(): FrequentPlaceDao
    abstract fun contactDao(): ContactDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun decisionTraceDao(): DecisionTraceDao
    abstract fun customRoutineDao(): CustomRoutineDao
    abstract fun drivingScoreDao(): DrivingScoreDao
    abstract fun energyHistoryDao(): EnergyHistoryDao
    abstract fun feedbackDao(): FeedbackDao
    abstract fun agentNoteDao(): AgentNoteDao
    abstract fun learnedSkillDao(): LearnedSkillDao
}
