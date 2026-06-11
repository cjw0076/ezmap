package com.example.ez_capstone.agent

/**
 * 그라운딩 위반 감지기 — 순수 함수(SDK/안드로이드 의존 0 → JUnit으로 잠금).
 *
 * 정의: 응답이 "추가/저장/보냈…했습니다" 같은 **완료(쓰기 성공) 주장**을 하는데
 * 이번 턴에 성공한 **WRITE 도구가 하나도 없으면** = 도구 없이 지어낸 거짓 = 위반.
 *
 * 용도(사용자 합의): **관측/측정 전용**. 응답을 "못한다/없다"로 바꾸는 차단은 하지 않는다
 * (그건 거절 기계가 됨). 근본 처방은 강제 그라운딩(mode=ANY)이 도구 실행을 보장하는 것이고,
 * 이 가드는 그게 안 걸린 잔여 거짓을 **로그로 가시화**해 회귀를 막는 게이트 역할.
 *
 * 고정밀 설계: 상태 묘사("저장되어 있어요")는 제외, 완료 주장("저장했습니다")만 잡는다.
 * 거짓음성(일부 놓침)은 허용 — 로깅이라 비용 낮음.
 */
object GroundingGuard {

    /** 상태를 변경하는(쓰기) 도구 — 이 중 하나라도 이번 턴 성공해야 완료 주장이 정당.
     *  단일 진실원천 ToolCatalog에서 파생(WRITE ∪ SENSITIVE). */
    val WRITE_TOOLS: Set<String> get() = ToolCatalog.STATE_CHANGING

    // 두 형태를 구분: ① 노운+하다("추가"→"추가했습니다") ② 이미 과거형 어간("보내"→"보냈습니다").
    // 수동 상태("저장되어 있어요")는 어미(되어…)가 목록에 없어 제외 → 완료 주장만 잡힘.
    private val COMPLETION_CLAIM = Regex(
        // ① 노운+하다 (어간과 어미 사이 공백 1개까지 허용: "저장 완료")
        "(추가|저장|등록|삭제|변경|수정|전송|발송|예약|설정|연결|해제|전화)\\s?(했어요|했습니다|했어|완료)" +
            // ② 이미 과거형 어간
            "|(보냈|보내드렸|맞췄|바꿨|껐|켰|뒀|놨|저장했|등록했)(어요|습니다|어)"
    )

    /** 응답이 완료(쓰기 성공)를 주장하는가. */
    fun claimsCompletion(reply: String): Boolean = COMPLETION_CLAIM.containsMatchIn(reply)

    /**
     * 위반 여부: 완료 주장 + 이번 턴 성공 WRITE 도구 0개.
     * @param succeededTools 이번 턴 성공(에러 없이 실행)한 도구 이름 집합.
     */
    fun isViolation(reply: String, succeededTools: Set<String>): Boolean =
        claimsCompletion(reply) && succeededTools.none { it in WRITE_TOOLS }
}
