package com.example.ez_capstone.agent.models

/**
 * Gemini Agent의 최종 응답.
 * ViewModel이 이 객체를 받아서 AgentUiState로 변환한다.
 */
data class AgentResponse(
    val replyText: String,
    val ttsText: String? = null,
    val uiAction: String = "none",
    val uiData: Map<String, Any?> = emptyMap(),
    val toolsUsed: List<String> = emptyList(),
    // 의사결정 근거 요약(설명가능성). 예: "search_places → get_directions 호출 (대체 1회) — 820ms"
    val explanation: String? = null
) {
    /**
     * TTS용 핵심 요약 추출.
     * reply_text의 첫 문장을 voiceSummary로 사용 (운전 중).
     */
    val voiceSummary: String
        get() {
            val text = ttsText ?: replyText
            val delimiters = listOf(". ", "。", "! ", "? ")
            var earliest = text.length
            for (d in delimiters) {
                val idx = text.indexOf(d)
                if (idx in 0 until earliest) earliest = idx + d.length
            }
            return if (earliest < text.length) text.substring(0, earliest).trim() else text.trim()
        }
}

/**
 * Agent 호출 시 전달하는 현재 상태 컨텍스트.
 */
object AgentDrivingState {
    const val IDLE = "idle"
    const val NAVIGATING = "navigating"

    fun normalize(value: String?): String = when (value?.trim()?.lowercase()) {
        NAVIGATING, "driving" -> NAVIGATING
        else -> IDLE
    }
}

data class AgentContext(
    val locationX: Double? = null,  // longitude
    val locationY: Double? = null,  // latitude
    val drivingState: String = AgentDrivingState.IDLE,
    // 주행 중 현재 목적지 좌표 — "더 빠른 길로/다른 길로" 재탐색에 사용
    val destX: Double? = null,      // 목적지 longitude
    val destY: Double? = null,      // 목적지 latitude
    // STT N-best 대안 후보(1순위 제외). 1순위가 맥락상 어색하면 에이전트가 이 중에서 재해석.
    val sttAlternatives: List<String> = emptyList()
)

/**
 * Kakao 장소 검색 결과.
 */
data class PlaceResult(
    val name: String,
    val address: String,
    val category: String,
    val lat: Double,
    val lng: Double,
    val distanceM: Int? = null,
    val phone: String? = null
)

/**
 * 지오코딩 결과.
 */
data class GeoResult(
    val address: String,
    val lat: Double,
    val lng: Double
)

/**
 * 역지오코딩 결과.
 */
data class ReverseGeoResult(
    val address: String?,
    val roadAddress: String?
)

/**
 * 카카오 경로 탐색 결과.
 * 기존 RouteItem과 호환되도록 coords/guides를 포함.
 */
data class DirectionsRoute(
    val coords: List<CoordData>,
    val guides: List<GuideData>,
    val distanceM: Int,
    val durationS: Int,
    val fare: Int? = null,
    val taxiFare: Int? = null
)

data class CoordData(val lat: Double, val lng: Double)

data class GuideData(
    val name: String,
    val guidance: String,
    val type: Int,
    val lat: Double,
    val lng: Double,
    val distance: Int,
    val duration: Int
)

/**
 * 경유지 파라미터.
 */
data class WaypointParam(val name: String, val lng: Double, val lat: Double)

/**
 * 미래 ETA 결과.
 */
data class FutureETAResult(
    val durationS: Int,
    val distanceM: Int,
    val departureTime: String
)

/**
 * 날씨 결과.
 */
data class WeatherResult(
    val temp: Int? = null,
    val condition: String = "unknown",
    val rainProbability: Int? = null,
    val windSpeed: Double? = null,
    val description: String = "날씨 정보 없음",
    val roadCondition: String = "normal"
)
