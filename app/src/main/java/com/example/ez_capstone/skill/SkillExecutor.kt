package com.example.ez_capstone.skill

import android.util.Log
import com.example.ez_capstone.agent.AgentPlan
import com.example.ez_capstone.agent.AgentPlanSource
import com.example.ez_capstone.agent.AgentPlanStep
import com.example.ez_capstone.agent.AgentResponseAdapter
import com.example.ez_capstone.agent.AgentRunRecorder
import com.example.ez_capstone.agent.AgentToolExecutionHarness
import com.example.ez_capstone.agent.FallbackStrategy
import com.example.ez_capstone.agent.ToolRegistry
import com.example.ez_capstone.agent.ToolExecutor
import com.example.ez_capstone.agent.models.AgentContext
import com.example.ez_capstone.agent.models.AgentResponse
import com.example.ez_capstone.analytics.AgentAnalytics
import com.example.ez_capstone.db.dao.ProfileDao
import com.example.ez_capstone.governance.AgentCapability
import com.example.ez_capstone.governance.PermissionManager
import com.example.ez_capstone.governance.PermissionResult
import com.example.ez_capstone.db.entity.ProfileEntity
import com.example.ez_capstone.safety.SafetyPolicyEngine
import com.example.ez_capstone.trace.DecisionTraceBuilder
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

class PlanCacheFallbackException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * L3 실행기 — 매칭된 LearnedSkill/Template의 toolChain을 실제 ToolExecutor 호출로 실행.
 *
 * 책임:
 * - toolChain JSON 파싱 → 순차 실행
 * - 슬롯 치환: $SLOT_NAME / $PROFILE.home / $GPS.current / $TIME.today
 * - 결과 집계 → AgentResponse
 * - 실행 통계 기록 (LearnedSkillDao.recordUsage)
 * - 실행 실패 시 fallback 응답 + Skill 강등
 */
@Singleton
class SkillExecutor @Inject constructor(
    private val toolExecutor: ToolExecutor,
    private val learnedSkillDao: LearnedSkillDao,
    private val profileDao: ProfileDao,
    private val fallbackStrategy: FallbackStrategy,
    private val safetyPolicyEngine: SafetyPolicyEngine,
    private val agentAnalytics: AgentAnalytics,
    private val permissionManager: PermissionManager
) {
    companion object {
        private const val TAG = "SkillExecutor"
        private val ISO_DATE = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)

        // ── 신뢰도 강화(reinforcement) 파라미터 ──
        // 정적 confidence(생성 시 0.65 고정) → 실제 사용 성공률로 적응 갱신.
        // 5회 이상 100% 성공 시 ~0.95로 올라 자동 활성화 임계(SAFE 0.75)를 넘고,
        // 실패가 쌓이면 임계 아래로 떨어져 다시 Gemini를 거친다.
        private const val REINFORCE_BASE = 0.40f      // 검증 0회 출발점
        private const val REINFORCE_SPAN = 0.55f      // 성공률·사용량 가중 최대 상승폭
        private const val REINFORCE_FULL_USES = 5f    // 이 횟수 이상이면 사용량 가중 최대
        private const val REINFORCE_MIN = 0.30f
        private const val REINFORCE_MAX = 0.95f

        /**
         * 순수 신뢰도 강화 함수 (테스트 잠금용, Android 의존 0).
         * 누적 성공률 × 사용량 가중 → confidence. 5회 100% 성공 시 0.95(자동 활성화 임계 0.75 초과),
         * 실패가 섞이면 임계 아래로 하락.
         */
        fun reinforcedConfidence(successCount: Int, usageCount: Int): Float {
            if (usageCount <= 0) return REINFORCE_BASE
            val successRate = successCount.toFloat() / usageCount
            val usageFactor = minOf(usageCount.toFloat() / REINFORCE_FULL_USES, 1f)
            return (REINFORCE_BASE + REINFORCE_SPAN * successRate * usageFactor)
                .coerceIn(REINFORCE_MIN, REINFORCE_MAX)
        }
    }

    /**
     * 매칭된 Skill을 실행하고 AgentResponse 반환.
     * Gemini 호출 없음 — L3 계층 hot path.
     */
    suspend fun execute(
        match: SkillMatchResult,
        context: AgentContext,
        runRecorder: AgentRunRecorder? = null
    ): AgentResponse {
        val startMs = System.currentTimeMillis()
        val profile = runCatching { profileDao.getProfile() }.getOrNull()
        val plan = try {
            buildAgentPlan(match, context, profile)
        } catch (e: Exception) {
            Log.e(TAG, "PlanCache plan build failed: ${e.message}", e)
            failPlanCache(match, runRecorder, "plan_cache_error", e.message, e)
        }
        runRecorder?.recordPlan(plan)
        rejectPlan(plan, match)?.let { reason ->
            failPlanCache(match, runRecorder, "plan_cache_rejected", reason)
        }

        try {
            val toolsUsed = mutableListOf<String>()
            val succeededTools = mutableSetOf<String>()
            val toolResultStore = mutableMapOf<String, Any>()
            val recorder = runRecorder ?: AgentRunRecorder.start(match.fingerprint, context)
            val traceBuilder = DecisionTraceBuilder("l3-${match.id()}", match.fingerprint)
            val toolResponses = AgentToolExecutionHarness(
                toolExecutor = toolExecutor,
                fallbackStrategy = fallbackStrategy,
                agentAnalytics = agentAnalytics,
                // 권한 단일 게이트 — Skill(PlanCache) 경로도 위치 권한을 강제(Gemini/Live와 일관).
                locationPermissionGranted = {
                    permissionManager.check(AgentCapability.LOCATION_ACCESS) is PermissionResult.Granted
                }
            ).executePlan(
                plan = plan,
                context = context,
                traceBuilder = traceBuilder,
                runRecorder = recorder,
                toolResultStore = toolResultStore,
                toolsUsed = toolsUsed,
                succeededTools = succeededTools
            )

            val failed = toolResponses.firstNotNullOfOrNull { response ->
                response.optJSONObject("functionResponse")
                    ?.optJSONObject("response")
                    ?.takeIf { it.has("error") }
            }
            if (failed != null) {
                Log.d(TAG, "PlanCache tool returned error — falling back (id=${match.id()})")
                failPlanCache(
                    match = match,
                    runRecorder = runRecorder,
                    kind = "plan_cache_tool_error",
                    detail = failed.optString("error", "unknown")
                )
            }

            val latency = (System.currentTimeMillis() - startMs).toInt()
            // LearnedSkill만 통계 기록 (Template은 아직 DB 없음 — Learner가 승격 후 기록)
            if (match.source == SkillMatchResult.Source.LEARNED_SKILL) {
                learnedSkillDao.recordUsage(
                    id = match.id(),
                    successDelta = 1,
                    latencyMs = latency,
                    now = System.currentTimeMillis()
                )
                reinforceConfidence(match.id())  // 성공 → 신뢰도 상승(자동 활성화 가능)
            }

            return responseFromStore(match, toolResultStore, toolsUsed)
        } catch (e: PlanCacheFallbackException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Skill execution failed: ${e.message}", e)
            failPlanCache(match, runRecorder, "plan_cache_error", e.message, e)
        }
    }

    private fun buildAgentPlan(
        match: SkillMatchResult,
        context: AgentContext,
        profile: ProfileEntity?
    ): AgentPlan {
        val chainArr = JSONArray(match.toolChainJson())
        val steps = mutableListOf<AgentPlanStep>()
        for (i in 0 until chainArr.length()) {
            val call = chainArr.getJSONObject(i)
            val rawParams = call.optJSONObject("params") ?: JSONObject()
            steps += AgentPlanStep(
                toolName = call.getString("tool"),
                args = resolveParams(rawParams, match.slotValues, context, profile)
            )
        }
        return AgentPlan(
            source = when (match.source) {
                SkillMatchResult.Source.LEARNED_SKILL -> AgentPlanSource.LEARNED_SKILL
                SkillMatchResult.Source.TEMPLATE -> AgentPlanSource.TEMPLATE
            },
            sourceId = match.id(),
            routeLabel = "l3_${match.source.name.lowercase()}",
            steps = steps
        )
    }

    private fun rejectPlan(plan: AgentPlan, match: SkillMatchResult): String? {
        if (plan.steps.isEmpty()) return "empty_tool_chain"
        plan.toolNames.firstOrNull { ToolRegistry.builtIn.specOrNull(it) == null }?.let {
            return "unknown_tool:$it"
        }
        plan.toolNames.firstOrNull { !safetyPolicyEngine.isLearnable(it) }?.let {
            return "non_learnable_tool:$it"
        }
        val declaredRisk = match.learnedSkill?.maxToolRiskTier ?: match.template?.maxToolRiskTier
        val expectedRisk = safetyPolicyEngine.maxRiskTierOf(plan.toolNames).code
        if (declaredRisk != null && declaredRisk.trim().uppercase() != expectedRisk) {
            return "stale_maxToolRiskTier:${declaredRisk}->${expectedRisk}"
        }
        return null
    }

    // ── 슬롯·$PROFILE·$GPS·$TIME 치환 ──

    private fun resolveParams(
        raw: JSONObject,
        slots: Map<String, String>,
        context: AgentContext,
        profile: ProfileEntity?
    ): Map<String, Any?> {
        val out = mutableMapOf<String, Any?>()
        val keys = raw.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val v = raw.get(key)
            out[key] = resolveValue(v, slots, context, profile)
        }
        return out
    }

    private fun resolveValue(
        v: Any?,
        slots: Map<String, String>,
        context: AgentContext,
        profile: ProfileEntity?
    ): Any? {
        if (v is JSONObject) {
            val m = mutableMapOf<String, Any?>()
            val keys = v.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                m[k] = resolveValue(v.get(k), slots, context, profile)
            }
            return m
        }
        if (v is JSONArray) {
            return (0 until v.length()).map { resolveValue(v.get(it), slots, context, profile) }
        }
        if (v !is String) return v
        if (!v.startsWith("$")) return v

        // 단일 $SLOT_NAME
        Regex("^\\$([A-Z_]+)$").find(v)?.let { m ->
            return slots[m.groupValues[1]]
        }

        // 경로 있는 참조
        return when (v) {
            "\$PROFILE.home" -> profile?.homeAddress
            "\$PROFILE.work" -> profile?.workAddress
            "\$PROFILE.home.lat" -> profile?.homeLat
            "\$PROFILE.home.lng" -> profile?.homeLng
            "\$PROFILE.work.lat" -> profile?.workLat
            "\$PROFILE.work.lng" -> profile?.workLng
            "\$GPS.current.x" -> context.locationX
            "\$GPS.current.y" -> context.locationY
            "\$GPS.current" -> mapOf("x" to context.locationX, "y" to context.locationY)
            "\$TIME.today" -> ISO_DATE.format(Date())
            "\$TIME.tomorrow" -> ISO_DATE.format(
                Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }.time
            )
            else -> v  // 알 수 없는 참조는 원본 유지 (Tool 쪽에서 처리 또는 무시)
        }
    }

    private fun renderTemplate(template: String, slots: Map<String, String>): String {
        if (template.isBlank()) return template
        var out = template
        slots.forEach { (k, v) ->
            out = out.replace("{$k}", v)
        }
        return out
    }

    // ── 실패 처리 ──

    private suspend fun handleFailure(match: SkillMatchResult) {
        if (match.source != SkillMatchResult.Source.LEARNED_SKILL) return
        val skill = match.learnedSkill ?: return
        val now = System.currentTimeMillis()
        learnedSkillDao.recordUsage(skill.id, successDelta = 0, latencyMs = 0, now = now)
        learnedSkillDao.recordNegativeFeedback(skill.id, now)
        reinforceConfidence(skill.id)  // 실패 → 성공률 하락 → 신뢰도 하락(자동 활성화 철회)

        // 연속 3회 실패 추정 시 DEGRADED, 5회면 AUTO_DISABLED
        val refreshed = learnedSkillDao.getById(skill.id) ?: return
        val negative = refreshed.negativeFeedbackCount
        val newStatus = when {
            negative >= 5 -> "AUTO_DISABLED"
            negative >= 3 -> "DEGRADED"
            else -> refreshed.status
        }
        if (newStatus != refreshed.status) {
            learnedSkillDao.updateStatus(skill.id, newStatus, now)
            Log.d(TAG, "Skill ${skill.id} → $newStatus (negative=$negative)")
        }
    }

    private suspend fun failPlanCache(
        match: SkillMatchResult,
        runRecorder: AgentRunRecorder?,
        kind: String,
        detail: String?,
        cause: Throwable? = null
    ): Nothing {
        val normalizedDetail = detail ?: "unknown"
        runRecorder?.recordFallback(kind, normalizedDetail)
        handleFailure(match)
        throw PlanCacheFallbackException("$kind: $normalizedDetail", cause)
    }

    /**
     * 실제 사용 결과(누적 성공률 × 사용량)로 confidence를 재계산·저장.
     * 정적 학습(고정 0.65) → 적응 학습. SkillMatcher/SafetyPolicyEngine의
     * confidence 임계 게이팅과 결합되어, 검증된 스킬만 자동 활성화(Gemini 우회)된다.
     */
    // L3 경로는 chatMutex 밖에서 실행되므로, 동일 스킬 동시 갱신 시 read-modify-write
    // 인터리빙을 막기 위해 confidence 재계산을 직렬화한다.
    private val reinforceMutex = kotlinx.coroutines.sync.Mutex()

    private suspend fun reinforceConfidence(skillId: String) = reinforceMutex.withLock {
        val s = learnedSkillDao.getById(skillId) ?: return@withLock
        if (s.usageCount <= 0) return@withLock
        val newConfidence = reinforcedConfidence(s.successCount, s.usageCount)
        if (kotlin.math.abs(newConfidence - s.confidence) >= 0.01f) {
            learnedSkillDao.updateConfidence(skillId, newConfidence, System.currentTimeMillis())
            Log.d(TAG, "Skill $skillId confidence ${"%.2f".format(s.confidence)} → ${"%.2f".format(newConfidence)} (${s.successCount}/${s.usageCount} success)")
        }
    }

    private fun responseFromStore(
        match: SkillMatchResult,
        toolResultStore: Map<String, Any>,
        toolsUsed: List<String>
    ): AgentResponse {
        val reply = renderTemplate(match.replyTemplate() ?: "", match.slotValues).ifBlank { "요청 처리했어요." }
        val tts = renderTemplate(match.ttsTemplate() ?: reply, match.slotValues).ifBlank { reply }
        return AgentResponseAdapter()
            .adapt(reply, match.fingerprint, toolResultStore)
            .copy(ttsText = tts, toolsUsed = toolsUsed)
    }
}
