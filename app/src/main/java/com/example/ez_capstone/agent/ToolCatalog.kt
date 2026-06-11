package com.example.ez_capstone.agent

/**
 * 도구 등급 — 에이전트가 앱에 행하는 행위의 신뢰/위험 계층.
 *
 * - READ      : 조회만. 상태 변경 없음. 자유롭게 실행 가능.
 * - WRITE     : 로컬 상태 변경(가역, 저위험). 즉시 실행 후 실제 결과를 echo.
 * - SENSITIVE : 외부 부작용/비가역(문자 발송·전화 발신). 반드시 사용자 확인(초안/확인카드) 후 실행.
 */
enum class ToolKind { READ, WRITE, SENSITIVE }

/**
 * 모든 도구의 등급 단일 진실원천. (이전엔 GroundingGuard.WRITE_TOOLS 등에 흩어져 있었음.)
 * 새 도구 추가 시 여기 분류 필수 — ToolCatalogTest가 누락을 빌드 실패로 잡는다.
 */
object ToolCatalog {

    val KIND: Map<String, ToolKind> by lazy {
        ToolRegistry.builtIn.kinds
    }

    /** 미분류는 보수적으로 SENSITIVE 처리해 확인 없는 외부 부작용을 차단한다. */
    fun kindOf(name: String): ToolKind = KIND[name] ?: ToolKind.SENSITIVE

    /** 상태를 바꾸는 도구(WRITE ∪ SENSITIVE) — 완료 주장이 정당하려면 이 중 하나가 성공해야 함. */
    val STATE_CHANGING: Set<String> =
        KIND.filterValues { it != ToolKind.READ }.keys

    /** 실행 전 사용자 확인이 필요한가(SENSITIVE). */
    fun requiresConfirmation(name: String): Boolean = kindOf(name) == ToolKind.SENSITIVE
}
