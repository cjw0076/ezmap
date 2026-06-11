package com.example.ez_capstone.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GeminiAgentEngine.shouldUsePredictiveCache 회귀 게이트.
 *
 * Predictive cache 키는 (요일×시간대)뿐이라 목적지·발화를 구분하지 못한다.
 * 따라서 주행 중(navigating)에는 절대 단락에 쓰면 안 된다 — 과거엔 주행 중 모든 발화가
 * 캐시된 경로 응답으로 덮여(예: "주유소/날씨/문자" → 같은 경로 답 반복) 에이전트가
 * 무력화됐다. 이 테스트가 그 회귀를 빌드 단계에서 차단한다.
 */
class PredictiveCacheGatingTest {

    private fun use(driving: String, text: String) =
        GeminiAgentEngine.shouldUsePredictiveCache(driving, text)

    // 단락은 폐기됨(항상 false): 캐시 키가 목적지를 구분 못 해 IDLE에서도 '다른 목적지' 경로를
    // 반환하는 오답을 내므로, 어떤 상태/발화에서도 predictive cache로 단락하지 않는다.

    @Test fun `주행 중에는 어떤 발화도 캐시 단락하지 않는다`() {
        assertFalse(use("navigating", "집에 가자"))
        assertFalse(use("navigating", "근처 주유소 찾아줘"))
        assertFalse(use("navigating", "파리 수도는?"))
        assertFalse(use("driving", "집에 가자"))
    }

    @Test fun `IDLE에서도 경로 요청 포함 어떤 발화도 캐시 단락하지 않는다`() {
        assertFalse(use("idle", "집에 가자"))
        assertFalse(use("idle", "회사로 가줘"))
        assertFalse(use("idle", "강남역 경로 알려줘"))
        assertFalse(use("idle", "오늘 날씨 어때"))
    }
}
