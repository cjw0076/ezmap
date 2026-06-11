package com.example.ez_capstone.navi

import android.util.Log
import com.example.ez_capstone.api.SpeedCameraApi
import com.example.ez_capstone.server.models.Coord
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 구간단속 / 고정단속 카메라 경고 엔진.
 * RestGuideEngine.start() 시 경로 프리패치 후 onLocationUpdate마다 checkAlerts() 호출.
 */
@Singleton
class SpeedEnforcement @Inject constructor(
    private val speedCameraApi: SpeedCameraApi
) {
    private var cameras: List<SpeedCameraApi.SpeedCamera> = emptyList()
    private var sectionStartTime = 0L
    private var inSection = false
    private var sectionStart: SpeedCameraApi.SpeedCamera? = null
    private val alertedIds = mutableSetOf<String>()

    /** 경로 로드 시 호출 */
    suspend fun loadCamerasForRoute(coords: List<Coord>) {
        try {
            cameras = speedCameraApi.getCamerasOnRoute(coords, bufferMeters = 300)
            Log.d("SpeedEnforcement", "Loaded ${cameras.size} speed cameras")
        } catch (e: Exception) {
            Log.w("SpeedEnforcement", "Speed camera load failed: ${e.message}")
            cameras = emptyList()
        }
        alertedIds.clear()
        inSection = false
        sectionStart = null
    }

    /** onLocationUpdate마다 호출 — null이면 알림 없음 */
    fun checkAlerts(lat: Double, lng: Double, speedKmh: Int): SpeedCameraAlert? {
        for (cam in cameras) {
            val dist = haversine(lat, lng, cam.lat, cam.lng).toInt()

            // 구간 진입 처리
            if (cam.type == "section_start" && dist <= 300 && !inSection) {
                inSection = true
                sectionStart = cam
                sectionStartTime = System.currentTimeMillis()
            }
            if (cam.type == "section_end" && inSection) {
                inSection = false
            }

            val key = "${cam.lat}_${cam.lng}"
            if (dist > 400) continue  // 400m 이상이면 알림 불필요
            if (dist > 50 && key in alertedIds) continue  // 50m 이내는 재알림 허용

            if (dist <= 300) {
                alertedIds.add(key)
                val safetyType = when (cam.type) {
                    "section_start" -> SafetyType.SECTION_START
                    "section_end" -> SafetyType.SECTION_END
                    else -> SafetyType.CAMERA
                }
                val remaining = if (inSection && sectionStart != null)
                    cam.sectionLengthM - haversine(sectionStart!!.lat, sectionStart!!.lng, lat, lng).toInt()
                else 0

                return SpeedCameraAlert(
                    type = safetyType,
                    limitSpeed = cam.limitSpeed,
                    distanceM = dist,
                    isOverSpeed = speedKmh > cam.limitSpeed,
                    sectionRemainingM = remaining.coerceAtLeast(0)
                )
            }
        }
        return null
    }

    fun reset() {
        cameras = emptyList()
        alertedIds.clear()
        inSection = false
        sectionStart = null
    }

    private fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
