package com.example.ez_capstone.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GroundingGuard 회귀 게이트 — "도구 없이 완료를 지어내면 위반"이라는 정의를 잠근다.
 * 이게 깨지면 빌드 실패 → 환각 측정 기준이 회귀하지 않음.
 */
class GroundingGuardTest {

    // ── 완료 주장 감지 ──

    @Test fun `완료 주장 감지`() {
        assertTrue(GroundingGuard.claimsCompletion("진동석 교수님을 추가했습니다"))
        assertTrue(GroundingGuard.claimsCompletion("일정을 저장했어요"))
        assertTrue(GroundingGuard.claimsCompletion("문자를 보냈습니다"))
        assertTrue(GroundingGuard.claimsCompletion("알람을 설정했어요"))
    }

    @Test fun `상태 묘사와 질문은 완료 주장 아님`() {
        assertFalse(GroundingGuard.claimsCompletion("연락처에 진동섭 교수님 있습니다"))
        assertFalse(GroundingGuard.claimsCompletion("집이 저장되어 있어요"))      // 수동 상태
        assertFalse(GroundingGuard.claimsCompletion("추가 정보를 알려드릴게요"))  // "추가"+명사
        assertFalse(GroundingGuard.claimsCompletion("경로를 안내할게요"))         // 미래
        assertFalse(GroundingGuard.claimsCompletion(""))
    }

    // ── 위반 판정 ──

    @Test fun `완료주장 + WRITE도구 0개 = 위반`() {
        // 보고된 버그: "있어?"에 lookup_contact(READ)만 부르고 "추가했습니다"
        assertTrue(GroundingGuard.isViolation("진동섭 교수님을 추가했습니다", setOf("lookup_contact")))
        assertTrue(GroundingGuard.isViolation("저장했어요", emptySet()))
    }

    @Test fun `완료주장 + 해당 WRITE도구 성공 = 정당(위반 아님)`() {
        assertFalse(GroundingGuard.isViolation("연락처에 추가했습니다", setOf("manage_contacts")))
        assertFalse(GroundingGuard.isViolation("일정을 등록했어요", setOf("create_schedule")))
    }

    @Test fun `완료주장 없으면 도구 없어도 위반 아님`() {
        assertFalse(GroundingGuard.isViolation("연락처에 진동섭 교수님 있습니다", setOf("lookup_contact")))
        assertFalse(GroundingGuard.isViolation("오늘 일정은 본가 방문이에요", setOf("get_schedule")))
    }

    @Test fun `WRITE_TOOLS는 상태변경 도구만 포함`() {
        assertTrue("manage_contacts" in GroundingGuard.WRITE_TOOLS)
        assertTrue("send_message" in GroundingGuard.WRITE_TOOLS)
        assertFalse("lookup_contact" in GroundingGuard.WRITE_TOOLS)  // READ
        assertFalse("get_schedule" in GroundingGuard.WRITE_TOOLS)    // READ
        assertFalse("search_places" in GroundingGuard.WRITE_TOOLS)   // READ
    }
}
