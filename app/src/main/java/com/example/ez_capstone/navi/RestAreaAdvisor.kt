package com.example.ez_capstone.navi

import com.example.ez_capstone.api.RestAreaApi
import com.example.ez_capstone.server.models.Coord
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 고속도로 휴게소 선제 안내 엔진.
 * 경로 로드 시 loadForRoute() 후, 5분 간격으로 getNextRecommendation() 호출.
 */
@Singleton
class RestAreaAdvisor @Inject constructor(
    private val restAreaApi: RestAreaApi
) {
    private var restAreas: List<RestAreaApi.RestArea> = emptyList()
    private var hasRecommended = false
    private val recommendedNames = mutableSetOf<String>()

    data class RestAreaRecommendation(
        val restArea: RestAreaApi.RestArea,
        val etaMinutes: Int,
        val distanceM: Int,
        val reason: String
    )

    suspend fun loadForRoute(coords: List<Coord>, routeName: String?) {
        restAreas = restAreaApi.getRestAreas(routeName)
        recommendedNames.clear()
        hasRecommended = false
    }

    /**
     * 주행 중 5분마다 호출.
     * @param drivingMinutes 현재까지 주행 시간 (분)
     * @param fuelType "gasoline"|"diesel"|"ev"|"hybrid"
     */
    fun getNextRecommendation(
        lat: Double,
        lng: Double,
        remainDistanceM: Int,
        drivingMinutes: Int,
        fuelType: String
    ): RestAreaRecommendation? {
        // 조건: 총 남은 거리 50km 이상, 주행 40분 이상 or EV 충전 필요
        val needsStop = drivingMinutes >= 40 || (fuelType == "ev" && remainDistanceM > 50000)
        if (!needsStop) return null

        val upcoming = restAreas
            .filter { area ->
                val dist = haversine(lat, lng, area.lat, area.lng)
                dist in 5000.0..50000.0 && area.name !in recommendedNames
            }
            .sortedBy { haversine(lat, lng, it.lat, it.lng) }

        val target = when (fuelType) {
            "ev" -> upcoming.firstOrNull { it.hasEvCharger }
            "gasoline", "diesel" -> upcoming.firstOrNull { it.hasGasStation || it.hasRestaurant }
            else -> upcoming.firstOrNull { it.hasRestaurant }
        } ?: return null

        val distM = haversine(lat, lng, target.lat, target.lng).toInt()
        val etaMin = (distM / 1000.0 / 80.0 * 60).toInt().coerceAtLeast(1)

        val reason = buildReason(target, fuelType, drivingMinutes)
        recommendedNames.add(target.name)

        return RestAreaRecommendation(
            restArea = target,
            etaMinutes = etaMin,
            distanceM = distM,
            reason = reason
        )
    }

    private fun buildReason(area: RestAreaApi.RestArea, fuelType: String, drivingMinutes: Int): String {
        return when {
            fuelType == "ev" && area.hasEvCharger -> "EV 충전소 있음"
            drivingMinutes >= 90 -> "장시간 주행 — 휴식 권장"
            area.hasGasStation -> "주유소 있음"
            else -> "화장실 · 식당 있음"
        }
    }

    fun reset() {
        restAreas = emptyList()
        recommendedNames.clear()
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
