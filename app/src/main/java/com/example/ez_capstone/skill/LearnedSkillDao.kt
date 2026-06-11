package com.example.ez_capstone.skill

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * LearnedSkill DAO — L3 계층 캐시 조회/갱신.
 *
 * 주 사용 패턴:
 * - findByFingerprint: 정확 매칭 (hot path, <10ms)
 * - getCandidates: 유사도 계산 후보 조회 (50ms 내 끝내야 함)
 * - recordUsage/recordNegativeFeedback: 실행 후 통계 갱신
 * - purgeStale: SkillCompactionWorker에서 주기적 청소
 */
@Dao
interface LearnedSkillDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(skill: LearnedSkillEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(skills: List<LearnedSkillEntity>)

    @Query("SELECT * FROM learned_skills WHERE id = :id")
    suspend fun getById(id: String): LearnedSkillEntity?

    /** 정확 fingerprint 일치 (hot path) */
    @Query("SELECT * FROM learned_skills WHERE fingerprint = :fingerprint AND status = 'ACTIVE' LIMIT 1")
    suspend fun findByFingerprint(fingerprint: String): LearnedSkillEntity?

    /**
     * 유사도 계산 후보 — 토큰 길이·상태·최근 사용일 필터링.
     * SkillMatcher가 in-memory에서 Jaccard 스코어링 수행.
     */
    @Query("""
        SELECT * FROM learned_skills
        WHERE status IN ('ACTIVE', 'DEGRADED')
          AND lastUsedAt >= :sinceMs
          AND tokenCount BETWEEN :minTokens AND :maxTokens
        ORDER BY confidence DESC, lastUsedAt DESC
        LIMIT :limit
    """)
    suspend fun getCandidates(
        sinceMs: Long,
        minTokens: Int,
        maxTokens: Int,
        limit: Int = 50
    ): List<LearnedSkillEntity>

    /** UI 표시용 — 최근 사용순 */
    @Query("SELECT * FROM learned_skills WHERE status != 'AUTO_DISABLED' ORDER BY lastUsedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<LearnedSkillEntity>

    /** AgentMemoryScreen 전체 목록용 */
    @Query("SELECT * FROM learned_skills ORDER BY usageCount DESC")
    suspend fun getAll(): List<LearnedSkillEntity>

    @Query("SELECT COUNT(*) FROM learned_skills WHERE status != 'AUTO_DISABLED'")
    suspend fun countActive(): Int

    @Query("SELECT COUNT(*) FROM learned_skills")
    suspend fun countTotal(): Int

    /** 실행 후 성공/실패 + 지연시간 갱신 (avgLatency는 이동 평균) */
    @Query("""
        UPDATE learned_skills
        SET usageCount = usageCount + 1,
            successCount = successCount + :successDelta,
            lastUsedAt = :now,
            avgLatencyMs = CASE WHEN usageCount = 0 THEN :latencyMs
                                ELSE ((avgLatencyMs * usageCount) + :latencyMs) / (usageCount + 1) END,
            updatedAt = :now
        WHERE id = :id
    """)
    suspend fun recordUsage(id: String, successDelta: Int, latencyMs: Int, now: Long)

    @Query("UPDATE learned_skills SET negativeFeedbackCount = negativeFeedbackCount + 1, updatedAt = :now WHERE id = :id")
    suspend fun recordNegativeFeedback(id: String, now: Long)

    @Query("UPDATE learned_skills SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, now: Long)

    @Query("UPDATE learned_skills SET confidence = :confidence, updatedAt = :now WHERE id = :id")
    suspend fun updateConfidence(id: String, confidence: Float, now: Long)

    /** 결정 3: 고정(Pin) */
    @Query("UPDATE learned_skills SET pinnedUntil = :until, updatedAt = :now WHERE id = :id")
    suspend fun updatePin(id: String, until: Long?, now: Long)

    @Query("DELETE FROM learned_skills WHERE id = :id")
    suspend fun delete(id: String)

    /**
     * SkillCompactionWorker 주기 청소:
     * - pinnedUntil이 과거이거나 null
     * - Tier별 TTL 만료
     * - AUTO_DISABLED는 제외 (수동 복구 여지 남김)
     */
    @Query("""
        DELETE FROM learned_skills
        WHERE (pinnedUntil IS NULL OR pinnedUntil < :now)
          AND lastUsedAt < (:now - (ttlDays * 86400000))
          AND status != 'AUTO_DISABLED'
    """)
    suspend fun purgeStale(now: Long): Int

    /** 상한(500개) 초과 시 LRU 기반 pruning — SkillCompactionWorker에서 호출 */
    @Query("""
        DELETE FROM learned_skills
        WHERE id IN (
            SELECT id FROM learned_skills
            WHERE pinnedUntil IS NULL AND status != 'AUTO_DISABLED'
            ORDER BY (confidence * 0.5 + CAST(successCount AS REAL) / MAX(usageCount, 1) * 0.5) ASC,
                     lastUsedAt ASC
            LIMIT :overflow
        )
    """)
    suspend fun pruneLowestScore(overflow: Int): Int
}
