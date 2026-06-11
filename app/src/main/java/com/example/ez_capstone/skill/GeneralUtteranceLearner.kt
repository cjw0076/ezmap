package com.example.ez_capstone.skill

import android.util.Log
import com.example.ez_capstone.memory.MemoryTier
import com.example.ez_capstone.safety.SafetyPolicyEngine
import com.example.ez_capstone.trace.DecisionTraceDao
import com.example.ez_capstone.trace.DecisionTraceEntity
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * 일반 Gemini 경로 학습 엔진 (SELF_LEARNING_AGENT.md Phase B-3).
 *
 * DecisionTrace에서 반복 패턴을 집계해 LearnedSkill을 자동 생성.
 * Template 기반 즉시 승격과 달리 **보수적 가드레일** 적용:
 *
 *  1. **3회 반복** (Template 2회 면제 대비 엄격)
 *  2. **confidence 상한 0.70** — Safe 계층 임계 0.75 미만이므로 Gemini 우회 자동 차단,
 *     사용자가 Pin해야만 실제 실행 (결정 3)
 *  3. **결정적 toolChain 필수** — 3개 trace 모두 동일 tool 시퀀스
 *  4. **context 일치율** — 요일·시간대 편차 검사, 불일치 시 −0.15 페널티
 *  5. **EFFECTFUL Tool 포함 skill은 학습 금지** (결정 2)
 *
 * 결정 5(B-4 연기) 준수 — 임계는 조정하지 않음, 피드백율은 AgentAnalytics에만 기록.
 */
@Singleton
class GeneralUtteranceLearner @Inject constructor(
    private val decisionTraceDao: DecisionTraceDao,
    private val learnedSkillDao: LearnedSkillDao,
    private val skillMatcher: SkillMatcher,
    private val safetyPolicy: SafetyPolicyEngine
) {
    companion object {
        private const val TAG = "GeneralUtteranceLearner"

        /** 반복 임계 — 비평가 제안 3회 (원 설계 2회 상향) */
        private const val MIN_REPETITIONS = 3

        /** confidence 상한 — Pin 전까지 safe/stateful 계층 임계 미달로 Gemini 우회 자동 차단 */
        private const val BASE_CONFIDENCE = 0.65f

        /** context 불일치 페널티 */
        private const val CONTEXT_MISMATCH_PENALTY = 0.15f

        /** 저장 최저 기준. 이 미만이면 학습 자체 포기 */
        private const val MIN_STORE_CONFIDENCE = 0.50f

        /** 분석 윈도우 (7일) */
        private const val WINDOW_MS = 7L * 24 * 60 * 60 * 1000
    }

    /**
     * 한 번 실행. SkillLearningWorker가 매일 자정 호출.
     *
     * @return 생성된 LearnedSkill 개수
     */
    suspend fun runOnce(): Int {
        val now = System.currentTimeMillis()
        val traces = decisionTraceDao.getRecent(limit = 500).filter { it.createdAt >= now - WINDOW_MS }
        if (traces.size < MIN_REPETITIONS) {
            Log.d(TAG, "Not enough traces: ${traces.size} < $MIN_REPETITIONS")
            return 0
        }

        // (정규화 발화, toolsUsed 시그니처) 단위로 그룹화 — 결정적 체인만 집계
        data class GroupKey(val fingerprint: String, val toolsSig: String)
        val groups = HashMap<GroupKey, MutableList<DecisionTraceEntity>>()
        for (t in traces) {
            val normalized = skillMatcher.normalize(t.requestText)
            val toolsSig = t.toolsUsed.trim()
            if (normalized.isBlank() || toolsSig.isBlank()) continue
            val key = GroupKey(normalized, toolsSig)
            groups.getOrPut(key) { mutableListOf() }.add(t)
        }

        var created = 0
        for ((key, group) in groups) {
            if (group.size < MIN_REPETITIONS) continue

            // 이미 동일 fingerprint로 학습된 Skill 있으면 건너뛰기 (중복 방지)
            if (learnedSkillDao.findByFingerprint(key.fingerprint) != null) {
                Log.d(TAG, "Skip '${key.fingerprint}' — already learned")
                continue
            }

            val toolNames = key.toolsSig.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (toolNames.isEmpty()) continue

            // EFFECTFUL Tool 포함 시 학습 금지 (결정 2)
            if (toolNames.any { !safetyPolicy.isLearnable(it) }) {
                Log.d(TAG, "Skip '${key.fingerprint}' — has non-learnable tool")
                continue
            }

            // context 일치율 계산 → 페널티 결정
            val contextOk = isContextConsistent(group)
            val confidence = if (contextOk) BASE_CONFIDENCE else (BASE_CONFIDENCE - CONTEXT_MISMATCH_PENALTY)
            if (confidence < MIN_STORE_CONFIDENCE) {
                Log.d(TAG, "Skip '${key.fingerprint}' — confidence $confidence < min")
                continue
            }

            val tier = safetyPolicy.maxRiskTierOf(toolNames)
            val tokens = key.fingerprint.split(" ").filter { it.isNotBlank() }
            val toolChainJson = buildToolChainJson(toolNames)

            // sensitivity 기본 PUBLIC — 일반 학습은 개인정보 슬롯 없음
            // (슬롯 있는 경우는 Template 경로로만 학습, 거기서 Tier 결정)
            val sensitivity = MemoryTier.PUBLIC

            val entity = LearnedSkillEntity(
                id = UUID.randomUUID().toString(),
                fingerprint = key.fingerprint,
                canonicalUtterance = group.first().requestText,
                coreTokenSet = JSONArray(tokens).toString(),
                tokenCount = tokens.size,
                slotSchema = "[]",                          // 일반 학습은 슬롯 추출 보류 (Phase D)
                toolChain = toolChainJson,
                replyTemplate = null,                       // Gemini 원 응답 참고 어려움 → null
                ttsTemplate = null,
                confidence = confidence,                    // ≤ 0.65 → Gemini 우회 자동 차단
                usageCount = group.size,
                successCount = group.size,
                maxToolRiskTier = tier.code,
                confidenceThreshold = tier.confidenceThreshold,
                sensitivity = sensitivity.code,
                ttlDays = sensitivity.ttlDays,
                originTemplateId = null,
                originTemplateVersion = 1,
                representativeTraceIds = JSONArray(group.take(3).map { it.id.toString() }).toString(),
                status = "ACTIVE",
                createdAt = now,
                updatedAt = now,
                lastUsedAt = now
            )
            learnedSkillDao.upsert(entity)
            created++
            Log.d(TAG, "Learned '${key.fingerprint}' (confidence=$confidence, reps=${group.size}, contextOk=$contextOk)")
        }

        return created
    }

    /**
     * Trace 그룹의 시간/요일 일관성 검사.
     * - 시간대(hour): 모두 ±4시간 범위 안인가?
     * - 요일: 평일만/주말만/요일 고정 or 무관하게 분산?
     *
     * 둘 다 일관이면 true, 한쪽이라도 분산이 너무 크면 false (페널티 부여).
     */
    private fun isContextConsistent(group: List<DecisionTraceEntity>): Boolean {
        if (group.size < 2) return true

        val hours = group.map {
            Calendar.getInstance().apply { timeInMillis = it.createdAt }.get(Calendar.HOUR_OF_DAY)
        }
        val daysOfWeek = group.map {
            Calendar.getInstance().apply { timeInMillis = it.createdAt }.get(Calendar.DAY_OF_WEEK)
        }

        // 시간대 편차: 최대-최소 ≤ 4
        val hourSpread = (hours.max() - hours.min()) <= 4

        // 요일 분포: 모두 평일 or 모두 주말 or 요일 개수 ≤ 2면 집중
        val weekdaySet = daysOfWeek.toSet()
        val allWeekday = weekdaySet.all { it in Calendar.MONDAY..Calendar.FRIDAY }
        val allWeekend = weekdaySet.all { it == Calendar.SATURDAY || it == Calendar.SUNDAY }
        val daySpread = allWeekday || allWeekend || weekdaySet.size <= 2

        return hourSpread && daySpread
    }

    /**
     * toolNames 리스트를 toolChain JSON으로 변환. params는 빈 object로 초기화
     * (B-3 일반 학습은 슬롯 추출이 불완전하므로 파라미터 없이 기본 호출만 가능).
     *
     * Pin된 Skill이 실제 실행되려면 이 toolChain을 AgentContext 기반으로 SkillExecutor가
     * 보정 실행해야 함 (현재는 빈 params로 기본 동작 유도).
     */
    private fun buildToolChainJson(toolNames: List<String>): String {
        val arr = JSONArray()
        for (name in toolNames) {
            arr.put(JSONObject().apply {
                put("tool", name)
                put("params", JSONObject())
            })
        }
        return arr.toString()
    }
}
