package com.example.ez_capstone

import com.example.ez_capstone.server.models.PlaceItem
import com.example.ez_capstone.server.models.RouteItem
import com.example.ez_capstone.server.models.ScheduleEvent

// 동적 카드 타입 — Phase 3 확장
enum class CardType {
    ROUTE_SUMMARY,      // 경로 요약
    CONFIRMATION,       // 사용자 확인 요청
    ALERT,              // 경고/에러
    INFO,               // 일반 정보
    THINKING,           // 에이전트 처리 중
    RECOMMENDATION,     // 추천 (여러 액션)
    SCHEDULE_POPUP,     // 일정 알림
    WEATHER,            // 날씨 정보
    DEPARTURE,          // 출발 카운트다운
    PROACTIVE,          // 프로액티브 제안
    SKILL_RESULT,       // 범용 Skill 결과
    SAFETY_ALERT,       // 안전 경고 (주행 중)
}

/**
 * CardType별 기본 autoDismiss 시간.
 * Long.MAX_VALUE = 사용자 액션 전까지 유지 (자동 dismiss 안 함).
 */
fun CardType.defaultDismissMs(): Long = when (this) {
    CardType.INFO, CardType.ALERT -> 3_000L
    CardType.ROUTE_SUMMARY, CardType.RECOMMENDATION, CardType.WEATHER -> 6_000L
    CardType.CONFIRMATION, CardType.SAFETY_ALERT, CardType.THINKING -> Long.MAX_VALUE
    CardType.SCHEDULE_POPUP, CardType.DEPARTURE, CardType.PROACTIVE, CardType.SKILL_RESULT -> 5_000L
}

// Agent가 제어하는 UI 상태
sealed class AgentUiState {
    data object Idle : AgentUiState()
    data class Processing(val hint: String = "처리 중...") : AgentUiState()
    data class RoutePreview(
        val routes: List<RouteItem>,
        val agentMessage: String,
        val routeId: Int,
        val ttsText: String? = null
    ) : AgentUiState()
    data class Navigating(val route: RouteItem) : AgentUiState()
    data class AgentMessage(val text: String, val ttsText: String? = null) : AgentUiState()
    data class AgentCard(
        val type: CardType,
        val title: String,
        val body: String,
        val actions: List<String> = emptyList(),
        val ttsText: String? = null,
        val autoDismissMs: Long = type.defaultDismissMs()
    ) : AgentUiState()
    data class ShowPlaces(
        val places: List<PlaceItem>,
        val message: String,
        val ttsText: String? = null
    ) : AgentUiState()
    data class ShowSchedule(
        val events: List<ScheduleEvent>,
        val message: String,
        val ttsText: String? = null
    ) : AgentUiState()
    data class ShowMessageDraft(
        val recipient: String,
        val phone: String = "",
        val message: String,
        val method: String = "sms",
        val ttsText: String? = null
    ) : AgentUiState()
    data class ShowCallConfirmation(
        val name: String,
        val phone: String,
        val ttsText: String? = null
    ) : AgentUiState()
    data class Error(
        val message: String,
        val cardType: CardType = CardType.ALERT,
        val retryAfterMs: Long = 0L,
        val actionRoute: String? = null
    ) : AgentUiState()
    data class Streaming(val partialText: String) : AgentUiState()
}
