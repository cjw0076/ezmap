package com.example.ez_capstone.safety

import com.example.ez_capstone.agent.models.AgentResponse
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * SafetyPolicyEngine 단위 테스트.
 * 모든 규칙은 결정론적 — 입력이 같으면 출력이 항상 같아야 한다.
 */
class SafetyPolicyEngineTest {

    private lateinit var engine: SafetyPolicyEngine

    @Before
    fun setup() {
        engine = SafetyPolicyEngine()
    }

    // ═══════════════════════════════════════════
    // Rule 1: 주행 중 복잡한 UI 차단
    // ═══════════════════════════════════════════

    @Test
    fun `주행 중 show_places → 허용 (음성 안내, show_cards만 차단)`() {
        // 정책 변경: 주행 방해 UI(show_cards)만 차단. 장소/일정은 음성 안내로 허용.
        val response = AgentResponse(
            replyText = "근처 맛집을 찾아봤어요. 연돈 도쿄카츠가 평점 4.5로 추천이에요.",
            uiAction = "show_places"
        )
        val safety = SafetyContext(drivingState = DrivingState.NAVIGATING)

        val (decision, result) = engine.evaluate(response, safety)

        assertTrue("허용이어야 함", decision is SafetyDecision.Allow)
        assertEquals("uiAction 유지", "show_places", result.uiAction)
    }

    @Test
    fun `주행 중 show_schedule → 허용 (음성 안내)`() {
        val response = AgentResponse(replyText = "오늘 일정이에요.", uiAction = "show_schedule")
        val safety = SafetyContext(drivingState = DrivingState.NAVIGATING)

        val (decision, result) = engine.evaluate(response, safety)
        assertTrue("허용이어야 함", decision is SafetyDecision.Allow)
        assertEquals("show_schedule", result.uiAction)
    }

    @Test
    fun `주행 중 show_cards → Simplify로 차단`() {
        // show_cards(복잡 카드 UI)는 주행 중 차단 유지.
        val response = AgentResponse(replyText = "정보 카드예요.", uiAction = "show_cards")
        val safety = SafetyContext(drivingState = DrivingState.NAVIGATING)

        val (decision, result) = engine.evaluate(response, safety)
        assertTrue("Simplify 차단", decision is SafetyDecision.Simplify)
        assertEquals("none", result.uiAction)
    }

    @Test
    fun `주행 중 show_route → 허용 (경로는 안전 정보)`() {
        // 예상: Allow — 경로 표시는 주행 중에도 허용
        val response = AgentResponse(replyText = "경로 안내 시작할게요.", uiAction = "show_route")
        val safety = SafetyContext(drivingState = DrivingState.NAVIGATING)

        val (decision, result) = engine.evaluate(response, safety)

        assertTrue("경로는 허용", decision is SafetyDecision.Allow)
        assertEquals("uiAction 유지", "show_route", result.uiAction)
    }

    @Test
    fun `정차 중 show_places → 허용`() {
        // 예상: Allow — 정차 중에는 모든 UI 허용
        val response = AgentResponse(replyText = "맛집 추천이에요.", uiAction = "show_places")
        val safety = SafetyContext(drivingState = DrivingState.IDLE)

        val (decision, _) = engine.evaluate(response, safety)
        assertTrue("정차 중 허용", decision is SafetyDecision.Allow)
    }

    // ═══════════════════════════════════════════
    // Rule 2: 주행 중 다중 경로 비교 축약
    // ═══════════════════════════════════════════

    @Test
    fun `주행 중 다중 경로 → 첫 문장만 남김`() {
        // 예상: Simplify, replyText가 첫 문장으로 축약
        val response = AgentResponse(
            replyText = "자차로 30분이에요. 대중교통은 45분이고 환승 1회예요.",
            uiAction = "show_route",
            uiData = mapOf("routes" to listOf(mapOf("a" to 1), mapOf("b" to 2)))
        )
        val safety = SafetyContext(drivingState = DrivingState.NAVIGATING)

        val (decision, result) = engine.evaluate(response, safety)

        assertTrue(decision is SafetyDecision.Simplify)
        assertEquals("첫 문장만", "자차로 30분이에요.", result.replyText)
    }

    @Test
    fun `주행 중 단일 경로 → 허용`() {
        // 예상: Allow — 경로 1개면 비교가 아니므로 허용
        val response = AgentResponse(
            replyText = "30분이면 도착해요.",
            uiAction = "show_route",
            uiData = mapOf("routes" to listOf(mapOf("a" to 1)))
        )
        val safety = SafetyContext(drivingState = DrivingState.NAVIGATING)

        val (decision, _) = engine.evaluate(response, safety)
        assertTrue("단일 경로 허용", decision is SafetyDecision.Allow)
    }

    // ═══════════════════════════════════════════
    // Rule 3: GPS 없을 때 위치 기반 응답 경고
    // ═══════════════════════════════════════════

    @Test
    fun `GPS 없음 + show_route → Warn 접두사 추가`() {
        // 예상: Warn, "GPS 신호가 불안정해요." 접두사
        val response = AgentResponse(replyText = "경로를 찾았어요.", uiAction = "show_route")
        val safety = SafetyContext(drivingState = DrivingState.IDLE, hasGps = false)

        val (decision, result) = engine.evaluate(response, safety)

        assertTrue(decision is SafetyDecision.Warn)
        assertTrue("GPS 경고 포함", result.replyText.startsWith("GPS 신호가 불안정해요."))
    }

    @Test
    fun `GPS 있음 + show_route → 허용`() {
        val response = AgentResponse(replyText = "경로를 찾았어요.", uiAction = "show_route")
        val safety = SafetyContext(drivingState = DrivingState.IDLE, hasGps = true)

        val (decision, _) = engine.evaluate(response, safety)
        assertTrue(decision is SafetyDecision.Allow)
    }

    // ═══════════════════════════════════════════
    // Rule 4: 배터리 위험
    // ═══════════════════════════════════════════

    @Test
    fun `배터리 10% + 내비 중 → Warn`() {
        // 예상: Warn, 배터리 경고 메시지 포함
        val response = AgentResponse(replyText = "직진하세요.", uiAction = "none")
        val safety = SafetyContext(drivingState = DrivingState.NAVIGATING, batteryPct = 10)

        val (decision, result) = engine.evaluate(response, safety)

        assertTrue(decision is SafetyDecision.Warn)
        assertTrue("배터리 경고", result.replyText.contains("배터리"))
    }

    @Test
    fun `배터리 50% + 내비 중 → 허용`() {
        val response = AgentResponse(replyText = "직진하세요.", uiAction = "none")
        val safety = SafetyContext(drivingState = DrivingState.NAVIGATING, batteryPct = 50)

        val (decision, _) = engine.evaluate(response, safety)
        assertTrue(decision is SafetyDecision.Allow)
    }

    @Test
    fun `배터리 5% + 정차 중 → 허용 (내비 중 아님)`() {
        val response = AgentResponse(replyText = "맛집 추천이에요.", uiAction = "show_places")
        val safety = SafetyContext(drivingState = DrivingState.IDLE, batteryPct = 5)

        val (decision, _) = engine.evaluate(response, safety)
        assertTrue("정차 중이면 배터리 규칙 미적용", decision is SafetyDecision.Allow)
    }

    // ═══════════════════════════════════════════
    // Rule 5: 고속도로 주행 중 비경로 액션 차단
    // ═══════════════════════════════════════════

    @Test
    fun `고속도로 + 내비 + show_cards → Simplify`() {
        val response = AgentResponse(replyText = "날씨 정보에요. 오후에 비 올 수 있어요.", uiAction = "show_cards")
        val safety = SafetyContext(drivingState = DrivingState.NAVIGATING, isHighway = true)

        val (decision, result) = engine.evaluate(response, safety)

        // 주행 중 show_cards는 Rule 1에서 먼저 잡히지만, 고속도로 규칙도 동일 결과
        assertTrue(decision is SafetyDecision.Simplify)
        assertEquals("none", result.uiAction)
    }

    @Test
    fun `고속도로 + 내비 + show_route → 허용`() {
        // 예상: Allow — 경로는 고속도로에서도 허용
        val response = AgentResponse(replyText = "경로 변경이에요.", uiAction = "show_route")
        val safety = SafetyContext(drivingState = DrivingState.NAVIGATING, isHighway = true)

        val (decision, _) = engine.evaluate(response, safety)
        assertTrue(decision is SafetyDecision.Allow)
    }

    // ═══════════════════════════════════════════
    // 복합 시나리오
    // ═══════════════════════════════════════════

    @Test
    fun `정차 + GPS 있음 + 배터리 충분 → 모든 UI 허용`() {
        val response = AgentResponse(
            replyText = "근처 맛집이에요. 연돈 도쿄카츠 추천.",
            uiAction = "show_places"
        )
        val safety = SafetyContext(
            drivingState = DrivingState.IDLE,
            hasGps = true,
            batteryPct = 80,
            isHighway = false
        )

        val (decision, result) = engine.evaluate(response, safety)

        assertTrue("정상 상태 → 허용", decision is SafetyDecision.Allow)
        assertEquals("show_places", result.uiAction)
        assertEquals(response.replyText, result.replyText)
    }

    @Test
    fun `미등록 Tool은 LearnedSkill 자동 실행을 허용하지 않는다`() {
        val safety = SafetyContext(drivingState = DrivingState.IDLE)

        assertFalse(
            "미등록 Tool은 높은 confidence여도 자동 실행 금지",
            engine.allowsLearnedSkill(
                toolNames = listOf("nonexistent_tool"),
                confidence = 1.0f,
                safety = safety
            )
        )
    }
}
