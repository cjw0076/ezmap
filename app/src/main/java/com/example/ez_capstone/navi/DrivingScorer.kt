package com.example.ez_capstone.navi

import com.example.ez_capstone.db.entity.DrivingScoreEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * 실시간 안전운전점수 계산 엔진.
 * onLocationUpdate마다 update() 호출 → DrivingLiveScore 반환.
 * 주행 종료 시 buildEntity()로 DB 저장.
 */
@Singleton
class DrivingScorer @Inject constructor() {

    private var prevSpeedKmh = 0
    private var prevBearing = 0f
    private var prevUpdateMs = 0L
    private var speedingCount = 0
    private var hardBrakeCount = 0
    private var sharpTurnCount = 0
    private var speedingDeductions = 0
    private var hardBrakeDeductions = 0
    private var sharpTurnDeductions = 0

    fun reset() {
        prevSpeedKmh = 0
        prevBearing = 0f
        prevUpdateMs = 0L
        speedingCount = 0
        hardBrakeCount = 0
        sharpTurnCount = 0
        speedingDeductions = 0
        hardBrakeDeductions = 0
        sharpTurnDeductions = 0
    }

    /**
     * onLocationUpdate마다 호출.
     * @param limitSpeed 현재 구간 제한속도 (SafetyAlertUi.limitSpeed 또는 기본값 80)
     */
    fun update(speedKmh: Int, bearing: Float, limitSpeed: Int): DrivingLiveScore {
        val now = System.currentTimeMillis()
        val deltaMs = if (prevUpdateMs > 0) (now - prevUpdateMs) else 1000L
        val deltaSec = deltaMs / 1000.0

        if (prevUpdateMs > 0 && deltaSec > 0) {
            // 과속 감지
            if (speedKmh > limitSpeed + 10) {
                speedingCount++
                speedingDeductions += 2
            }

            // 급감속 감지: 20km/h/s 이상 감속
            val deceleration = (prevSpeedKmh - speedKmh) / deltaSec
            if (deceleration > 20.0) {
                hardBrakeCount++
                hardBrakeDeductions += 3
            }

            // 급회전 감지: 1초 이내 45° 이상 방향 변화
            if (deltaSec <= 1.5) {
                val angleDiff = abs(((bearing - prevBearing + 180) % 360) - 180)
                if (angleDiff > 45f) {
                    sharpTurnCount++
                    sharpTurnDeductions += 1
                }
            }
        }

        prevSpeedKmh = speedKmh
        prevBearing = bearing
        prevUpdateMs = now

        val score = (100 - speedingDeductions - hardBrakeDeductions - sharpTurnDeductions)
            .coerceIn(0, 100)

        return DrivingLiveScore(
            current = score,
            speedingCount = speedingCount,
            hardBrakeCount = hardBrakeCount,
            sharpTurnCount = sharpTurnCount
        )
    }

    fun buildEntity(sessionId: String, startedAt: Long, distanceM: Int): DrivingScoreEntity {
        val score = (100 - speedingDeductions - hardBrakeDeductions - sharpTurnDeductions)
            .coerceIn(0, 100)
        return DrivingScoreEntity(
            sessionId = sessionId,
            startedAt = startedAt,
            endedAt = System.currentTimeMillis(),
            totalDistanceM = distanceM,
            finalScore = score,
            speedingCount = speedingCount,
            hardBrakeCount = hardBrakeCount,
            sharpTurnCount = sharpTurnCount,
            speedingDeductions = speedingDeductions,
            hardBrakeDeductions = hardBrakeDeductions,
            sharpTurnDeductions = sharpTurnDeductions
        )
    }
}
