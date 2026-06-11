package com.example.ez_capstone.navi

import android.util.Log
import com.example.ez_capstone.api.EvChargerApi
import com.example.ez_capstone.server.models.Coord
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * EV 배터리 기반 충전 경유지 최적화.
 * ProfileEntity.batteryCapacityKwh / currentBatteryPct 기반으로 경유 계획 수립.
 */
@Singleton
class EvRouteOptimizer @Inject constructor(
    private val evChargerApi: EvChargerApi
) {
    companion object {
        private const val TAG = "EvRouteOptimizer"
        private const val KWH_PER_KM_HIGHWAY = 0.18f
        private const val KWH_PER_KM_CITY = 0.22f
        private const val MIN_ARRIVAL_PCT = 20          // 목적지 도착 시 최소 배터리 %
    }

    data class EvChargerStop(
        val charger: EvChargerApi.EvCharger,
        val distanceFromStartM: Int,
        val arrivalBatteryPct: Int,
        val targetBatteryPct: Int,
        val estimatedChargeMin: Int
    )

    data class EvRoutePlan(
        val chargeStops: List<EvChargerStop>,
        val totalTimeMin: Int,
        val chargeTimeMin: Int,
        val arrivalBatteryPct: Int
    )

    fun estimateConsumption(distanceM: Int, isHighway: Boolean): Float {
        val kwhPerKm = if (isHighway) KWH_PER_KM_HIGHWAY else KWH_PER_KM_CITY
        return (distanceM / 1000.0f) * kwhPerKm
    }

    suspend fun planRoute(
        routeCoords: List<Coord>,
        currentBatteryPct: Int,
        batteryCapacityKwh: Float,
        chargingSpeedKw: Float
    ): EvRoutePlan? {
        if (batteryCapacityKwh <= 0f || routeCoords.isEmpty()) return null

        val totalDistM = calcTotalDist(routeCoords)
        val totalKwh = estimateConsumption(totalDistM, isHighway = true)
        val currentKwh = batteryCapacityKwh * currentBatteryPct / 100f
        val minKwh = batteryCapacityKwh * MIN_ARRIVAL_PCT / 100f

        // 충전 없이 완주 가능 여부 체크
        if (currentKwh - totalKwh >= minKwh) {
            val arrivalPct = ((currentKwh - totalKwh) / batteryCapacityKwh * 100).toInt()
            return EvRoutePlan(emptyList(), (totalDistM / 1000.0 / 80.0 * 60).toInt(), 0, arrivalPct)
        }

        // 충전 경유지 탐색
        val chargeStops = mutableListOf<EvChargerStop>()
        var remainKwh = currentKwh
        var distTraveled = 0
        var chargeTimeTotal = 0

        for (i in 0 until routeCoords.size - 1) {
            val segDist = haversine(
                routeCoords[i].lat, routeCoords[i].lng,
                routeCoords[i + 1].lat, routeCoords[i + 1].lng
            ).toInt()
            val segKwh = estimateConsumption(segDist, isHighway = true)
            remainKwh -= segKwh
            distTraveled += segDist

            // 남은 배터리가 30% 이하로 떨어지면 충전소 탐색
            val pct = (remainKwh / batteryCapacityKwh * 100).toInt()
            if (pct <= 30 && i < routeCoords.size - 10) {
                try {
                    val chargers = evChargerApi.getChargers(
                        routeCoords[i + 1].lat, routeCoords[i + 1].lng,
                        radius = 5000,
                        statusFilter = true
                    )
                    val best = chargers.firstOrNull() ?: continue

                    val targetKwh = batteryCapacityKwh * 0.8f  // 80%까지 충전
                    val chargeKwh = targetKwh - remainKwh
                    val chargeMin = if (chargingSpeedKw > 0) (chargeKwh / chargingSpeedKw * 60).toInt() else 30

                    chargeStops.add(EvChargerStop(
                        charger = best,
                        distanceFromStartM = distTraveled,
                        arrivalBatteryPct = pct,
                        targetBatteryPct = 80,
                        estimatedChargeMin = chargeMin
                    ))
                    remainKwh = targetKwh
                    chargeTimeTotal += chargeMin
                } catch (e: Exception) {
                    Log.w(TAG, "충전소 탐색 실패: ${e.message}")
                }
            }
        }

        val arrivalPct = ((remainKwh / batteryCapacityKwh) * 100).toInt().coerceIn(0, 100)
        val driveMin = (totalDistM / 1000.0 / 80.0 * 60).toInt()

        return EvRoutePlan(
            chargeStops = chargeStops,
            totalTimeMin = driveMin + chargeTimeTotal,
            chargeTimeMin = chargeTimeTotal,
            arrivalBatteryPct = arrivalPct
        )
    }

    private fun calcTotalDist(coords: List<Coord>): Int {
        var total = 0
        for (i in 0 until coords.size - 1) {
            total += haversine(coords[i].lat, coords[i].lng, coords[i+1].lat, coords[i+1].lng).toInt()
        }
        return total
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
