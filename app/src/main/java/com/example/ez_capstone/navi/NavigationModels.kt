package com.example.ez_capstone.navi

data class DrivingHudState(
    val speed: Int = 0,
    val remainDistance: Int = 0,
    val remainTime: Int = 0,
    val currentRoadName: String = "",
    val turnDirection: TurnDirectionUi? = null,
    val nextTurnDirection: TurnDirectionUi? = null,
    val safetyAlerts: List<SafetyAlertUi> = emptyList(),
    val aiAlerts: List<AiAlertUi> = emptyList(),
    val trafficSignal: TrafficSignalUi? = null,
    val guideState: NavigationGuideState = NavigationGuideState.INITIALIZING,
    // Phase 7 additions
    val speedCameraAlert: SpeedCameraAlert? = null,
    val roadHazards: List<RoadHazardAlert> = emptyList(),
    val liveScore: DrivingLiveScore? = null
)

data class TurnDirectionUi(
    val directionCode: Int,
    val distance: Int,
    val roadName: String,
    val dirName: String
) {
    val icon: String get() = when (directionCode) {
        0 -> "\u2191"    // ↑ 직진
        1 -> "\u2190"    // ← 좌회전
        2 -> "\u2192"    // → 우회전
        3 -> "\u21BA"    // ↺ U턴
        5 -> "\u2B06"    // ⬆ 고가
        6 -> "\u2B07"    // ⬇ 지하
        11 -> "\uD83D\uDE97" // 차
        12 -> "\uD83C\uDFC1" // 도착
        16 -> "\u2196"   // ↖ 왼쪽 도로
        17 -> "\u2197"   // ↗ 오른쪽 도로
        else -> "\u2191"
    }

    val distanceText: String get() = if (distance >= 1000)
        "%.1fkm".format(distance / 1000.0) else "${distance}m"
}

data class SafetyAlertUi(
    val type: SafetyType,
    val limitSpeed: Int,
    val distance: Int,
    val isOverSpeed: Boolean
)

enum class SafetyType { CAMERA, SECTION_START, SECTION_END, CAUTION, ICY_ROAD, FOG, HEAVY_SNOW }

data class AiAlertUi(
    val id: Long = System.currentTimeMillis(),
    val type: AiAlertType,
    val message: String,
    val actions: List<String> = emptyList(),
    val autoDismissMs: Long = 5000
)

enum class AiAlertType {
    REROUTE, ROAD_EVENT, WAYPOINT_ARRIVAL, ALTERNATIVE_ROUTE, AI_RESPONSE,
    INCIDENT, HAZARD, REST_AREA, PARKING
}

data class TrafficSignalUi(
    val color: String,
    val remainTime: Int
)

enum class NavigationGuideState { INITIALIZING, ACTIVE, REROUTING, ARRIVED, FINISHED }

// TTS 3-stage events
enum class TtsStage { FAR, NEAR, IMMINENT }  // 300m, 100m, 50m

data class TtsEvent(
    val guideIndex: Int,
    val stage: TtsStage,
    val text: String
)

// Driving summary on arrival
data class DrivingSummary(
    val totalDistance: Int = 0,    // meters
    val totalTime: Int = 0,       // seconds
    val avgSpeed: Int = 0         // km/h
)

// Phase 7: 구간단속 카메라 알림
data class SpeedCameraAlert(
    val type: SafetyType,          // CAMERA | SECTION_START | SECTION_END
    val limitSpeed: Int,
    val distanceM: Int,
    val isOverSpeed: Boolean,
    val sectionRemainingM: Int = 0
)

// Phase 7: 도로 위험 알림
enum class HazardType { ICY_ROAD, FOG, HEAVY_SNOW, STRONG_WIND }

data class RoadHazardAlert(
    val hazardType: HazardType,
    val message: String,
    val distanceM: Int,
    val advisorySpeed: Int? = null
)

// Phase 7: 실시간 운전점수
data class DrivingLiveScore(
    val current: Int,              // 100에서 차감
    val speedingCount: Int,
    val hardBrakeCount: Int,
    val sharpTurnCount: Int
)
