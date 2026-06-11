package com.example.ez_capstone.api

import android.util.Log
import com.example.ez_capstone.config.ApiKeyProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * ITS 국가교통정보센터 실시간 API.
 * Tools: get_traffic_speed, get_traffic_incidents, get_traffic_cctv
 * 모두 dataGoKrKey 사용.
 */
@Singleton
class TrafficInfoApi @Inject constructor(
    private val apiKeyProvider: ApiKeyProvider
) {
    companion object {
        private const val TAG = "TrafficInfoApi"
        private const val TRAFFIC_BASE = "http://openapi.its.go.kr:8081/api/NTrafficInfo"
        private const val INCIDENT_BASE = "http://openapi.its.go.kr:8081/api/NIncidentInfo"
        private const val CCTV_BASE = "http://openapi.its.go.kr:8081/api/NCCTVInfo"
    }

    private val client = OkHttpClient()

    data class TrafficSegment(
        val linkId: String,
        val roadName: String,
        val speed: Int,
        val travelTime: Int,
        val congestionLevel: String
    )

    data class Incident(
        val type: String,
        val description: String,
        val roadName: String,
        val lat: Double,
        val lng: Double,
        val startTime: String,
        val detourInfo: String?
    )

    data class TrafficCctv(
        val cctvName: String,
        val lat: Double,
        val lng: Double,
        val imageUrl: String?,
        val videoUrl: String?
    )

    suspend fun getTrafficSpeed(lat: Double, lng: Double, radius: Int = 5000): List<TrafficSegment> =
        withContext(Dispatchers.IO) {
            val key = apiKeyProvider.dataGoKrKey
            if (key.isBlank()) return@withContext emptyList()

            try {
                val url = TRAFFIC_BASE.toHttpUrl().newBuilder().apply {
                    addQueryParameter("apiKey", key)
                    addQueryParameter("type", "all")
                    addQueryParameter("dpType", "json")
                }.build()

                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext emptyList()

                val json = JSONObject(body)
                val items = json.optJSONObject("body")
                    ?.optJSONArray("items")
                    ?: return@withContext emptyList()

                val results = mutableListOf<TrafficSegment>()
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val iLat = item.optDouble("startLat", 0.0)
                    val iLng = item.optDouble("startLon", 0.0)

                    if (iLat == 0.0 || iLng == 0.0) continue
                    if (haversine(lat, lng, iLat, iLng) > radius) continue

                    val speed = item.optInt("speed", 0)
                    val congestion = when {
                        speed >= 40 -> "원활"
                        speed >= 20 -> "서행"
                        else -> "정체"
                    }

                    results.add(TrafficSegment(
                        linkId = item.optString("linkId", ""),
                        roadName = item.optString("roadName", ""),
                        speed = speed,
                        travelTime = item.optInt("travelTime", 0),
                        congestionLevel = congestion
                    ))
                }
                results.sortedBy { haversine(lat, lng, it.linkId.hashCode().toDouble(), 0.0) }.take(10)
            } catch (e: Exception) {
                Log.e(TAG, "교통소통 API 실패: ${e.message}")
                emptyList()
            }
        }

    suspend fun getIncidents(lat: Double, lng: Double, radius: Int = 10000): List<Incident> =
        withContext(Dispatchers.IO) {
            val key = apiKeyProvider.dataGoKrKey
            if (key.isBlank()) return@withContext emptyList()

            try {
                val url = INCIDENT_BASE.toHttpUrl().newBuilder().apply {
                    addQueryParameter("apiKey", key)
                    addQueryParameter("type", "all")
                    addQueryParameter("dpType", "json")
                }.build()

                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext emptyList()

                val json = JSONObject(body)
                val items = json.optJSONObject("body")
                    ?.optJSONArray("items")
                    ?: return@withContext emptyList()

                val results = mutableListOf<Incident>()
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val iLat = item.optDouble("coordY", 0.0)
                    val iLng = item.optDouble("coordX", 0.0)

                    if (iLat == 0.0 || iLng == 0.0) continue
                    if (haversine(lat, lng, iLat, iLng) > radius) continue

                    val typeCode = item.optInt("incidentType", 0)
                    val typeName = when (typeCode) {
                        1 -> "사고"
                        2 -> "공사"
                        3 -> "기상"
                        4 -> "행사"
                        else -> "기타"
                    }

                    results.add(Incident(
                        type = typeName,
                        description = item.optString("incidentMsg", ""),
                        roadName = item.optString("roadName", ""),
                        lat = iLat,
                        lng = iLng,
                        startTime = item.optString("startDate", ""),
                        detourInfo = item.optString("detourInfo", "").ifBlank { null }
                    ))
                }
                results.take(10)
            } catch (e: Exception) {
                Log.e(TAG, "돌발상황 API 실패: ${e.message}")
                emptyList()
            }
        }

    suspend fun getCctvInfo(lat: Double, lng: Double, radius: Int = 5000): List<TrafficCctv> =
        withContext(Dispatchers.IO) {
            val key = apiKeyProvider.dataGoKrKey
            if (key.isBlank()) return@withContext emptyList()

            try {
                val url = CCTV_BASE.toHttpUrl().newBuilder().apply {
                    addQueryParameter("apiKey", key)
                    addQueryParameter("type", "all")
                    addQueryParameter("dpType", "json")
                    addQueryParameter("cctvType", "1")
                }.build()

                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext emptyList()

                val json = JSONObject(body)
                val items = json.optJSONObject("body")
                    ?.optJSONArray("items")
                    ?: return@withContext emptyList()

                val results = mutableListOf<TrafficCctv>()
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val iLat = item.optDouble("coordY", 0.0)
                    val iLng = item.optDouble("coordX", 0.0)

                    if (iLat == 0.0 || iLng == 0.0) continue
                    if (haversine(lat, lng, iLat, iLng) > radius) continue

                    results.add(TrafficCctv(
                        cctvName = item.optString("cctvName", ""),
                        lat = iLat,
                        lng = iLng,
                        imageUrl = item.optString("cctvUrl", "").ifBlank { null },
                        videoUrl = item.optString("cctvFormat", "").ifBlank { null }
                    ))
                }
                results.sortedBy { haversine(lat, lng, it.lat, it.lng) }.take(5)
            } catch (e: Exception) {
                Log.e(TAG, "CCTV API 실패: ${e.message}")
                emptyList()
            }
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
