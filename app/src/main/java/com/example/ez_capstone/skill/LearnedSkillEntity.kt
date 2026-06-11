package com.example.ez_capstone.skill

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 학습된 Skill — Gemini가 처리한 쿼리 + tool chain을 재사용 가능한 실행 단위로 저장.
 *
 * 설계 문서: document/spec/SELF_LEARNING_AGENT.md
 *
 * 필드 그룹:
 * - 핵심 매칭: fingerprint, canonicalUtterance, coreTokenSet, tokenCount
 * - 실행: slotSchema, toolChain, replyTemplate, ttsTemplate
 * - 학습 통계: confidence, usageCount, successCount, negativeFeedbackCount, avgLatencyMs
 * - 결정 2(계층 임계): maxToolRiskTier, confidenceThreshold
 * - 결정 3(사용자 통제): pinnedUntil
 * - 결정 4(템플릿): originTemplateId, originTemplateVersion
 * - R3(설명 가능성): representativeTraceIds
 * - R6(메모리 정책): sensitivity, ttlDays
 * - R4(생명주기): status (SkillLifecycleManager 상태와 일치)
 *
 * JSON 필드는 String으로 저장 (TypeConverter 회피, 유연성 확보).
 */
@Entity(
    tableName = "learned_skills",
    indices = [
        // fingerprint 매칭 — 정확 일치 쿼리 고속화
        Index(value = ["fingerprint", "status"], name = "idx_learned_fingerprint_status"),
        // 최근 사용일 + status 필터 — 후보 범위 축소
        Index(value = ["lastUsedAt", "status"], name = "idx_learned_lastused_status"),
        // confidence 임계 필터
        Index(value = ["confidence"], name = "idx_learned_confidence")
    ]
)
data class LearnedSkillEntity(
    @PrimaryKey val id: String,                      // UUID

    // ── 핵심 매칭 ──
    val fingerprint: String,                         // 정규화된 패턴, 예: "{POI} 들렀다 {PLACE_REF}"
    val canonicalUtterance: String,                  // 원본 대표 발화 (UI 표시·디버그용)
    val coreTokenSet: String,                        // JSON 배열, 슬롯 제외 핵심 토큰
    val tokenCount: Int,                             // 프리필터용 (유사 길이 후보만)

    // ── 실행 ──
    val slotSchema: String,                          // JSON: [{name, type}]
    val toolChain: String,                           // JSON: [{tool, params}]
    val replyTemplate: String? = null,
    val ttsTemplate: String? = null,

    // ── 학습 통계 ──
    val confidence: Float,                           // 0.0~1.0
    val usageCount: Int = 0,
    val successCount: Int = 0,
    val negativeFeedbackCount: Int = 0,
    val avgLatencyMs: Int = 0,
    val contextDistribution: String = "{}",          // JSON: {hour, dayOfWeek, homeProximity}

    // ── 결정 2: 계층 임계 캐싱 ──
    val maxToolRiskTier: String = "SAFE",            // SAFE / STATEFUL / EFFECTFUL
    val confidenceThreshold: Float = 0.75f,

    // ── 결정 3: 사용자 통제 ──
    val pinnedUntil: Long? = null,                   // 고정 만료 시각. null=고정 해제

    // ── 결정 4: 템플릿 역추적 ──
    val originTemplateId: String? = null,
    val originTemplateVersion: Int = 1,

    // ── R3 설명 가능성 ──
    val representativeTraceIds: String = "[]",       // JSON: 최근 3개 DecisionTrace ID

    // ── R6 메모리 정책 ──
    val sensitivity: String = "PUBLIC",              // MemoryTier.code: PUBLIC / PERSONAL / SENSITIVE
    val ttlDays: Int = 30,

    // ── R4 생명주기 (SkillLifecycleManager 상태와 일치) ──
    val status: String = "ACTIVE",                   // ACTIVE/DEGRADED/FAILING/AUTO_DISABLED/KEY_MISSING

    // ── Phase 10: 벡터 임베딩 ──
    val embedding: ByteArray? = null,                // 512-dim float32 BLOB (EmbeddingEngine 출력)

    // ── 메타 ──
    val schemaVersion: Int = 1,                      // slotSchema 확장용 forward-compat
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis()
)
