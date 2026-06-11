package com.example.ez_capstone.skill

import android.util.Log
import com.example.ez_capstone.memory.MemoryTier
import com.example.ez_capstone.safety.SafetyPolicyEngine
import com.example.ez_capstone.safety.ToolRiskTier
import org.json.JSONArray
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DecisionTrace + 템플릿 매칭 결과를 분석하여 LearnedSkill 생성/갱신.
 *
 * Phase A 책임:
 * 1. Template 매칭 성공 → 즉시 LearnedSkill로 승격 (2회 반복 조건 면제, 결정 4)
 * 2. Gemini 일반 응답 → 반복 패턴 누적 (SkillCompactionWorker에서 2회+ 시 LearnedSkill 생성)
 *
 * Phase B 확장 예정:
 * - DecisionTrace의 복합 tool chain 자동 추출 + fingerprint 생성
 * - Gemini 응답 분석으로 slot 자동 인식
 */
@Singleton
class SkillLearner @Inject constructor(
    private val dao: LearnedSkillDao,
    private val matcher: SkillMatcher,
    private val safetyPolicy: SafetyPolicyEngine,
    private val vectorStore: com.example.ez_capstone.offline.VectorStore
) {
    companion object {
        private const val TAG = "SkillLearner"
    }

    /**
     * Template 기반 즉시 승격.
     * 결정 4(스켈레톤 템플릿): 첫 매칭 시 2회 반복 조건 면제하여 바로 LearnedSkill 생성.
     *
     * 이미 같은 originTemplateId + slot signature로 학습된 Skill이 있으면 usageCount만 갱신.
     *
     * @return 생성/갱신된 LearnedSkill id, 또는 null (EFFECTFUL 포함 시 차단)
     */
    suspend fun promoteFromTemplate(
        userText: String,
        match: SkillMatchResult,
        decisionTraceId: String?
    ): String? {
        if (match.source != SkillMatchResult.Source.TEMPLATE) return null
        val t = match.template ?: return null

        // EFFECTFUL Tool 포함 시 학습 금지 (결정 2)
        val toolNames = t.toolChain.map { it.tool }
        if (toolNames.any { !safetyPolicy.isLearnable(it) }) {
            Log.d(TAG, "Template [${t.id}] has non-learnable tool — skipping promotion")
            return null
        }

        // 동일 originTemplateId로 이미 학습된 Skill 있는지 검색 (fingerprint 일치로 대체)
        val existing = dao.findByFingerprint(t.fingerprint)
        val now = System.currentTimeMillis()

        if (existing != null && existing.originTemplateId == t.id) {
            // 이미 존재 — usageCount만 +1
            dao.recordUsage(existing.id, successDelta = 1, latencyMs = 0, now = now)
            Log.d(TAG, "Template [${t.id}] already promoted as ${existing.id}, usage++")
            return existing.id
        }

        // 신규 승격
        val riskTier = safetyPolicy.maxRiskTierOf(toolNames)
        val sensitivity = MemoryTier.fromCode(t.sensitivity)
        val normalized = matcher.normalize(userText)
        val tokens = matcher.tokenize(normalized)
        val slotValues = match.slotValues.values.toSet()
        val coreTokens = tokens.filter { it !in slotValues }

        val entity = LearnedSkillEntity(
            id = UUID.randomUUID().toString(),
            fingerprint = t.fingerprint,                          // 템플릿 fingerprint 그대로 사용
            canonicalUtterance = userText,
            coreTokenSet = JSONArray(coreTokens).toString(),
            tokenCount = tokens.size,
            slotSchema = t.slotSchemaJson(),
            toolChain = t.toolChainJson(),
            replyTemplate = t.replyTemplate,
            ttsTemplate = t.ttsTemplate,
            confidence = t.initialConfidence,
            usageCount = 1,
            successCount = 1,
            maxToolRiskTier = riskTier.code,
            confidenceThreshold = riskTier.confidenceThreshold,
            sensitivity = sensitivity.code,
            ttlDays = sensitivity.ttlDays,
            originTemplateId = t.id,
            originTemplateVersion = t.version,
            representativeTraceIds = if (decisionTraceId != null) JSONArray().put(decisionTraceId).toString() else "[]",
            status = "ACTIVE",
            createdAt = now,
            updatedAt = now,
            lastUsedAt = now
        )
        val embeddingBlob = vectorStore.embeddingBytes(entity.fingerprint)
        dao.upsert(entity.copy(embedding = embeddingBlob))
        vectorStore.upsert(entity.id, entity.fingerprint)
        Log.d(TAG, "Template [${t.id}] promoted → LearnedSkill ${entity.id} (confidence=${entity.confidence})")
        return entity.id
    }

    /**
     * 일반 발화 관찰. Phase A에서는 기록만 하고 승격하지 않음.
     * SkillCompactionWorker가 유휴 시간에 반복 패턴을 집계하여 LearnedSkill 생성 (Phase B).
     *
     * 현재는 no-op. Hook으로만 유지.
     */
    suspend fun observeUtterance(userText: String, toolsUsed: List<String>, decisionTraceId: String?) {
        // TODO(Phase B): 동일 정규화 발화 + 동일 toolChain 2회+ 반복 시 LearnedSkill 생성
        // 현재는 Template 매칭 경로만 학습하여 안전하게 Phase A 완료
    }

    /**
     * 부정 피드백 처리 (사용자가 "아니야" 또는 실행 취소).
     * SkillExecutor.handleFailure()도 유사 로직을 수행하므로 여기는 명시적 피드백 전용.
     */
    suspend fun recordNegativeFeedback(skillId: String) {
        val now = System.currentTimeMillis()
        dao.recordNegativeFeedback(skillId, now)
        val refreshed = dao.getById(skillId) ?: return
        val newStatus = when {
            refreshed.negativeFeedbackCount >= 5 -> "AUTO_DISABLED"
            refreshed.negativeFeedbackCount >= 3 -> "DEGRADED"
            else -> refreshed.status
        }
        if (newStatus != refreshed.status) {
            dao.updateStatus(skillId, newStatus, now)
            Log.d(TAG, "Skill $skillId → $newStatus (explicit negative feedback)")
        }
    }
}
