package com.example.ez_capstone.memory

import android.util.Log
import com.example.ez_capstone.db.dao.ConversationDao
import com.example.ez_capstone.db.dao.PreferenceDao
import com.example.ez_capstone.trace.DecisionTraceDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 메모리 민감도 Tier (discrimination.md R6 구현).
 * LearnedSkill, AgentNote 등 학습 데이터의 TTL 차등 적용 기준.
 *
 * - PUBLIC: 개인정보 없음 ("스타벅스 찾아줘"). 긴 TTL.
 * - PERSONAL: 자주 가는 장소·시간 패턴. 중간 TTL.
 * - SENSITIVE: 집 주소·병원·종교시설·가족. 짧은 TTL + 명시적 확인.
 */
enum class MemoryTier(val code: String, val ttlDays: Int) {
    PUBLIC("PUBLIC", 30),
    PERSONAL("PERSONAL", 90),
    SENSITIVE("SENSITIVE", 14);

    companion object {
        fun fromCode(code: String?): MemoryTier = values().firstOrNull { it.code == code } ?: PUBLIC
    }
}

/**
 * 메모리 거버넌스: 기억의 수명 관리.
 * - 대화 로그: 30일 후 자동 삭제
 * - Decision Trace: 7일 후 자동 삭제
 * - 선호도: 수동 삭제만 (장기 기억)
 * - LearnedSkill: MemoryTier별 차등 TTL (PUBLIC 30d / PERSONAL 90d / SENSITIVE 14d)
 *
 * WorkManager에서 매일 자정 purgeExpired() 호출.
 */
@Singleton
class MemoryGovernor @Inject constructor(
    private val conversationDao: ConversationDao,
    private val preferenceDao: PreferenceDao,
    private val decisionTraceDao: DecisionTraceDao
) {
    companion object {
        private const val TAG = "MemoryGovernor"
        private const val DAY_MS = 86_400_000L
        const val CONVERSATION_RETENTION_DAYS = 30L
        const val TRACE_RETENTION_DAYS = 7L
    }

    /**
     * 만료된 기억 정리. WorkManager에서 주기적으로 호출.
     * @return 삭제된 항목 수 (대화 세션 + trace)
     */
    suspend fun purgeExpired(): Int {
        var deleted = 0
        val now = System.currentTimeMillis()

        try {
            // 대화 로그: 30일 이전 세션 삭제
            val cutoff = now - (CONVERSATION_RETENTION_DAYS * DAY_MS)
            val oldSessions = conversationDao.getSessionIds(limit = 200)
            for (sessionId in oldSessions) {
                val messages = conversationDao.getSession(sessionId, 200)
                val oldest = messages.minByOrNull { it.createdAt }
                if (oldest != null && oldest.createdAt < cutoff) {
                    conversationDao.deleteSession(sessionId)
                    deleted++
                }
            }

            // Decision Trace: 7일 이전 삭제
            val traceCutoff = now - (TRACE_RETENTION_DAYS * DAY_MS)
            decisionTraceDao.deleteOlderThan(traceCutoff)

            Log.d(TAG, "purgeExpired: deleted $deleted old sessions, traces older than ${TRACE_RETENTION_DAYS}d")
        } catch (e: Exception) {
            Log.e(TAG, "purgeExpired failed: ${e.message}")
        }

        return deleted
    }

    /**
     * 메모리 통계 (Settings UI용).
     */
    suspend fun getStats(): MemoryStats {
        return try {
            val sessionCount = conversationDao.getSessionIds(200).size
            val preferenceCount = preferenceDao.getAll().size
            val traceCount = decisionTraceDao.count()

            MemoryStats(
                conversationSessions = sessionCount,
                preferences = preferenceCount,
                decisionTraces = traceCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "getStats failed: ${e.message}")
            MemoryStats()
        }
    }

    /**
     * 특정 카테고리의 선호도 전체 삭제.
     */
    suspend fun clearPreferenceCategory(category: String) {
        try {
            val prefs = preferenceDao.getByCategory(category)
            for (pref in prefs) {
                preferenceDao.delete(pref.category, pref.key)
            }
            Log.d(TAG, "Cleared $category preferences: ${prefs.size} items")
        } catch (e: Exception) {
            Log.e(TAG, "clearPreferenceCategory failed: ${e.message}")
        }
    }

    /**
     * 전체 기억 초기화 (공장 초기화).
     */
    suspend fun purgeAll() {
        try {
            val sessions = conversationDao.getSessionIds(1000)
            sessions.forEach { conversationDao.deleteSession(it) }

            val prefs = preferenceDao.getAll()
            prefs.forEach { preferenceDao.delete(it.category, it.key) }

            decisionTraceDao.deleteOlderThan(System.currentTimeMillis() + 1)

            Log.d(TAG, "purgeAll: all memory cleared")
        } catch (e: Exception) {
            Log.e(TAG, "purgeAll failed: ${e.message}")
        }
    }

    data class MemoryStats(
        val conversationSessions: Int = 0,
        val preferences: Int = 0,
        val decisionTraces: Int = 0
    )
}
