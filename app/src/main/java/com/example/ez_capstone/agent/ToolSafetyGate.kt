package com.example.ez_capstone.agent

import com.example.ez_capstone.safety.ToolRiskTier

/**
 * 단일 도구 안전 게이트 (Agent Harness Audit — Wave 5 / gap #4·#5).
 *
 * 모든 도구 실행은 출처(Gemini / Live / Skill / 미래 A2A)에 관계없이 이 게이트를 통과한다.
 * 공유 [AgentToolExecutionHarness]에서 실제 실행 직전에 호출되므로 "단일 필수 단계"가 된다.
 *
 * 정책:
 *  - 빌트인 ToolSpec이 있는 도구 → ALLOW. 분류·노출·위험등급이 ToolSpec으로 보증되며,
 *    effectful 빌트인(send_message/make_call)은 다운스트림에서 draft/확인 UI로 처리된다.
 *  - 빌트인 ToolSpec이 없는 도구(외부/MCP/미상) → 기본 허용(사용자가 명시적으로 추가한
 *    MCP 서버의 읽기성 도구)하되, **돌이킬 수 없거나 외부로 나가는 effectful 동작으로 보이는
 *    이름은 차단**한다. 외부 도구는 ToolSpec의 risk/확인 메타데이터를 갖지 않으므로,
 *    확인 경로 없는 부작용(결제·발송·삭제 등)이 무게이트로 실행되는 것을 막는다.
 */
object ToolSafetyGate {

    enum class Decision { ALLOW, ALLOW_EXTERNAL, BLOCK }

    data class Verdict(val decision: Decision, val reason: String) {
        val allowed: Boolean get() = decision != Decision.BLOCK
    }

    // 외부(빌트인 스펙 없는) 도구 이름에서 '돌이킬 수 없거나 외부로 나가는' effectful 신호.
    // MCP 도구명은 보통 영어(mcp__server__action)라 영어 동사로 매칭한다.
    private val EXTERNAL_EFFECTFUL_HINTS = listOf(
        "send", "delete", "remove", "pay", "transfer", "purchase",
        "buy", "withdraw", "wire", "refund", "email", "sms", "checkout"
    )

    /**
     * @param externalRiskTier 외부(빌트인 아님) 도구의 분류된 위험등급 조회. 보통 MCP 게이트웨이의
     *   riskTierOf를 넘긴다. 제공되면 이름 휴리스틱보다 우선해 정확히 판단한다.
     */
    fun evaluate(
        toolName: String,
        registry: ToolRegistry = ToolRegistry.builtIn,
        externalRiskTier: ((String) -> ToolRiskTier?)? = null
    ): Verdict {
        val spec = registry.specOrNull(toolName)
        if (spec != null) {
            // 빌트인: ToolSpec이 보증. effectful 확인은 다운스트림이 담당.
            return Verdict(Decision.ALLOW, "builtin:${spec.riskTier.name.lowercase()}")
        }
        // 외부/MCP: 분류된 risk 메타데이터가 있으면 그걸로 판단(이름 휴리스틱보다 정확).
        externalRiskTier?.invoke(toolName)?.let { risk ->
            return if (risk == ToolRiskTier.EFFECTFUL) {
                Verdict(Decision.BLOCK, "external_effectful_blocked:${risk.code.lowercase()}")
            } else {
                Verdict(Decision.ALLOW_EXTERNAL, "external_${risk.code.lowercase()}_allowed")
            }
        }
        // 메타데이터 없음 → 이름 휴리스틱 폴백.
        val lower = toolName.lowercase()
        if (EXTERNAL_EFFECTFUL_HINTS.any { lower.contains(it) }) {
            return Verdict(Decision.BLOCK, "external_effectful_blocked")
        }
        return Verdict(Decision.ALLOW_EXTERNAL, "external_read_allowed")
    }
}
