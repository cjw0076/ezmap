package com.example.ez_capstone.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.ez_capstone.db.EZMapDatabase
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import com.example.ez_capstone.db.dao.*
import com.example.ez_capstone.skill.CustomRoutineDao
import com.example.ez_capstone.skill.LearnedSkillDao
import com.example.ez_capstone.trace.DecisionTraceDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** v1→v2: DecisionTrace 테이블 추가 */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS decision_traces (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    sessionId TEXT NOT NULL,
                    requestText TEXT NOT NULL,
                    stepsJson TEXT NOT NULL,
                    totalDurationMs INTEGER NOT NULL,
                    iterationCount INTEGER NOT NULL,
                    toolsUsed TEXT NOT NULL,
                    safetyDecision TEXT,
                    createdAt INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }

    /** v2→v3: CustomRoutine 테이블 추가 */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS custom_routines (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    triggerDayOfWeek INTEGER,
                    triggerTimeStart INTEGER,
                    triggerTimeEnd INTEGER,
                    stepsJson TEXT NOT NULL,
                    isActive INTEGER NOT NULL DEFAULT 1,
                    createdAt INTEGER NOT NULL,
                    lastTriggeredAt INTEGER,
                    executionCount INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
        }
    }

    /** v4→v5: driving_scores 테이블 추가 + ProfileEntity EV 컬럼 추가 */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS driving_scores (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    sessionId TEXT NOT NULL,
                    startedAt INTEGER NOT NULL,
                    endedAt INTEGER NOT NULL,
                    totalDistanceM INTEGER NOT NULL,
                    finalScore INTEGER NOT NULL,
                    speedingCount INTEGER NOT NULL,
                    hardBrakeCount INTEGER NOT NULL,
                    sharpTurnCount INTEGER NOT NULL,
                    speedingDeductions INTEGER NOT NULL,
                    hardBrakeDeductions INTEGER NOT NULL,
                    sharpTurnDeductions INTEGER NOT NULL,
                    routeSummary TEXT
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_driving_scores_sessionId ON driving_scores(sessionId)")
            // ProfileEntity EV 컬럼 추가
            db.execSQL("ALTER TABLE profile ADD COLUMN batteryCapacityKwh REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE profile ADD COLUMN currentBatteryPct INTEGER NOT NULL DEFAULT 80")
            db.execSQL("ALTER TABLE profile ADD COLUMN chargingSpeedKw REAL NOT NULL DEFAULT 50")
        }
    }

    /** v5→v6: energy_history 테이블 추가 */
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS energy_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    sessionId TEXT NOT NULL,
                    recordedAt INTEGER NOT NULL,
                    startBatteryPct INTEGER NOT NULL,
                    endBatteryPct INTEGER NOT NULL,
                    distanceM INTEGER NOT NULL,
                    consumedKwh REAL NOT NULL,
                    regenKwh REAL NOT NULL,
                    avgKwhPerKm REAL NOT NULL,
                    isHighway INTEGER NOT NULL,
                    temperatureCelsius INTEGER
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_energy_history_sessionId ON energy_history(sessionId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_energy_history_recordedAt ON energy_history(recordedAt)")
        }
    }

    /** v6→v7: feedback 테이블 추가 */
    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS feedback (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    rating INTEGER NOT NULL,
                    comment TEXT,
                    createdAt INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }

    /** v7→v8: 스키마 변경 없음 — 경로 연결용 */
    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 스키마 변경 없음 — 경로 연결용
        }
    }

    /** v8→v9: 스키마 변경 없음 — 경로 연결용 */
    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 스키마 변경 없음 — 경로 연결용
        }
    }

    /** v9→v10: 스키마 변경 없음 — 경로 연결용 */
    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 스키마 변경 없음 — 경로 연결용
        }
    }

    /** v10→v11: learned_skills 테이블 추가 (자기학습 에이전트 시스템) */
    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS learned_skills (
                    id TEXT PRIMARY KEY NOT NULL,
                    fingerprint TEXT NOT NULL,
                    canonicalUtterance TEXT NOT NULL,
                    coreTokenSet TEXT NOT NULL,
                    tokenCount INTEGER NOT NULL,
                    slotSchema TEXT NOT NULL,
                    toolChain TEXT NOT NULL,
                    replyTemplate TEXT,
                    ttsTemplate TEXT,
                    confidence REAL NOT NULL,
                    usageCount INTEGER NOT NULL DEFAULT 0,
                    successCount INTEGER NOT NULL DEFAULT 0,
                    negativeFeedbackCount INTEGER NOT NULL DEFAULT 0,
                    avgLatencyMs INTEGER NOT NULL DEFAULT 0,
                    contextDistribution TEXT NOT NULL DEFAULT '{}',
                    maxToolRiskTier TEXT NOT NULL DEFAULT 'SAFE',
                    confidenceThreshold REAL NOT NULL DEFAULT 0.75,
                    pinnedUntil INTEGER,
                    originTemplateId TEXT,
                    originTemplateVersion INTEGER NOT NULL DEFAULT 1,
                    representativeTraceIds TEXT NOT NULL DEFAULT '[]',
                    sensitivity TEXT NOT NULL DEFAULT 'PUBLIC',
                    ttlDays INTEGER NOT NULL DEFAULT 30,
                    status TEXT NOT NULL DEFAULT 'ACTIVE',
                    schemaVersion INTEGER NOT NULL DEFAULT 1,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    lastUsedAt INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_learned_fingerprint_status ON learned_skills(fingerprint, status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_learned_lastused_status ON learned_skills(lastUsedAt, status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_learned_confidence ON learned_skills(confidence)")
        }
    }

    /** v11→v12: LearnedSkillEntity embedding BLOB 컬럼 추가 (Phase 10 벡터 RAG) */
    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE learned_skills ADD COLUMN embedding BLOB")
        }
    }

    /** v3→v4: 쿼리 성능을 위한 인덱스 추가 */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_conversations_sessionId_createdAt " +
                "ON conversations(sessionId, createdAt)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_route_history_dayOfWeek_departedAt " +
                "ON route_history(dayOfWeek, departedAt)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_decision_traces_sessionId_createdAt " +
                "ON decision_traces(sessionId, createdAt)"
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EZMapDatabase {
        val passphrase = getOrCreateDbPassphrase(context)
        val factory = SupportFactory(SQLiteDatabase.getBytes(passphrase.toCharArray()))
        return Room.databaseBuilder(context, EZMapDatabase::class.java, "ezmap.db")
            .openHelperFactory(factory)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
            .build()
    }

    /**
     * DB 패스프레이즈를 EncryptedSharedPreferences에서 불러오거나,
     * 없으면 SecureRandom으로 생성 후 저장.
     */
    private fun getOrCreateDbPassphrase(context: Context): String {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = EncryptedSharedPreferences.create(
            context, "ezmap_db_key", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        return prefs.getString("db_passphrase", null) ?: run {
            val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val key = Base64.getEncoder().encodeToString(bytes)
            prefs.edit().putString("db_passphrase", key).apply()
            key
        }
    }

    @Provides fun provideProfileDao(db: EZMapDatabase): ProfileDao = db.profileDao()
    @Provides fun providePreferenceDao(db: EZMapDatabase): PreferenceDao = db.preferenceDao()
    @Provides fun provideRouteHistoryDao(db: EZMapDatabase): RouteHistoryDao = db.routeHistoryDao()
    @Provides fun provideConversationDao(db: EZMapDatabase): ConversationDao = db.conversationDao()
    @Provides fun provideFrequentPlaceDao(db: EZMapDatabase): FrequentPlaceDao = db.frequentPlaceDao()
    @Provides fun provideContactDao(db: EZMapDatabase): ContactDao = db.contactDao()
    @Provides fun provideScheduleDao(db: EZMapDatabase): ScheduleDao = db.scheduleDao()
    @Provides fun provideDecisionTraceDao(db: EZMapDatabase): DecisionTraceDao = db.decisionTraceDao()
    @Provides fun provideCustomRoutineDao(db: EZMapDatabase): CustomRoutineDao = db.customRoutineDao()
    @Provides fun provideDrivingScoreDao(db: EZMapDatabase): DrivingScoreDao = db.drivingScoreDao()
    @Provides fun provideEnergyHistoryDao(db: EZMapDatabase): EnergyHistoryDao = db.energyHistoryDao()
    @Provides fun provideFeedbackDao(db: EZMapDatabase): FeedbackDao = db.feedbackDao()
    @Provides fun provideAgentNoteDao(db: EZMapDatabase): AgentNoteDao = db.agentNoteDao()
    @Provides fun provideLearnedSkillDao(db: EZMapDatabase): LearnedSkillDao = db.learnedSkillDao()
}
