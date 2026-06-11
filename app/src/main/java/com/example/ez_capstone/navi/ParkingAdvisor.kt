package com.example.ez_capstone.navi

import com.example.ez_capstone.api.ParkingApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 목적지 도착 2km 전 주차장 선제 안내.
 * NavigationViewModel.LocationUpdateCallback에서 remainDistanceM <= 2000 조건으로 호출.
 */
@Singleton
class ParkingAdvisor @Inject constructor(
    private val parkingApi: ParkingApi
) {
    private var hasTriggered = false

    data class ParkingRecommendation(
        val lot: ParkingApi.ParkingLot,
        val distanceFromDestM: Int,
        val walkMinutes: Int,
        val message: String
    )

    suspend fun checkAndRecommend(
        destLat: Double,
        destLng: Double,
        remainDistanceM: Int
    ): ParkingRecommendation? = withContext(Dispatchers.IO) {
        if (hasTriggered || remainDistanceM > 2000) return@withContext null
        hasTriggered = true

        val lots = parkingApi.getParkingLots(destLat, destLng, radius = 500)
        val nearest = lots
            .filter { it.totalSpaces > 0 }
            .minByOrNull { haversine(destLat, destLng, it.lat, it.lng) }
            ?: return@withContext null

        val distM = haversine(destLat, destLng, nearest.lat, nearest.lng).toInt()
        val walkMin = (distM / 80.0).toInt().coerceAtLeast(1)  // 도보 80m/분

        ParkingRecommendation(
            lot = nearest,
            distanceFromDestM = distM,
            walkMinutes = walkMin,
            message = "${nearest.name} (도보 ${walkMin}분) — ${nearest.feeInfo}"
        )
    }

    fun reset() {
        hasTriggered = false
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
