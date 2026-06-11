package com.example.ez_capstone.navi

import com.example.ez_capstone.api.IncidentApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 돌발상황 필터링 및 AiAlertUi 변환.
 * 진행 방향 ±45° + 5km 이내 돌발만 경고.
 */
@Singleton
class IncidentReporter @Inject constructor(
    private val incidentApi: IncidentApi
) {
    private val alertedIds = mutableSetOf<String>()

    suspend fun getRelevantIncidents(
        lat: Double,
        lng: Double,
        bearingDeg: Float
    ): List<AiAlertUi> {
        val incidents = incidentApi.getIncidents(lat, lng, radius = 5000)
        return filterRelevantIncidents(incidents, lat, lng, bearingDeg)
            .filter { it.id !in alertedIds }
            .also { filtered -> filtered.forEach { alertedIds.add(it.id) } }
            .map { toAlert(it, haversine(lat, lng, it.lat, it.lng).toInt()) }
    }

    fun filterRelevantIncidents(
        incidents: List<IncidentApi.RoadIncident>,
        lat: Double,
        lng: Double,
        bearingDeg: Float
    ): List<IncidentApi.RoadIncident> {
        return incidents.filter { incident ->
            val dist = haversine(lat, lng, incident.lat, incident.lng)
            if (dist > 5000) return@filter false

            val incidentBearing = calculateBearing(lat, lng, incident.lat, incident.lng)
            val angleDiff = abs((incidentBearing - bearingDeg + 360) % 360)
            angleDiff <= 45 || angleDiff >= 315  // ±45° 범위
        }
    }

    fun toAlert(incident: IncidentApi.RoadIncident, distanceM: Int): AiAlertUi {
        val typeLabel = when (incident.type) {
            "accident" -> "교통사고"
            "construction" -> "공사"
            "fallen_object" -> "낙하물"
            else -> "돌발상황"
        }
        return AiAlertUi(
            type = AiAlertType.INCIDENT,
            message = "$typeLabel ${distanceM}m 앞 — ${incident.routeName} ${incident.description}",
            actions = listOf("우회하기", "무시"),
            autoDismissMs = 15000
        )
    }

    fun reset() {
        alertedIds.clear()
    }

    private fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun calculateBearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val dLng = Math.toRadians(lng2 - lng1)
        val y = sin(dLng) * cos(Math.toRadians(lat2))
        val x = cos(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) -
                sin(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * cos(dLng)
        return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
    }
}
