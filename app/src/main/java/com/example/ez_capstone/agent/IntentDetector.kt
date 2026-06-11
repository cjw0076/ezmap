package com.example.ez_capstone.agent

import android.util.Log

/**
 * 사용자 발화 → IntentCategory 분류.
 *
 * LLM 호출 없이 키워드 매칭으로만 동작한다 (0ms, 추가 API 비용 없음).
 * 불명확하거나 복합 의도이면 AUTO를 반환하여 전체 Tool을 사용한다.
 *
 * 점수 체계:
 *   - ANCHOR_KW 키워드 1개라도 포함 → 해당 카테고리 점수 2 (즉시 확정 가능)
 *   - 일반 키워드: 일치 개수만큼 점수 누적
 *   - MIN_SCORE=2 이상이어야 해당 intent 확정, 미만이면 AUTO
 */
object IntentDetector {

    private const val TAG = "IntentDetector"
    private const val MIN_SCORE = 2

    // ── 앵커 키워드 (단독 출현 시 해당 카테고리 확정) ──
    private val ANCHOR_KW = mapOf(
        IntentCategory.NAVIGATION    to setOf(
            "가줘", "가자", "데려다줘", "안내해줘", "경로", "내비",
            // 흔한 경로 표현 — 누락 시 AUTO로 빠져 툴 44개가 전부 실리는 비용 발생
            "가는 길", "가는길", "길 알려", "길안내", "길 안내",
            "어떻게 가", "어떻게가", "가려면", "가는 방법",
            "가고 싶", "가고싶", "데려다", "태워"
        ),
        IntentCategory.TRAFFIC       to setOf(
            "막혀", "정체", "사고났", "막히나", "막히는지",
            "교통상황", "교통 상황", "교통상태"
        ),
        IntentCategory.PLACES        to setOf(
            "맛집", "약국", "응급실", "주유소", "충전소", "충전기", "주차장"
        ),
        IntentCategory.COMMUNICATION to setOf(
            "전화해", "전화해줘", "카톡해", "카톡해줘", "문자해", "문자해줘", "알람",
            // "연락처" 단독으로 COMMUNICATION 확정 → "연락처에 OO 있어?"가 AUTO로 새지 않고
            // 강제 그라운딩(lookup_contact 호출)을 받게 한다(존재확인 환각 방지의 핵심).
            "연락처"
        ),
        IntentCategory.SCHEDULE      to setOf(
            "일정 추가", "일정 삭제", "일정 변경", "일정 취소", "일정 등록",
            "오늘 일정", "내일 일정", "이번 주 일정", "다음 일정",
            // "내 일정 알려줘"류가 점수 미달로 AUTO로 새지 않게 (동작은 하나 분류 일관성 확보)
            "내 일정", "일정 알려", "일정 뭐", "일정 있어", "일정 보여"
        ),
        IntentCategory.INFO          to setOf(
            "날씨", "미세먼지", "환율", "대기질"
        ),
        IntentCategory.PROFILE       to setOf(
            "집 주소", "회사 주소", "주소 등록", "주소 변경", "내 차", "즐겨찾기 추가"
        ),
        IntentCategory.MUSIC         to setOf(
            "노래 틀", "음악 틀", "노래 켜", "음악 켜", "노래 꺼", "음악 꺼",
            "노래 멈춰", "음악 멈춰", "다음 곡", "다음 노래", "이전 곡", "이전 노래",
            "재생해줘", "틀어줘", "들려줘", "무슨 노래"
        ),
    )

    // ── 일반 키워드 (2개 이상 매칭 시 카테고리 확정) ──
    private val REGULAR_KW = mapOf(
        IntentCategory.NAVIGATION to setOf(
            "안내", "출발", "도착", "지하철", "버스", "대중교통", "택시",
            "걸려", "걸리나", "얼마나", "몇 분", "들러", "경유", "우회",
            "킬로", "km", "미터", "방향"
        ),
        IntentCategory.TRAFFIC to setOf(
            "사고", "공사", "통제", "속도", "소통",
            "고속도로", "고속", "경부", "올림픽대로", "강변북로", "카메라", "단속",
            "휴게소", "돌발", "위험구간"
        ),
        IntentCategory.PLACES to setOf(
            "카페", "식당", "음식점", "빵집", "편의점",
            "병원", "치과", "전기차",
            "찾아줘", "어디 있어", "근처", "주변"
        ),
        IntentCategory.COMMUNICATION to setOf(
            "전화", "연락", "문자", "메시지", "보내줘", "보내",
            "알림", "일어나", "기상", "잊지 말게"
        ),
        IntentCategory.SCHEDULE to setOf(
            "일정", "약속", "미팅", "회의", "예약", "이벤트",
            "추가해", "삭제해", "변경해", "취소해", "등록해",
            "오늘 뭐", "내일 뭐", "이번 주", "다음 주", "언제"
        ),
        IntentCategory.INFO to setOf(
            "비", "눈", "기온", "온도", "습도", "바람",
            "공기", "대기",
            "달러", "유로", "엔화",
            "뭐야", "높이", "역사", "검색해"
        ),
        IntentCategory.PROFILE to setOf(
            "차량", "연료", "하이패스",
            "즐겨찾기", "선호", "좋아해", "싫어해", "설정",
            "패턴", "분석", "기억", "뭘 기억", "노트", "메모"
        ),
        IntentCategory.MUSIC to setOf(
            // "다음"·"이전" 단독은 SCHEDULE("다음 주")·NAVIGATION과 충돌 → 제외.
            // "다음 곡/노래", "이전 곡/노래"는 ANCHOR_KW에 이미 있어 커버됨.
            "노래", "음악", "곡", "재생", "정지", "일시정지", "볼륨",
            "플레이", "트랙", "스포티파이"
        ),
    )

    // ── 복합 의도 패턴 (매칭 시 AUTO) ──
    private val COMPOSITE_PATTERNS = listOf(
        Regex("(가면서|가는 길에|가다가).*(날씨|일정|맛집|카페|약국|주유소|충전소|충전기|주차장|편의점|화장실|휴게소|은행|atm)"),
        Regex("(날씨|일정|맛집).*(가줘|안내|경로)"),
        Regex("비교|카카오랑 네이버|네이버랑 카카오"),
        Regex("(막혀|정체|사고).*(가줘|경로|안내)"),
        Regex("(가줘|경로).*(막혀|정체|사고)"),
        Regex("(일정|약속).*(전화|문자|연락|카톡)"),
        Regex("(맛집|카페|약국).*(가줘|안내|경로)"),
        Regex("(가줘|경로).*(들러|경유).*(맛집|카페)"),
    )

    /**
     * 발화 텍스트를 분석하여 IntentCategory 반환.
     * @param text 사용자 발화 (STT 결과)
     * @param drivingState 현재 주행 상태. 의도 판별에는 영향을 주지 않는다(아래 주석 참고).
     *
     * 주행 중에도 idle과 '동일한' 전체 의도 판별을 사용한다.
     * (과거: 주행 중엔 앵커 키워드 없는 모든 발화를 NAVIGATION으로 강제 → "파리 수도는?"을
     *  목적지로 geocode하거나, "지수한테 문자보내줘"에서 send_message 도구가 빠져,
     *  집에선 되던 기능이 주행 중 전부 '엉뚱한 경로 답/찾을 수 없음'으로 실패했다.)
     * 주행 안전 도구(DRIVING_EXTENDED)는 ToolDeclarations가 drivingState로 별도 추가하고,
     * 답변 길이 제한·안전은 SystemPrompt의 안전 규칙이 담당한다 — 둘 다 의도 분류와 무관.
     */
    fun detect(text: String, drivingState: String = "idle"): IntentCategory {
        for (pattern in COMPOSITE_PATTERNS) {
            if (pattern.containsMatchIn(text)) {
                Log.d(TAG, "Composite pattern matched -> AUTO: ${pattern.pattern}")
                return IntentCategory.AUTO
            }
        }

        val scores = IntentCategory.entries
            .filter { it != IntentCategory.AUTO }
            .associateWith { cat -> computeScore(text, cat) }

        val topTwo = scores.entries.sortedByDescending { it.value }.take(2)
        val best = topTwo[0]
        val second = topTwo.getOrNull(1)

        if (best.value < MIN_SCORE) {
            Log.d(TAG, "Score ${best.value} < MIN_SCORE $MIN_SCORE -> AUTO")
            return IntentCategory.AUTO
        }

        if (second != null && best.value == second.value) {
            Log.d(TAG, "Tie ${best.key}=${best.value} == ${second.key} -> AUTO")
            return IntentCategory.AUTO
        }

        Log.d(TAG, "Intent: ${best.key} (score=${best.value})")
        return best.key
    }

    private fun computeScore(text: String, category: IntentCategory): Int {
        val anchors = ANCHOR_KW[category] ?: emptySet()
        if (anchors.any { text.contains(it) }) return MIN_SCORE
        return (REGULAR_KW[category] ?: emptySet()).count { text.contains(it) }
    }
}
