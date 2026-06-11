package com.example.ez_capstone.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * GeminiAgentEngine.nbestActionFor 회귀 게이트.
 *
 * 실측 배경: STT 대안을 프롬프트에 넣어도 Flash가 재해석 지시를 무시 → 하니스가 결정론적으로 처리.
 * 정책: 막다른 답 + 대안 존재 시 내비/장소=조용히 재시도, 그 외=확인. 정상 응답엔 개입 안 함.
 */
class NbestActionTest {

    private val alts = listOf("파리 수도가 어디야")
    private val deadEnd = "'허리 수도'라는 정보는 찾을 수 없어요."
    private val ok = "프랑스의 수도는 파리입니다."

    @Test fun `막다른답+대안 내비는 조용히 재시도`() {
        val a = GeminiAgentEngine.nbestActionFor(IntentCategory.NAVIGATION, deadEnd, alts)
        assertTrue(a is GeminiAgentEngine.NbestAction.SilentRetry)
        assertEquals("파리 수도가 어디야", (a as GeminiAgentEngine.NbestAction.SilentRetry).alternative)
    }

    @Test fun `막다른답+대안 장소는 조용히 재시도`() {
        val a = GeminiAgentEngine.nbestActionFor(IntentCategory.PLACES, deadEnd, alts)
        assertTrue(a is GeminiAgentEngine.NbestAction.SilentRetry)
    }

    @Test fun `막다른답+대안 그외 의도는 확인`() {
        for (intent in listOf(IntentCategory.AUTO, IntentCategory.INFO, IntentCategory.COMMUNICATION)) {
            val a = GeminiAgentEngine.nbestActionFor(intent, deadEnd, alts)
            assertTrue("$intent → Confirm 기대", a is GeminiAgentEngine.NbestAction.Confirm)
        }
    }

    @Test fun `정상 응답엔 개입 안 함`() {
        assertEquals(
            GeminiAgentEngine.NbestAction.None,
            GeminiAgentEngine.nbestActionFor(IntentCategory.AUTO, ok, alts)
        )
    }

    @Test fun `대안 없으면 개입 안 함`() {
        assertEquals(
            GeminiAgentEngine.NbestAction.None,
            GeminiAgentEngine.nbestActionFor(IntentCategory.NAVIGATION, deadEnd, emptyList())
        )
    }

    @Test fun `빈 대안만 있으면 개입 안 함`() {
        assertEquals(
            GeminiAgentEngine.NbestAction.None,
            GeminiAgentEngine.nbestActionFor(IntentCategory.NAVIGATION, deadEnd, listOf("", "  "))
        )
    }

    @Test fun `unresolved 마커 판별`() {
        assertTrue(GeminiAgentEngine.isUnresolvedReply("정보는 찾을 수 없어요"))
        assertTrue(GeminiAgentEngine.isUnresolvedReply("그 장소는 못 찾았어요"))
        assertFalse(GeminiAgentEngine.isUnresolvedReply("프랑스의 수도는 파리입니다."))
    }
}
