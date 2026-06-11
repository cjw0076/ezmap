package com.example.ez_capstone.agent

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GeminiAgentEngine.reflectionHintFor 회귀 게이트.
 *
 * 연구 적용(구조화·행동지향 에러가 회복률↑): error_kind별 reflection 힌트를 분기한다.
 * 키/인증/한도처럼 재시도가 무의미한 에러엔 '재시도 금지+대체/안내', 그 외엔 '재계획·재시도'.
 */
class ReflectionHintTest {

    @Test fun `재시도 무의미 에러는 비재시도 힌트`() {
        for (kind in listOf("MISSING_KEY", "AUTH_FAILED", "RATE_LIMITED")) {
            val hint = GeminiAgentEngine.reflectionHintFor(kind)
            assertTrue("$kind → 재시도 금지 안내 기대: $hint", hint.contains("재시도하지 마세요"))
        }
    }

    @Test fun `복구 가능 에러는 재계획 힌트`() {
        for (kind in listOf("NETWORK", "SERVER_ERROR", "UPSTREAM", "UNCLASSIFIED")) {
            val hint = GeminiAgentEngine.reflectionHintFor(kind)
            assertTrue("$kind → 재시도/재계획 안내 기대: $hint", hint.contains("다시 시도하세요"))
        }
    }
}
