package com.example.ez_capstone.agent

import com.example.ez_capstone.safety.ToolRiskTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ToolSafetyGate 회귀 게이트 (Agent Harness Wave 5 / gap #4·#5).
 *
 * 단일 안전 게이트: 모든 도구 실행이 출처 무관하게 통과. 빌트인은 허용(다운스트림 확인),
 * 빌트인 ToolSpec 없는 외부(MCP) effectful 도구는 차단(확인 없는 부작용 방지).
 */
class ToolSafetyGateTest {

    @Test fun `빌트인 도구는 허용된다`() {
        assertTrue(ToolSafetyGate.evaluate("search_places").allowed)
        assertTrue(ToolSafetyGate.evaluate("get_directions").allowed)
        assertTrue(ToolSafetyGate.evaluate("get_weather_kma").allowed)
    }

    @Test fun `effectful 빌트인도 게이트는 통과한다 (다운스트림 draft 확인이 담당)`() {
        // send_message/make_call/delete_*는 빌트인 ToolSpec이 있어 ALLOW —
        // 이름에 send/delete가 있어도 빌트인 경로가 먼저 매칭되어 외부 차단 규칙을 타지 않는다.
        assertTrue(ToolSafetyGate.evaluate("send_message").allowed)
        assertTrue(ToolSafetyGate.evaluate("make_call").allowed)
        assertTrue(ToolSafetyGate.evaluate("delete_schedule").allowed)
        assertTrue(ToolSafetyGate.evaluate("delete_note").allowed)
    }

    @Test fun `외부 읽기성 도구는 ALLOW_EXTERNAL`() {
        val v = ToolSafetyGate.evaluate("mcp__notion__search")
        assertTrue(v.allowed)
        assertEquals(ToolSafetyGate.Decision.ALLOW_EXTERNAL, v.decision)
    }

    @Test fun `외부 effectful 도구는 BLOCK된다 (이름 휴리스틱 폴백)`() {
        // externalRiskTier 미제공 → 이름 휴리스틱 폴백.
        assertFalse(ToolSafetyGate.evaluate("mcp__gmail__send_email").allowed)
        assertFalse(ToolSafetyGate.evaluate("transfer_funds").allowed)
        assertFalse(ToolSafetyGate.evaluate("mcp__bank__wire_money").allowed)
        assertFalse(ToolSafetyGate.evaluate("checkout_cart").allowed)
        assertEquals(ToolSafetyGate.Decision.BLOCK, ToolSafetyGate.evaluate("delete_remote_record").decision)
    }

    @Test fun `외부 risk 메타데이터가 이름 휴리스틱보다 우선한다`() {
        val byName: (String) -> ToolRiskTier? = { name ->
            when {
                name.contains("send") -> ToolRiskTier.EFFECTFUL
                name.contains("search") -> ToolRiskTier.SAFE
                else -> ToolRiskTier.STATEFUL
            }
        }
        // 메타데이터 EFFECTFUL → 차단
        assertFalse(ToolSafetyGate.evaluate("mcp_x_send_thing", externalRiskTier = byName).allowed)
        // 메타데이터 SAFE/STATEFUL → 허용
        assertTrue(ToolSafetyGate.evaluate("mcp_x_search_thing", externalRiskTier = byName).allowed)
        assertTrue(ToolSafetyGate.evaluate("mcp_x_create_thing", externalRiskTier = byName).allowed)

        // 메타데이터(SAFE)가 이름의 effectful 휴리스틱('send')을 덮어쓴다 → 오탐 감소.
        val alwaysSafe: (String) -> ToolRiskTier? = { ToolRiskTier.SAFE }
        assertTrue(ToolSafetyGate.evaluate("mcp_x_send_report", externalRiskTier = alwaysSafe).allowed)

        // 빌트인 도구는 람다와 무관하게 항상 ALLOW.
        assertTrue(ToolSafetyGate.evaluate("send_message", externalRiskTier = byName).allowed)
    }
}
