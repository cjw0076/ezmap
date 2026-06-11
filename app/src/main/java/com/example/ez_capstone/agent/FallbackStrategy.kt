package com.example.ez_capstone.agent

import android.util.Log
import com.example.ez_capstone.agent.models.AgentContext
import com.example.ez_capstone.config.ApiKeyProvider
import com.example.ez_capstone.resilience.SkillCache
import com.example.ez_capstone.skill.SkillLifecycleManager
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Self-Healing: tool 실패 시 정적 fallback 매핑 + 복구 로직.
 * 3단계: 원래 tool → 정적 fallback → LLM에 대안 요청(에러 JSON 반환).
 * Phase 4: SkillLifecycleManager 연동 — 호출 결과 건강 기록.
 */
@Singleton
class FallbackStrategy @Inject constructor(
    private val apiKeyProvider: ApiKeyProvider,
    private val skillLifecycleManager: SkillLifecycleManager,
    private val skillCache: SkillCache
) {
    companion object {
        private const val TAG = "FallbackStrategy"

        /**
         * 정적 fallback 매핑: 실패 tool → 대체 tool.
         * 파라미터 변환 불필요 — 모든 쌍이 동일한 args 형식(origin_x/y, dest_x/y 등) 사용.
         */
        val FALLBACK_MAP: Map<String, String> = mapOf(
            "get_directions" to "get_directions_naver",
            "get_directions_naver" to "get_directions",
            "get_weather_kma" to "get_weather",
            "get_weather" to "get_weather_kma",
            "get_realtime_parking" to "get_parking",
            "get_parking" to "get_realtime_parking",
        )

        /**
         * 검증/제어성 오류(인자 누락 등) 판정 — 도구 "고장"이 아니라 호출자(에이전트)가
         * 인자를 안 채운 것. 이런 건 건강 실패로 카운트하면 안 됨(오인 자동비활성화 방지).
         * 예: "recipient 필요", "message 필요", "query 필요", "위치 필요", "id 필요".
         */
        fun isValidationError(error: String): Boolean =
            error.contains("필요")
    }

    data class FallbackEvent(
        val originalTool: String,
        val fallbackTool: String?,
        val stage: Int,       // 1=original 성공, 2=static fallback, 3=LLM 대안 요청
        val error: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val _events = mutableListOf<FallbackEvent>()
    val events: List<FallbackEvent> get() = _events.toList()

    fun getFallbackToolName(failedTool: String): String? = FALLBACK_MAP[failedTool]

    fun logEvent(event: FallbackEvent) {
        synchronized(_events) {
            _events.add(event)
            if (_events.size > 50) _events.removeAt(0)
        }
        Log.d(TAG, "Fallback: ${event.originalTool} → ${event.fallbackTool ?: "none"} (stage ${event.stage}, error=${event.error})")
    }

    /**
     * 3단계 복구 실행.
     * @return 성공한 결과 JSONObject. _fallback_from 필드로 원래 tool 추적 가능.
     */
    suspend fun executeWithRecovery(
        originalName: String,
        args: Map<String, Any?>,
        context: AgentContext,
        executor: suspend (String, Map<String, Any?>, AgentContext) -> JSONObject
    ): JSONObject {
        // Cache 체크 (TTL-기반 LRU)
        val argsJson = JSONObject().apply { args.forEach { (k, v) -> put(k, v) } }
        val argsHash = SkillCache.argsHash(argsJson)
        val cached = skillCache.get(originalName, argsHash)
        if (cached != null) return JSONObject(cached)

        // Auto-disabled 체크
        if (!skillLifecycleManager.isAvailable(originalName)) {
            return JSONObject().apply {
                put("error", "$originalName 자동 비활성화됨 (연속 실패)")
                put("suggest_alternative", true)
                put("failed_tool", originalName)
            }
        }

        // Stage 1: 원래 tool 실행
        val tStart = System.currentTimeMillis()
        val result = executor(originalName, args, context)
        val latency = System.currentTimeMillis() - tStart

        if (!result.has("error")) {
            skillLifecycleManager.recordExecution(originalName, true, latency)
            skillCache.put(originalName, argsHash, result.toString())
            return result
        }

        val originalError = result.optString("error", "unknown")
        // 인자 누락 등 검증 오류는 도구 고장이 아님 → 건강 실패로 기록하지 않고 그대로 반환.
        // (에이전트가 인자 채워 재시도/사용자 안내. send_message가 "recipient 필요"로 오인 비활성화되던 버그 차단)
        if (isValidationError(originalError)) {
            return result
        }
        skillLifecycleManager.recordExecution(originalName, false, latency, originalError)

        // Stage 2: 정적 fallback
        val fallbackName = getFallbackToolName(originalName)
        if (fallbackName != null && fallbackName != originalName) {
            logEvent(FallbackEvent(originalName, fallbackName, 2, originalError))
            val tFb = System.currentTimeMillis()
            val fallbackResult = executor(fallbackName, args, context)
            val fbLatency = System.currentTimeMillis() - tFb

            if (!fallbackResult.has("error")) {
                skillLifecycleManager.recordExecution(fallbackName, true, fbLatency)
                fallbackResult.put("_fallback_from", originalName)
                fallbackResult.put("_fallback_to", fallbackName)
                return fallbackResult
            }
            skillLifecycleManager.recordExecution(fallbackName, false, fbLatency)
        }

        // Stage 3: LLM에 대안 정보를 포함한 에러 반환
        logEvent(FallbackEvent(originalName, null, 3, originalError))
        return JSONObject().apply {
            put("error", originalError)
            put("suggest_alternative", true)
            put("failed_tool", originalName)
            put("message", "${originalName} 실행 실패. 다른 방법을 시도하거나 사용자에게 안내해주세요.")
        }
    }
}
