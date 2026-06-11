package com.example.ez_capstone.agent

import com.example.ez_capstone.agent.models.AgentResponse

/**
 * 쇼케이스/발표용 데모 모드 응답 저장소.
 *
 * ApiKeyProvider.isDemoMode == true 일 때
 * GeminiAgentEngine.chat() 가 실제 API 호출 대신 이 응답을 반환한다.
 * 네트워크 없이도 완전한 데모 흐름을 보장한다.
 *
 * 트리거 매칭: 가장 먼저 매칭되는 항목 반환.
 * 대소문자 무시, 부분 문자열 매칭.
 */
object DemoResponseRepository {

    data class DemoEntry(
        val triggers: List<String>,
        val response: AgentResponse,
        val label: String  // 발표 스크립트 레이블
    )

    val entries: List<DemoEntry> = listOf(

        // ── Scenario 1: 출근 — 날씨+경로+일정 종합 오케스트레이션 ──
        DemoEntry(
            triggers = listOf("강남역", "강남 가", "강남으로"),
            label = "S1 출근",
            response = AgentResponse(
                replyText = "지금 약하게 비 내리고 있어요. 강남역까지 차로 23분 예상이고, 오후 3시 미팅 전에 충분해요. 경로 안내 시작할까요?",
                ttsText = "비 오는 중, 강남역 23분 예상이에요.",
                uiAction = "show_route",
                uiData = mapOf(
                    "routes" to listOf(
                        mapOf(
                            "distance_m" to 8400,
                            "duration_s" to 1380,
                            "summary" to "강남대로 경유",
                            "fare" to 0,
                            "coords" to listOf(
                                mapOf("lat" to 37.5663, "lng" to 126.9779),
                                mapOf("lat" to 37.5550, "lng" to 126.9850),
                                mapOf("lat" to 37.5200, "lng" to 127.0100),
                                mapOf("lat" to 37.4979, "lng" to 127.0276)
                            ),
                            "guides" to listOf(
                                mapOf("name" to "서울시청 방면", "guidance" to "직진", "type" to 11, "distance" to 500, "duration" to 120, "lat" to 37.5663, "lng" to 126.9779),
                                mapOf("name" to "강남대로", "guidance" to "우회전", "type" to 2, "distance" to 6000, "duration" to 900, "lat" to 37.5200, "lng" to 127.0100),
                                mapOf("name" to "강남역", "guidance" to "도착", "type" to 101, "distance" to 0, "duration" to 0, "lat" to 37.4979, "lng" to 127.0276)
                            )
                        ),
                        mapOf(
                            "distance_m" to 9100,
                            "duration_s" to 1620,
                            "summary" to "올림픽대로 경유",
                            "fare" to 0,
                            "coords" to listOf(
                                mapOf("lat" to 37.5663, "lng" to 126.9779),
                                mapOf("lat" to 37.5300, "lng" to 127.0400),
                                mapOf("lat" to 37.4979, "lng" to 127.0276)
                            ),
                            "guides" to listOf(
                                mapOf("name" to "올림픽대로", "guidance" to "직진", "type" to 11, "distance" to 7000, "duration" to 1200, "lat" to 37.5663, "lng" to 126.9779),
                                mapOf("name" to "강남역", "guidance" to "도착", "type" to 101, "distance" to 0, "duration" to 0, "lat" to 37.4979, "lng" to 127.0276)
                            )
                        )
                    )
                ),
                toolsUsed = listOf("get_weather_kma", "get_directions", "get_schedule")
            )
        ),

        // ── Scenario 2: 집 가자 — 프로필 기반 ──
        DemoEntry(
            triggers = listOf("집 가자", "집에 가", "귀가"),
            label = "S2 귀가",
            response = AgentResponse(
                replyText = "역삼동 집으로 안내할게요. 지금 출발하면 31분 예상이고 퇴근 시간대라 강남대로 정체 있어요. 올림픽대로 우회 추천드려요.",
                ttsText = "집까지 31분, 우회로 추천해요.",
                uiAction = "start_navigation",
                uiData = mapOf(
                    "routes" to listOf(
                        mapOf(
                            "distance_m" to 8400,
                            "duration_s" to 1860,
                            "summary" to "올림픽대로 우회 (추천)",
                            "fare" to 0,
                            "coords" to listOf(
                                mapOf("lat" to 37.4979, "lng" to 127.0276),
                                mapOf("lat" to 37.5100, "lng" to 127.0500),
                                mapOf("lat" to 37.5400, "lng" to 127.0350),
                                mapOf("lat" to 37.5663, "lng" to 126.9779)
                            ),
                            "guides" to listOf(
                                mapOf("name" to "강남역", "guidance" to "출발", "type" to 11, "distance" to 500, "duration" to 60, "lat" to 37.4979, "lng" to 127.0276),
                                mapOf("name" to "올림픽대로", "guidance" to "좌회전", "type" to 1, "distance" to 6000, "duration" to 1500, "lat" to 37.5100, "lng" to 127.0500),
                                mapOf("name" to "역삼동 집", "guidance" to "도착", "type" to 101, "distance" to 0, "duration" to 0, "lat" to 37.5663, "lng" to 126.9779)
                            )
                        )
                    )
                ),
                toolsUsed = listOf("get_user_profile", "get_directions", "get_traffic_speed")
            )
        ),

        // ── Scenario 3: 근처 카페 — 장소 검색 ──
        DemoEntry(
            triggers = listOf("카페", "커피"),
            label = "S3 카페",
            response = AgentResponse(
                replyText = "현재 위치 주변 카페 3곳 찾았어요. 스타벅스 강남역점이 150m로 가장 가까워요.",
                ttsText = "스타벅스 강남역점이 150m 앞에 있어요.",
                uiAction = "show_places",
                uiData = mapOf(
                    "places" to listOf(
                        mapOf("name" to "스타벅스 강남역점", "address" to "서울 강남구 강남대로 390", "lat" to 37.4985, "lng" to 127.0270, "category" to "카페", "distance" to 150),
                        mapOf("name" to "투썸플레이스 강남역사거리점", "address" to "서울 강남구 강남대로 394", "lat" to 37.4982, "lng" to 127.0268, "category" to "카페", "distance" to 210),
                        mapOf("name" to "이디야커피 강남역점", "address" to "서울 강남구 강남대로 388", "lat" to 37.4981, "lng" to 127.0265, "category" to "카페", "distance" to 280)
                    )
                ),
                toolsUsed = listOf("search_places")
            )
        ),

        // ── Scenario 4: 주유소 — 오피넷 연동 ──
        DemoEntry(
            triggers = listOf("주유소", "기름", "주유"),
            label = "S4 주유소",
            response = AgentResponse(
                replyText = "500m 안에 GS칼텍스가 리터당 1,640원으로 가장 저렴해요. 현재 유가 평균보다 30원 낮아요.",
                ttsText = "GS칼텍스 500m, 리터 1,640원이에요.",
                uiAction = "show_cards",
                uiData = mapOf(
                    "card_type" to "gas_stations",
                    "stations" to listOf(
                        mapOf("name" to "GS칼텍스 강남역점", "address" to "서울 강남구 강남대로 지하 396", "price_gasoline" to 1640, "distance" to 480),
                        mapOf("name" to "SK에너지 서초점", "address" to "서울 서초구 서초대로 301", "price_gasoline" to 1658, "distance" to 720),
                        mapOf("name" to "현대오일뱅크 강남점", "address" to "서울 강남구 테헤란로 205", "price_gasoline" to 1671, "distance" to 950)
                    )
                ),
                toolsUsed = listOf("get_gas_stations")
            )
        ),

        // ── Scenario 5: 날씨 + 미세먼지 ──
        DemoEntry(
            triggers = listOf("날씨", "미세먼지", "공기"),
            label = "S5 날씨",
            response = AgentResponse(
                replyText = "현재 서울 기온 12도, 비 내리고 있어요. 미세먼지 '보통'이라 우산만 챙기시면 돼요.",
                ttsText = "기온 12도, 비 와요. 미세먼지 보통이에요.",
                uiAction = "show_cards",
                uiData = mapOf(
                    "card_type" to "weather",
                    "weather" to mapOf(
                        "temp" to 12,
                        "condition" to "rainy",
                        "rain_probability" to 80,
                        "description" to "약한 비",
                        "air_quality" to "보통",
                        "pm10" to 45,
                        "pm25" to 22
                    )
                ),
                toolsUsed = listOf("get_weather_kma", "get_air_quality")
            )
        ),

        // ── Scenario 6: 운전 중 — 사고 확인 ──
        DemoEntry(
            triggers = listOf("사고", "막혀", "정체", "앞에"),
            label = "S6 교통",
            response = AgentResponse(
                replyText = "현재 구간 사고 없어요. 다만 2km 앞 강남대교 구간에서 서행 중이에요. 우회로로 변경할까요?",
                ttsText = "사고 없어요. 2km 앞 서행 중이에요.",
                uiAction = "none",
                uiData = emptyMap(),
                toolsUsed = listOf("get_traffic_incidents", "get_traffic_speed")
            )
        ),

        // ── Scenario 7: 일정 확인 ──
        DemoEntry(
            triggers = listOf("일정", "오늘 뭐", "스케줄", "약속"),
            label = "S7 일정",
            response = AgentResponse(
                replyText = "오늘 오후 3시에 팀 회의, 저녁 7시에 강남 미팅 있어요. 3시 회의 20분 전 출발하면 여유 있어요.",
                ttsText = "3시 팀 회의, 7시 강남 미팅이에요.",
                uiAction = "show_schedule",
                uiData = mapOf(
                    "events" to listOf(
                        mapOf("title" to "팀 회의", "location" to "회사 3층 회의실", "startTime" to "15:00", "endTime" to "16:00"),
                        mapOf("title" to "강남 미팅", "location" to "강남역 스타벅스", "startTime" to "19:00", "endTime" to "20:30")
                    )
                ),
                toolsUsed = listOf("get_schedule")
            )
        )
    )

    /**
     * userText와 매칭되는 데모 응답 반환.
     * 매칭 없으면 null → 실제 Gemini API 호출.
     */
    fun match(userText: String): AgentResponse? {
        val lower = userText.lowercase()
        return entries.firstOrNull { entry ->
            entry.triggers.any { trigger -> lower.contains(trigger.lowercase()) }
        }?.response
    }
}
