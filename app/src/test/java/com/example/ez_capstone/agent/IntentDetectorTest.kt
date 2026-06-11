package com.example.ez_capstone.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * IntentDetector 회귀 게이트 (Phase 5 "유연성 게이트 안").
 *
 * IntentDetector는 LLM 없이 키워드로 발화→카테고리를 정해 Gemini에 실을 Tool 집합을 좁힌다.
 * AUTO면 전체 44개 Tool이 실려 토큰/지연 비용↑. 따라서 분류 정확도는 비용·정합성 둘 다에 직결.
 * 키워드 추가/수정 시 이 게이트가 깨지면 빌드 실패 → 안전하게 유연성 확장 가능.
 *
 * 발화 다수는 2026-05-26 실기기에서 실제로 동작 확인한 것.
 */
class IntentDetectorTest {

    private fun detect(text: String, driving: String = "idle") =
        IntentDetector.detect(text, driving)

    // ── 앵커: 단독 출현 시 즉시 확정 ──

    @Test fun `주차장 약국 주유소는 PLACES`() {
        assertEquals(IntentCategory.PLACES, detect("근처 주차장 알려줘"))
        assertEquals(IntentCategory.PLACES, detect("근처 약국 찾아줘"))
        assertEquals(IntentCategory.PLACES, detect("가까운 주유소"))
    }

    @Test fun `경로 표현은 NAVIGATION`() {
        assertEquals(IntentCategory.NAVIGATION, detect("차로 울산대공원까지 안내해줘"))
        assertEquals(IntentCategory.NAVIGATION, detect("서울역까지 어떻게 가"))
        assertEquals(IntentCategory.NAVIGATION, detect("집에 가자"))
        assertEquals(IntentCategory.NAVIGATION, detect("강남역 가는 길"))
    }

    @Test fun `대중교통 표현도 NAVIGATION`() {
        // "지하철/버스/대중교통"은 REGULAR지만 "어떻게 가" 앵커와 결합 → NAVIGATION
        assertEquals(IntentCategory.NAVIGATION, detect("서울역 지하철로 어떻게 가"))
    }

    @Test fun `날씨 미세먼지 환율은 INFO`() {
        assertEquals(IntentCategory.INFO, detect("오늘 날씨 어때"))
        assertEquals(IntentCategory.INFO, detect("지금 미세먼지 어때"))
        assertEquals(IntentCategory.INFO, detect("환율 알려줘"))
    }

    @Test fun `일정 앵커는 SCHEDULE`() {
        assertEquals(IntentCategory.SCHEDULE, detect("오늘 일정 알려줘"))
        assertEquals(IntentCategory.SCHEDULE, detect("내일 오후 3시에 치과 예약 일정 추가해줘"))
        // H7: "내 일정 알려줘"가 점수 미달 AUTO로 새지 않고 SCHEDULE로 분류
        assertEquals(IntentCategory.SCHEDULE, detect("내 일정 알려줘"))
    }

    @Test fun `전화 문자 알람은 COMMUNICATION`() {
        assertEquals(IntentCategory.COMMUNICATION, detect("엄마한테 전화해줘"))
        assertEquals(IntentCategory.COMMUNICATION, detect("문자해줘"))
    }

    @Test fun `연락처 존재확인은 COMMUNICATION(강제 그라운딩 대상)`() {
        // AUTO로 새면 강제 그라운딩이 안 걸려 lookup_contact 호출이 LLM 선택에 의존 → 환각 위험.
        assertEquals(IntentCategory.COMMUNICATION, detect("연락처에 진동섭 교수님 있어?"))
        assertEquals(IntentCategory.COMMUNICATION, detect("연락처에 엄마 있나?"))
    }

    @Test fun `교통 앵커는 TRAFFIC`() {
        assertEquals(IntentCategory.TRAFFIC, detect("앞에 막혀?"))
        assertEquals(IntentCategory.TRAFFIC, detect("사고났어?"))
        // H7: "교통상황"이 AUTO로 새지 않고 TRAFFIC으로 분류
        assertEquals(IntentCategory.TRAFFIC, detect("지금 교통상황 어때"))
        assertEquals(IntentCategory.TRAFFIC, detect("지금 교통 상황 어때", driving = "navigating"))
    }

    // ── 복합/불명확 → AUTO (전체 Tool 로드) ──

    @Test fun `경유 복합 의도는 AUTO`() {
        assertEquals(IntentCategory.AUTO, detect("회사 가는 길에 약국 들러"))
        assertEquals(IntentCategory.AUTO, detect("사고 구간이면 우회 경로로 안내해줘"))
    }

    @Test fun `듀얼 경로 비교는 AUTO`() {
        assertEquals(IntentCategory.AUTO, detect("카카오랑 네이버 비교해줘"))
    }

    @Test fun `점수 미달은 AUTO`() {
        // 지식 질문: 전용 카테고리 없음 → AUTO로 전체 Tool(search_knowledge 포함)
        assertEquals(IntentCategory.AUTO, detect("에펠탑에 대해 알려줘"))
        assertEquals(IntentCategory.AUTO, detect("그거 해줘"))
    }

    // ── 주행 중(navigating): idle과 '동일한' 전체 의도 판별 (주행이 분류를 좁히지 않음) ──

    @Test fun `주행 중에도 앵커 없는 일반 발화는 AUTO (NAVIGATION 강제 금지)`() {
        // 회귀 방지: 과거엔 주행 중 앵커 없는 발화를 NAVIGATION으로 강제 → "파리 수도는?"을
        // 목적지로 geocode하거나 send_message 도구가 빠져 집에선 되던 기능이 전부 실패했다.
        assertEquals(IntentCategory.AUTO, detect("파리 수도는?", driving = "navigating"))
        assertEquals(IntentCategory.AUTO, detect("에펠탑에 대해 알려줘", driving = "navigating"))
        assertEquals(IntentCategory.AUTO, detect("저 앞에서", driving = "navigating"))
    }

    @Test fun `주행 중 문자 보내기는 COMMUNICATION (send_message 도구 확보)`() {
        // "문자보내줘"는 앵커가 아니지만 REGULAR(문자+보내+보내줘)로 COMMUNICATION 확정.
        assertEquals(IntentCategory.COMMUNICATION, detect("지수한테 문자보내줘", driving = "navigating"))
    }

    @Test fun `주행 중 경로 요청은 NAVIGATION`() {
        assertEquals(IntentCategory.NAVIGATION, detect("울산대공원까지 안내해줘", driving = "navigating"))
    }

    @Test fun `주행 중에도 날씨 일정 문자 앵커는 정상`() {
        assertEquals(IntentCategory.INFO, detect("날씨 어때", driving = "navigating"))
        assertEquals(IntentCategory.SCHEDULE, detect("오늘 일정 뭐 있지", driving = "navigating"))
        assertEquals(IntentCategory.COMMUNICATION, detect("집에 전화해줘", driving = "navigating"))
    }

    @Test fun `빈 발화는 AUTO`() {
        assertEquals(IntentCategory.AUTO, detect(""))
    }

    // ── 기능 명세(realdevice_userflow) 주행 중 라우팅 회귀 락 ──
    // 핵심 불변식: 비-내비 명령은 주행 중에도 NAVIGATION으로 오분류되지 않는다.
    // (과거 버그: 주행 중 앵커 없는 발화를 NAVIGATION으로 강제 → "파리 수도/문자보내줘"를
    //  목적지로 길찾기 시도하며 전 기능이 실패. 이 락이 그 회귀를 빌드 단계에서 차단한다.)
    // NAVIGATION이 아니기만 하면 도구 집합이 올바르거나(특정 intent) 전체(AUTO)라 기능 동작.

    @Test fun `주행 중 비-내비 기능 명령은 NAVIGATION으로 새지 않는다`() {
        val nonNav = listOf(
            "근처 카페 찾아줘", "오늘 날씨 어때", "지금 미세먼지 어때",
            "근처 주유소 찾아줘", "근처 전기차 충전소 찾아줘", "근처 주차장 알려줘",
            "근처 약국 찾아줘", "근처 병원 찾아줘", "에펠탑에 대해 알려줘",
            "달러 환율 알려줘", "파리 수도는?",
            "내일 오후 3시에 팀 회의 일정 추가해줘",
            "지수한테 문자보내줘", "음악 틀어줘",
        )
        for (cmd in nonNav) {
            assertNotEquals("주행 중 '$cmd' 가 NAVIGATION으로 오분류됨",
                IntentCategory.NAVIGATION, detect(cmd, driving = "navigating"))
        }
    }

    @Test fun `주행 중 실제 경로 명령은 NAVIGATION`() {
        assertEquals(IntentCategory.NAVIGATION, detect("울산대공원으로 가는 길 알려줘", driving = "navigating"))
        assertEquals(IntentCategory.NAVIGATION, detect("울산대공원까지 대중교통으로 어떻게 가", driving = "navigating"))
    }
}
