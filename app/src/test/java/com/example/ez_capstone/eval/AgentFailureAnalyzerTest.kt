package com.example.ez_capstone.eval

import com.example.ez_capstone.trace.DecisionTraceEntity
import com.example.ez_capstone.trace.TraceStep
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AgentFailureAnalyzer 회귀 테스트.
 * 자가개선 분석(반복 실패 집계·랭킹·노이즈 제외)이 깨지면 빌드 실패.
 */
class AgentFailureAnalyzerTest {

    private val gson = Gson()

    private fun trace(steps: List<TraceStep>, iterations: Int = 1, safety: String? = null) =
        DecisionTraceEntity(
            sessionId = "s", requestText = "r",
            stepsJson = gson.toJson(steps),
            totalDurationMs = 100, iterationCount = iterations,
            toolsUsed = steps.mapNotNull { it.toolName }.joinToString(","),
            safetyDecision = safety
        )

    private fun toolCall(name: String, error: String? = null, fallback: Boolean = false) =
        TraceStep(iteration = 1, type = "tool_call", toolName = name, durationMs = 10, error = error, fallbackUsed = fallback)

    @Test
    fun `반복 tool 에러 집계`() {
        val traces = listOf(
            trace(listOf(toolCall("get_air_quality", error = "Forbidden"))),
            trace(listOf(toolCall("get_air_quality", error = "Forbidden"))),
            trace(listOf(toolCall("get_weather_kma")))
        )
        val r = AgentFailureAnalyzer.analyze(traces)
        val f = r.findings.first { it.kind == "tool_error" && it.subject == "get_air_quality" }
        assertEquals(2, f.count)
        assertEquals("Forbidden", f.sample)  // 대표 에러 메시지 캡처
        assertEquals(3, r.totalTraces)
    }

    @Test
    fun `1회성은 제외 (MIN_OCCURRENCE)`() {
        val traces = listOf(trace(listOf(toolCall("get_parking", error = "x"))))
        val r = AgentFailureAnalyzer.analyze(traces)
        assertTrue(r.findings.none { it.subject == "get_parking" })
    }

    @Test
    fun `fallback 집계`() {
        val traces = listOf(
            trace(listOf(toolCall("get_gas_stations", fallback = true))),
            trace(listOf(toolCall("get_gas_stations", fallback = true)))
        )
        val r = AgentFailureAnalyzer.analyze(traces)
        assertEquals(2, r.findings.first { it.kind == "fallback" }.count)
    }

    @Test
    fun `safety 결정 집계 (Allow 제외)`() {
        val traces = listOf(
            trace(emptyList(), safety = "Simplify"),
            trace(emptyList(), safety = "Simplify"),
            trace(emptyList(), safety = "Allow")
        )
        val r = AgentFailureAnalyzer.analyze(traces)
        assertEquals(2, r.findings.first { it.kind == "safety" }.count)
    }

    @Test
    fun `긴 체인 집계`() {
        val traces = listOf(
            trace(emptyList(), iterations = 6),
            trace(emptyList(), iterations = 5),
            trace(emptyList(), iterations = 2)
        )
        val r = AgentFailureAnalyzer.analyze(traces)
        assertEquals(2, r.findings.first { it.kind == "long_chain" }.count)
    }

    @Test
    fun `count 내림차순 정렬`() {
        val traces = listOf(
            trace(listOf(toolCall("a", error = "e"))),
            trace(listOf(toolCall("a", error = "e"))),
            trace(listOf(toolCall("a", error = "e"))),
            trace(listOf(toolCall("b", fallback = true))),
            trace(listOf(toolCall("b", fallback = true)))
        )
        val r = AgentFailureAnalyzer.analyze(traces)
        assertTrue(r.findings.size >= 2)
        assertTrue("count desc", r.findings[0].count >= r.findings[1].count)
        assertEquals("a", r.findings[0].subject)
    }

    @Test
    fun `빈 입력 안전`() {
        val r = AgentFailureAnalyzer.analyze(emptyList())
        assertEquals(0, r.totalTraces)
        assertTrue(r.findings.isEmpty())
    }

    // ── triage 분류 (엔지니어 에이전트가 원인별로 점검 방향 제시) ──

    @Test
    fun `Forbidden은 인증or구독 미신청으로 분류`() {
        assertEquals("auth_or_subscription", AgentFailureAnalyzer.classifyToolError("Forbidden").first)
        assertEquals("auth_or_subscription", AgentFailureAnalyzer.classifyToolError("API 키가 유효하지 않습니다 (403)").first)
    }

    @Test
    fun `검증오류와 자동비활성 구분`() {
        assertEquals("validation", AgentFailureAnalyzer.classifyToolError("recipient 필요").first)
        assertEquals("circuit_breaker", AgentFailureAnalyzer.classifyToolError("send_message 자동 비활성화됨 (연속 실패)").first)
    }

    @Test
    fun `네트워크와 한도 분류`() {
        assertEquals("network", AgentFailureAnalyzer.classifyToolError("Unable to resolve host \"x\"").first)
        assertEquals("rate_limit", AgentFailureAnalyzer.classifyToolError("RATE_LIMITED").first)
    }

    @Test
    fun `샘플 없으면 unknown`() {
        assertEquals("unknown", AgentFailureAnalyzer.classifyToolError(null).first)
    }

    @Test
    fun `tool_error finding에 cause 태그가 채워짐`() {
        val traces = listOf(
            trace(listOf(toolCall("get_air_quality", error = "Forbidden"))),
            trace(listOf(toolCall("get_air_quality", error = "Forbidden")))
        )
        val f = AgentFailureAnalyzer.analyze(traces).findings.first { it.kind == "tool_error" }
        assertEquals("auth_or_subscription", f.cause)
        assertTrue("suggestion에 cause 태그 노출", f.suggestion.contains("auth_or_subscription"))
    }
}
