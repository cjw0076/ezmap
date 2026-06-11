package com.example.ez_capstone.navi

import java.time.LocalDateTime

/**
 * 경로 상 POI 프로액티브 추천 엔진.
 * 주행 상황에 따라 자동으로 추천 트리거.
 */
object RouteContextEngine {

    private const val COOLDOWN_MS = 30 * 60 * 1000L  // 30분 쿨다운
    private val lastSuggestionTime = mutableMapOf<SuggestionType, Long>()

    fun resetCooldowns() { lastSuggestionTime.clear() }

    enum class SuggestionType {
        REST_STOP,       // 장시간 주행 → 휴게소
        MEAL,            // 식사 시간 → 맛집
        PARKING,         // 도착 임박 → 주차장
        GAS_STATION,     // 주유 필요 → 주유소
        EV_CHARGER,      // 충전 필요 → 충전소
        WEATHER_WARNING  // 기상 악화 → 안전 안내
    }

    data class ProactiveSuggestion(
        val type: SuggestionType,
        val message: String,
        val agentQuery: String  // Agent에 전달할 자연어 쿼리
    )

    /**
     * 현재 주행 상태로 트리거 조건 체크.
     * @param drivingMinutes 연속 주행 시간(분)
     * @param remainingMinutes 목적지까지 남은 시간(분)
     * @param fuelType 연료 타입 (gasoline/diesel/electric/lpg)
     */
    fun checkTriggers(
        drivingMinutes: Int,
        remainingMinutes: Int,
        fuelType: String = "gasoline"
    ): List<ProactiveSuggestion> {
        val suggestions = mutableListOf<ProactiveSuggestion>()
        val now = LocalDateTime.now()
        val hour = now.hour

        // 2시간 이상 연속 주행 → 휴게소 추천
        if (drivingMinutes >= 120) {
            suggestions.add(ProactiveSuggestion(
                type = SuggestionType.REST_STOP,
                message = "2시간 넘게 운전하셨어요. 잠시 쉬어가시겠어요?",
                agentQuery = "근처 휴게소 또는 쉴 수 있는 곳 검색해줘"
            ))
        }

        // 식사 시간대 (11:30~13:00, 17:30~19:00) + 30분 이상 남음
        val isMealTime = (hour == 11 && now.minute >= 30) || hour == 12 ||
                (hour == 17 && now.minute >= 30) || hour == 18
        if (isMealTime && remainingMinutes > 30) {
            suggestions.add(ProactiveSuggestion(
                type = SuggestionType.MEAL,
                message = "식사 시간이에요. 근처 맛집 검색해드릴까요?",
                agentQuery = "경로 근처 맛집 추천해줘"
            ))
        }

        // 목적지 5분 전 → 주차장
        if (remainingMinutes in 1..5) {
            suggestions.add(ProactiveSuggestion(
                type = SuggestionType.PARKING,
                message = "곧 도착해요. 근처 주차장을 찾아볼까요?",
                agentQuery = "목적지 근처 주차장 검색해줘"
            ))
        }

        // 전기차 → 충전소 (장거리)
        if (fuelType == "electric" && drivingMinutes >= 90) {
            suggestions.add(ProactiveSuggestion(
                type = SuggestionType.EV_CHARGER,
                message = "충전이 필요할 수 있어요. 근처 충전소를 확인할까요?",
                agentQuery = "근처 전기차 충전소 중 충전 가능한 곳 알려줘"
            ))
        }

        // 주유 알림 (가솔린/디젤/LPG, 90분 이상)
        if (fuelType in listOf("gasoline", "diesel", "lpg") && drivingMinutes >= 90) {
            suggestions.add(ProactiveSuggestion(
                type = SuggestionType.GAS_STATION,
                message = "주유가 필요할 수 있어요. 근처 최저가 주유소를 찾아볼까요?",
                agentQuery = "근처 싼 주유소 알려줘"
            ))
        }

        // 쿨다운 필터: 30분 이내 같은 타입 제거
        val currentTimeMs = System.currentTimeMillis()
        return suggestions.filter { suggestion ->
            val lastTime = lastSuggestionTime[suggestion.type] ?: 0L
            if (currentTimeMs - lastTime > COOLDOWN_MS) {
                lastSuggestionTime[suggestion.type] = currentTimeMs
                true
            } else false
        }
    }
}
