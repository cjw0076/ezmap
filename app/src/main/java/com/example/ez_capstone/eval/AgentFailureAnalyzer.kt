package com.example.ez_capstone.eval

import com.example.ez_capstone.trace.DecisionTraceEntity

/** 반복 실패 1건(사람이 검토할 제안 단위). */
data class FailureFinding(
    val kind: String,        // tool_error | fallback | safety | long_chain
    val subject: String,     // 도구명/결정/패턴
    val count: Int,
    val suggestion: String,  // 사람이 볼 제안(데이터, 코드 아님)
    val sample: String? = null, // 대표 에러 메시지(tool_error일 때) — 원인 추적용
    val cause: String? = null   // tool_error triage 태그: auth_or_subscription/rate_limit/network/validation/circuit_breaker/empty_result/unknown
)

/** 자가개선 분석 결과. */
data class FailureReport(
    val totalTraces: Int,
    val findings: List<FailureFinding>  // count 내림차순
)

/**
 * Phase 6 자가개선 엔지니어의 **분석 코어** — 순수 함수.
 *
 * DecisionTrace 기록을 모아 반복되는 실패 패턴을 랭킹된 리포트로 만든다.
 * 출력은 "사람이 검토할 제안 데이터"이지 코드/프롬프트 자동수정이 아니다
 * (토론 합의: 런타임 자가수정 금지, 사람 게이트). 오프라인 워커가 이걸 호출하고,
 * 표시는 UI(별도 소유)에서. SDK/DB/안드로이드 의존 0 → JUnit으로 잠근다.
 */
object AgentFailureAnalyzer {
    const val LONG_CHAIN_ITERATIONS = 5
    /** 1회성 노이즈 제외 — 2회 이상 반복된 패턴만 보고. */
    const val MIN_OCCURRENCE = 2

    fun analyze(traces: List<DecisionTraceEntity>): FailureReport {
        val toolErrors = LinkedHashMap<String, Int>()
        val toolErrorSamples = HashMap<String, String>()  // 도구별 대표 에러 메시지
        val fallbacks = LinkedHashMap<String, Int>()
        val safety = LinkedHashMap<String, Int>()
        var longChains = 0

        for (t in traces) {
            for (s in t.getSteps()) {
                if (s.type == "tool_call" && s.error != null) {
                    val name = s.toolName ?: "unknown"
                    toolErrors[name] = (toolErrors[name] ?: 0) + 1
                    toolErrorSamples.putIfAbsent(name, s.error)
                }
                if (s.fallbackUsed) {
                    val name = s.toolName ?: s.fallbackFrom ?: "unknown"
                    fallbacks[name] = (fallbacks[name] ?: 0) + 1
                }
            }
            t.safetyDecision?.takeIf { it.isNotBlank() && it != "Allow" }?.let { d ->
                safety[d] = (safety[d] ?: 0) + 1
            }
            if (t.iterationCount >= LONG_CHAIN_ITERATIONS) longChains++
        }

        val findings = buildList {
            toolErrors.forEach { (tool, n) ->
                if (n >= MIN_OCCURRENCE) {
                    val (causeTag, action) = classifyToolError(toolErrorSamples[tool])
                    add(
                        FailureFinding(
                            "tool_error", tool, n,
                            "$tool ${n}회 에러 [$causeTag] — $action",
                            sample = toolErrorSamples[tool], cause = causeTag
                        )
                    )
                }
            }
            fallbacks.forEach { (tool, n) ->
                if (n >= MIN_OCCURRENCE) add(
                    FailureFinding("fallback", tool, n, "$tool ${n}회 폴백 — 1차 경로 신뢰성 점검")
                )
            }
            safety.forEach { (d, n) ->
                if (n >= MIN_OCCURRENCE) add(
                    FailureFinding("safety", d, n, "안전 필터 '$d' ${n}회 — 주행 중 UI/응답 정책 재검토")
                )
            }
            if (longChains >= MIN_OCCURRENCE) add(
                FailureFinding("long_chain", "iterations>=$LONG_CHAIN_ITERATIONS", longChains, "긴 tool 체인 ${longChains}회 — 인텐트 라우팅/도구 선택 최적화")
            )
        }.sortedByDescending { it.count }

        return FailureReport(totalTraces = traces.size, findings = findings)
    }

    /**
     * 대표 에러 메시지를 원인 카테고리(triage 태그) + 권고 조치로 분류.
     * 엔지니어 에이전트가 "무엇을 점검할지"를 바로 알 수 있게 함(사람 게이트 제안).
     * @return (causeTag, action)
     */
    fun classifyToolError(sample: String?): Pair<String, String> {
        val s = sample ?: return "unknown" to "샘플 로그 확인 필요"
        return when {
            s.contains("자동 비활성") || s.contains("자동비활성") ->
                "circuit_breaker" to "회로차단 발동 — __RESET_HEALTH__ 후 근본원인(인자/구독) 점검"
            s.contains("Forbidden", true) || s.contains("403") ||
                s.contains("INVALID_KEY") || s.contains("유효하지 않") ->
                "auth_or_subscription" to "API 키 무효/미구독 — 해당 서비스 활용신청·키 확인"
            s.contains("RATE_LIMITED") || s.contains("429") || s.contains("한도") ->
                "rate_limit" to "호출 한도 초과 — 백오프/캐시·요청량 점검"
            s.contains("resolve host", true) || s.contains("네트워크") ||
                s.contains("timeout", true) || s.contains("Unable", true) ->
                "network" to "네트워크 의존 — 오프라인 폴백 경로 점검"
            s.contains("필요") ->
                "validation" to "인자 누락(도구 고장 아님) — 스키마/시스템 프롬프트 점검"
            s.contains("찾을 수 없") || s.contains("없습니다") ->
                "empty_result" to "결과 없음 — 쿼리 정규화·폴백(Kakao 등) 점검"
            else -> "unknown" to "샘플 로그 확인 필요"
        }
    }
}
