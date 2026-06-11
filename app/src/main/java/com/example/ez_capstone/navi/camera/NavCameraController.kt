package com.example.ez_capstone.navi.camera

import kotlin.math.abs

/** 동적 카메라 입력 (GPS틱마다 RestGuideEngine hudState에서 환산). */
data class NavCameraInput(
    val speedKmh: Float,
    val bearingDeg: Float,
    val distToTurnM: Int? = null,   // 다음 턴까지 거리. null = 임박 턴 없음
    val isPanning: Boolean = false  // 사용자가 지도 조작 중 → 동적 카메라 동결
)

/** 카메라에 적용할 목표 상태(SDK 무관). */
data class NavCameraTarget(
    val zoom: Float,
    val tiltDeg: Double,
    val bearingDeg: Float
)

/**
 * 동적 맵 카메라 정책 — 순수 함수.
 *
 * 설계(3역할 토론 합의): "여러 입력 → 단일 목표로 합성 후 평활".
 * 줌 커브보다 **평활/클램프**가 체감을 지배 → 연속 보간(밴드 경계 깜빡임 X) +
 * step당 변화율 클램프(펌핑/숨쉬기 방지) + 데드존(미세 떨림 차단) + 저속 bearing freeze.
 *
 * SDK/안드로이드 의존 0 → JUnit으로 잠그고 주행 시뮬레이터(debugFeedLocation)로 상수 튜닝.
 * 적용은 KakaoMapView가 NavCameraTarget만 받아 수행(A3).
 */
object NavCameraController {

    // ── 튜닝 상수 (시뮬레이터로 조정) ──
    // Kakao 줌은 높을수록 가까움. 주행은 street-level(17~18.5). 기존 고정값 17보다
    // 멀어지면 "전지적 시점"이 되어 운행 불가 → 17 미만으로 내려가지 않게 한다.
    const val ZOOM_MIN = 17f            // 고속에서도 17(기존 고정값) 이하로 멀어지지 않음
    const val ZOOM_MAX = 18.5f          // 저속/정지에서 가깝게 줌인
    const val ZOOM_SPEED_CAP_KMH = 100f // 이 속도 이상은 ZOOM_MIN 고정
    const val TILT_MIN = 40.0           // 저속(너무 top-down하지 않게 상향)
    const val TILT_MAX = 55.0           // 고속(전방 원경감)
    const val DEFAULT_TILT = 45.0
    const val MANEUVER_RANGE_M = 300f   // 이 거리 안에서 턴 줌인 시작
    const val MANEUVER_BASE_ZOOM = 18f  // 줌인 시작 줌
    const val MANEUVER_PEAK_ZOOM = 19f  // 턴 코앞(교차로) 가깝게
    const val BEARING_FREEZE_KMH = 5f   // 이하 속도면 회전 동결(정지 시 빙빙 방지)
    const val MAX_ZOOM_DELTA = 0.3f     // step(≈1s)당 줌 변화 한계
    const val MAX_TILT_DELTA = 2.0      // step당 틸트 변화 한계

    /** 속도(km/h) → 목표 줌. 연속 보간 → 밴드 경계 깜빡임 없음. */
    fun speedToZoom(speedKmh: Float): Float {
        val t = (speedKmh / ZOOM_SPEED_CAP_KMH).coerceIn(0f, 1f)
        return ZOOM_MAX - (ZOOM_MAX - ZOOM_MIN) * t
    }

    /** 다음 턴까지 거리 → 줌인 목표(가까울수록 줌인). 범위 밖/없으면 null. */
    fun maneuverZoom(distToTurnM: Int?): Float? {
        if (distToTurnM == null || distToTurnM > MANEUVER_RANGE_M) return null
        val t = (1f - distToTurnM / MANEUVER_RANGE_M).coerceIn(0f, 1f) // 0(멀리)~1(코앞)
        return MANEUVER_BASE_ZOOM + (MANEUVER_PEAK_ZOOM - MANEUVER_BASE_ZOOM) * t
    }

    /** 속도 → 목표 틸트. */
    fun speedToTilt(speedKmh: Float): Double {
        val t = (speedKmh / ZOOM_SPEED_CAP_KMH).coerceIn(0f, 1f).toDouble()
        return TILT_MIN + (TILT_MAX - TILT_MIN) * t
    }

    /**
     * 입력 + 직전 타깃 → 평활/클램프된 다음 타깃.
     * @param prev 직전 적용 타깃(없으면 첫 프레임).
     */
    fun compute(input: NavCameraInput, prev: NavCameraTarget?): NavCameraTarget {
        // 사용자 패닝 중엔 동적 카메라 동결(직전 유지)
        if (input.isPanning && prev != null) return prev

        val speedZoom = speedToZoom(input.speedKmh)
        val turnZoom = maneuverZoom(input.distToTurnM)
        // maneuver vs speed = max() (합산 금지 → 진동 방지)
        val targetZoom = if (turnZoom != null) maxOf(speedZoom, turnZoom) else speedZoom
        val targetTilt = speedToTilt(input.speedKmh)
        // 저속 bearing freeze (정지 시 GPS bearing 노이즈로 맵이 도는 것 방지)
        val targetBearing = if (input.speedKmh < BEARING_FREEZE_KMH && prev != null) prev.bearingDeg else input.bearingDeg

        if (prev == null) return NavCameraTarget(targetZoom, targetTilt, targetBearing)

        // step당 변화율 클램프 → 펌핑/숨쉬기 방지
        return NavCameraTarget(
            zoom = clampDelta(prev.zoom, targetZoom, MAX_ZOOM_DELTA),
            tiltDeg = clampDelta(prev.tiltDeg, targetTilt, MAX_TILT_DELTA),
            bearingDeg = targetBearing
        )
    }

    /** 데드존: 직전과 거의 같으면 SDK 적용 스킵(미세 떨림 차단). */
    fun shouldApply(prev: NavCameraTarget?, next: NavCameraTarget): Boolean {
        if (prev == null) return true
        return abs(prev.zoom - next.zoom) >= 0.05f ||
            abs(prev.tiltDeg - next.tiltDeg) >= 0.5 ||
            angleDiffDeg(prev.bearingDeg, next.bearingDeg) >= 2f
    }

    private fun clampDelta(prev: Float, target: Float, maxDelta: Float): Float =
        prev + (target - prev).coerceIn(-maxDelta, maxDelta)

    private fun clampDelta(prev: Double, target: Double, maxDelta: Double): Double =
        prev + (target - prev).coerceIn(-maxDelta, maxDelta)

    /** 0~360° 각도 최소 차(경계 처리). */
    fun angleDiffDeg(a: Float, b: Float): Float {
        var d = abs(a - b) % 360f
        if (d > 180f) d = 360f - d
        return d
    }
}
