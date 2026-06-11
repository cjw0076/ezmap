package com.example.ez_capstone.agent

import org.junit.Assert.*
import org.junit.Test

/**
 * FallbackStrategy 정적 매핑 테스트.
 * 결정론적: FALLBACK_MAP은 컴파일 시 확정.
 */
class FallbackStrategyTest {

    @Test
    fun `카카오 경로 실패 → 네이버 fallback`() {
        // 예상: "get_directions_naver"
        val fallback = FallbackStrategy.FALLBACK_MAP["get_directions"]
        assertEquals("get_directions_naver", fallback)
    }

    @Test
    fun `네이버 경로 실패 → 카카오 fallback`() {
        // 예상: "get_directions" (양방향)
        val fallback = FallbackStrategy.FALLBACK_MAP["get_directions_naver"]
        assertEquals("get_directions", fallback)
    }

    @Test
    fun `기상청 날씨 실패 → OpenWeather fallback`() {
        val fallback = FallbackStrategy.FALLBACK_MAP["get_weather_kma"]
        assertEquals("get_weather", fallback)
    }

    @Test
    fun `OpenWeather 실패 → 기상청 fallback`() {
        val fallback = FallbackStrategy.FALLBACK_MAP["get_weather"]
        assertEquals("get_weather_kma", fallback)
    }

    @Test
    fun `실시간 주차 실패 → 정적 주차 fallback`() {
        val fallback = FallbackStrategy.FALLBACK_MAP["get_realtime_parking"]
        assertEquals("get_parking", fallback)
    }

    @Test
    fun `매핑 없는 tool → null`() {
        // 예상: null — search_places는 fallback 없음
        val fallback = FallbackStrategy.FALLBACK_MAP["search_places"]
        assertNull(fallback)
    }

    @Test
    fun `존재하지 않는 tool → null`() {
        val fallback = FallbackStrategy.FALLBACK_MAP["nonexistent_tool"]
        assertNull(fallback)
    }

    @Test
    fun `모든 fallback은 양방향`() {
        // 예상: A→B이면 B→A도 존재
        for ((key, value) in FallbackStrategy.FALLBACK_MAP) {
            val reverse = FallbackStrategy.FALLBACK_MAP[value]
            assertNotNull("$value → ? 역방향 매핑 존재해야 함", reverse)
            assertEquals("$key ↔ $value 양방향", key, reverse)
        }
    }

    // 검증오류는 도구 고장 아님 → 자동비활성화 카운트 제외 (send_message 오인 비활성화 차단)
    @Test
    fun `인자 누락 오류는 검증오류로 판정`() {
        assertTrue(FallbackStrategy.isValidationError("recipient 필요"))
        assertTrue(FallbackStrategy.isValidationError("message 필요"))
        assertTrue(FallbackStrategy.isValidationError("query 필요"))
        assertTrue(FallbackStrategy.isValidationError("위치 필요"))
        assertTrue(FallbackStrategy.isValidationError("id 필요"))
    }

    @Test
    fun `실제 도구 고장은 검증오류 아님`() {
        assertFalse(FallbackStrategy.isValidationError("HTTP 403: Forbidden"))
        assertFalse(FallbackStrategy.isValidationError("네트워크 오류"))
        assertFalse(FallbackStrategy.isValidationError("RATE_LIMITED"))
        assertFalse(FallbackStrategy.isValidationError("경로를 찾을 수 없습니다"))
    }
}
