package com.example.ez_capstone.agent

import org.json.JSONObject

/**
 * 도구 실패의 단일 분류 표현.
 *
 * 설계 의도(핵심): silent failure 박멸 + "하나의 중앙 시스템".
 * - 기존 문제: 각 API가 실패를 emptyList()/defaultWeather()로 **삼켜서**, 호출자가
 *   "데이터 없음"과 "호출 실패"를 구분하지 못함 → FallbackStrategy·DecisionTrace·
 *   SkillLifecycle(건강점수)이 실패를 보지 못하고 성공으로 오인 → 감지·복구·학습 무력화.
 * - 해결: API는 실패를 삼키지 말고 [ToolFailureException]을 **던진다**. 그러면
 *   ToolExecutor.execute()의 중앙 catch 하나가 모든 실패를 일관 분류해 {error, error_kind}로
 *   surface하고, 그 아래 복구/추적/학습 메커니즘이 자동으로 깨어난다.
 * - 새 도구를 추가해도 "실패면 던진다"만 지키면 중앙에서 자동 처리된다(엣지케이스 도돌이표 X,
 *   시스템프롬프트 수정 X).
 */
enum class FailureKind(val code: String, val userMessage: String) {
    MISSING_KEY("MISSING_KEY", "필요한 API 키가 설정되지 않았어요. 설정에서 키를 등록해주세요."),
    AUTH("AUTH_FAILED", "API 인증에 실패했어요(키 만료/오류). 설정에서 키를 확인해주세요."),
    RATE_LIMIT("RATE_LIMITED", "요청이 한도를 초과했어요. 잠시 후 다시 시도해주세요."),
    NETWORK("NETWORK", "네트워크 연결에 문제가 있어요. 연결을 확인해주세요."),
    SERVER("SERVER_ERROR", "외부 서비스에 일시적인 문제가 있어요. 잠시 후 다시 시도해주세요."),
    UPSTREAM("UPSTREAM_ERROR", "데이터를 가져오는 중 문제가 생겼어요.");

    companion object {
        /** HTTP 상태코드 → 실패 분류. */
        fun fromHttp(code: Int): FailureKind = when (code) {
            401, 403 -> AUTH
            429 -> RATE_LIMIT
            in 500..599 -> SERVER
            else -> UPSTREAM
        }

        /** 임의 예외 → 실패 분류(네트워크 vs 그 외). */
        fun fromThrowable(t: Throwable): FailureKind = when (t) {
            is ToolFailureException -> t.kind
            is java.io.IOException -> NETWORK
            else -> UPSTREAM
        }
    }
}

/**
 * 도구/API가 실패를 '삼키지 말고' 던지는 단일 예외.
 * ToolExecutor 중앙 catch가 이것을 받아 분류된 에러 JSON으로 변환한다.
 */
class ToolFailureException(
    val kind: FailureKind,
    detail: String? = null
) : Exception(detail ?: kind.userMessage)

/** 분류된 실패를 도구 결과 JSON(에러)으로 표준화. error_kind로 하류가 원인을 안다. */
fun failureJson(kind: FailureKind, detail: String? = null): JSONObject =
    JSONObject().apply {
        put("error", detail ?: kind.userMessage)
        put("error_kind", kind.code)
    }
