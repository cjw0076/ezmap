package com.example.ez_capstone.safety

import android.util.Log
import com.example.ez_capstone.agent.ToolRegistry
import com.example.ez_capstone.agent.models.AgentResponse
import javax.inject.Inject
import javax.inject.Singleton

/** 주행 상태 — 오타 방지를 위해 enum으로 관리 */
enum class DrivingState {
    IDLE, NAVIGATING, WALKING, PARKED;

    companion object {
        fun fromAgentState(value: String?): DrivingState = when (value?.lowercase()) {
            "navigating", "driving" -> NAVIGATING
            "walking" -> WALKING
            "parked" -> PARKED
            else -> IDLE
        }
    }
}

/**
 * 안전 정책 엔진.
 * Agent 응답을 사용자에게 전달하기 전에 안전 규칙을 적용한다.
 * 운전 중 복잡한 UI 차단, GPS 불안정 대응, 배터리 위험 알림 등.
 */
data class SafetyContext(
    val drivingState: DrivingState = DrivingState.IDLE,
    val hasGps: Boolean = true,
    val batteryPct: Int? = null,           // 0-100, null이면 알 수 없음
    val isHighway: Boolean = false
)

sealed class SafetyDecision {
    data object Allow : SafetyDecision()
    data class Simplify(val reason: String) : SafetyDecision()
    data class Warn(val reason: String, val prefix: String) : SafetyDecision()
}

/**
 * Tool 위험도 분류 — SELF_LEARNING_AGENT.md 결정 2 구현.
 *
 * - SAFE: 정보 조회형. LearnedSkill 생성 가능. confidence 임계 0.75
 * - STATEFUL: 상태 변경형(경로 시작 등). LearnedSkill 생성 가능. confidence 임계 0.85
 * - EFFECTFUL: 부작용 큰 것(전화·메시지). LearnedSkill 생성 금지 — 항상 Gemini+SafetyPolicyEngine 경유
 */
enum class ToolRiskTier(val code: String, val confidenceThreshold: Float) {
    SAFE("SAFE", 0.75f),
    STATEFUL("STATEFUL", 0.85f),
    EFFECTFUL("EFFECTFUL", 1.01f);  // 1.01 = 절대 도달 불가, LearnedSkill 우회 금지

    companion object {
        fun max(a: ToolRiskTier, b: ToolRiskTier): ToolRiskTier =
            if (a.ordinal >= b.ordinal) a else b
    }
}

@Singleton
class SafetyPolicyEngine @Inject constructor() {
    companion object {
        private const val TAG = "SafetyPolicy"

        /**
         * 운전 중 차단할 복잡한 UI 액션.
         * show_places/show_schedule는 음성 안내 허용.
         * show_message_draft는 NavigationVM이 즉시 발송(음성 확인)으로 처리하므로 허용.
         */
        private val COMPLEX_UI_ACTIONS = setOf(
            "show_cards"
        )

        /** 주행 중 confidence 임계 가산 (결정 2) */
        const val DRIVING_CONFIDENCE_BOOST = 0.05f

        fun toolRiskTierOf(toolName: String): ToolRiskTier =
            ToolRegistry.builtIn.specOrNull(toolName)?.riskTier ?: ToolRiskTier.STATEFUL
    }

    /**
     * Tool이 LearnedSkill에 포함될 수 있는지 판정.
     * EFFECTFUL Tool(전화·메시지)은 항상 Gemini + 사용자 확인 경유.
     */
    fun isLearnable(toolName: String): Boolean {
        val tier = ToolRegistry.builtIn.specOrNull(toolName)?.riskTier ?: return false  // 미등록 Tool은 보수적으로 금지
        return tier != ToolRiskTier.EFFECTFUL
    }

    /**
     * Tool의 위험 계층. 미등록 Tool은 STATEFUL로 보수적 처리.
     */
    fun toolRiskTier(toolName: String): ToolRiskTier =
        toolRiskTierOf(toolName)

    /**
     * toolChain 전체의 최대 위험 계층.
     * LearnedSkillEntity.maxToolRiskTier에 캐시됨.
     */
    fun maxRiskTierOf(toolNames: List<String>): ToolRiskTier =
        toolNames.map { toolRiskTier(it) }.fold(ToolRiskTier.SAFE) { acc, t -> ToolRiskTier.max(acc, t) }

    /**
     * LearnedSkill Pre-flight 허용 여부.
     * 차단 조건:
     * - toolChain에 EFFECTFUL Tool이 하나라도 포함
     * - 주행 중이고 toolChain 길이 ≥ 3 (복잡성 임계)
     * - 현재 위치가 Skill 학습 컨텍스트에서 크게 벗어난 경우 (TODO: ContextDistribution 검사)
     *
     * @param toolNames Skill의 toolChain에 포함된 Tool 이름 목록
     * @param confidence Skill의 현재 confidence
     * @param safety 현재 SafetyContext
     * @return 이 Skill을 Gemini 우회로 즉시 실행해도 되는가
     */
    fun allowsLearnedSkill(
        toolNames: List<String>,
        confidence: Float,
        safety: SafetyContext
    ): Boolean {
        // EFFECTFUL 또는 미등록 Tool 포함 금지
        if (toolNames.any { !isLearnable(it) }) return false

        // 주행 중 복잡성 임계
        if (safety.drivingState == DrivingState.NAVIGATING && toolNames.size >= 3) return false

        // 계층 임계 + 주행 가산
        val tier = maxRiskTierOf(toolNames)
        val threshold = tier.confidenceThreshold +
            (if (safety.drivingState == DrivingState.NAVIGATING) DRIVING_CONFIDENCE_BOOST else 0f)

        return confidence >= threshold
    }

    /**
     * 안전 규칙 평가 후 필요 시 응답을 수정하여 반환.
     * @return Pair(적용된 결정, 수정된 응답)
     */
    fun evaluate(response: AgentResponse, safety: SafetyContext): Pair<SafetyDecision, AgentResponse> {
        // Rule 1: 주행 중 복잡한 UI 차단
        if (safety.drivingState == DrivingState.NAVIGATING && response.uiAction in COMPLEX_UI_ACTIONS) {
            val decision = SafetyDecision.Simplify("주행 중 복잡한 UI 차단: ${response.uiAction}")
            Log.d(TAG, decision.reason)
            // 미루지 않고 핵심만 음성으로 — "정차 후 보세요" 식 deferral 제거
            val simplified = response.copy(
                uiAction = "none",
                replyText = extractFirstSentence(response.replyText)
            )
            return decision to simplified
        }

        // Rule 2: 주행 중 경로 비교(2개+) 시 음성으로 축약
        if (safety.drivingState == DrivingState.NAVIGATING && response.uiAction == "show_route") {
            val routes = response.uiData["routes"]
            if (routes is List<*> && routes.size > 1) {
                val decision = SafetyDecision.Simplify("주행 중 다중 경로 비교 축약")
                Log.d(TAG, decision.reason)
                val simplified = response.copy(
                    replyText = extractFirstSentence(response.replyText)
                )
                return decision to simplified
            }
        }

        // Rule 3: GPS 없는데 위치 기반 UI 제공 시 경고
        if (!safety.hasGps && response.uiAction in setOf("show_route", "start_navigation", "show_places")) {
            val decision = SafetyDecision.Warn("GPS 없이 위치 기반 응답", "GPS 신호가 불안정해요. ")
            Log.d(TAG, decision.reason)
            val warned = response.copy(
                replyText = decision.prefix + response.replyText
            )
            return decision to warned
        }

        // Rule 4: 배터리 위험 (10% 이하) + 내비 중
        if (safety.batteryPct != null && safety.batteryPct <= 10 && safety.drivingState == DrivingState.NAVIGATING) {
            val decision = SafetyDecision.Warn("배터리 ${safety.batteryPct}%", "배터리가 거의 없어요. ")
            Log.d(TAG, decision.reason)
            val warned = response.copy(
                replyText = "배터리가 ${safety.batteryPct}%예요. 충전기를 연결하시거나, 목적지 주소를 메모해두세요. " + response.replyText
            )
            return decision to warned
        }

        // Rule 5: 고속도로 주행 중 비경로 액션 차단 (장소/일정 조회는 허용)
        if (safety.isHighway && safety.drivingState == DrivingState.NAVIGATING &&
            response.uiAction !in setOf("none", "show_route", "start_navigation", "update_route", "show_places", "show_schedule", "show_message_draft")) {
            val decision = SafetyDecision.Simplify("고속 주행 중 비경로 액션 차단")
            Log.d(TAG, decision.reason)
            val simplified = response.copy(
                uiAction = "none",
                replyText = extractFirstSentence(response.replyText)
            )
            return decision to simplified
        }

        return SafetyDecision.Allow to response
    }

    /** reply_text에서 첫 문장만 추출 (TTS용) */
    private fun extractFirstSentence(text: String): String {
        val delimiters = listOf(". ", "。", "! ", "? ")
        var earliest = text.length
        for (d in delimiters) {
            val idx = text.indexOf(d)
            if (idx in 0 until earliest) earliest = idx + d.length
        }
        return if (earliest < text.length) text.substring(0, earliest).trim() else text.trim()
    }
}
