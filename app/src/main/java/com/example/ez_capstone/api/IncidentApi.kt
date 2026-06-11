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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 한국도로공사 고속도로 돌발정보 API.
 * Tool: get_road_incidents
 * 경로 상 사고/공사/낙하물 실시간 돌발 정보 조회.
 */
@Singleton
class IncidentApi @Inject constructor(
    private val apiKeyProvider: ApiKeyProvider
) {
    companion object {
        private const val TAG = "IncidentApi"
        private const val BASE = "http://data.ex.co.kr/openapi/trafficInfo"
    }

    private val client = OkHttpClient()

    data class RoadIncident(
        val id: String,
        val type: String,               // "accident" | "construction" | "fallen_object" | "congestion"
        val description: String,
        val lat: Double,
        val lng: Double,
        val routeName: String,
        val startTime: Long,
        val expectedEndTime: Long? = null
    )

    suspend fun getIncidents(lat: Double, lng: Double, radius: Int = 10000): List<RoadIncident> =
        withContext(Dispatchers.IO) {
            val key = apiKeyProvider.dataGoKrKey
            if (key.isBlank()) return@withContext emptyList()

            try {
                val url = "$BASE/getAcdntIlssInfo".toHttpUrl().newBuilder().apply {
                    addQueryParameter("key", key)
                    addQueryParameter("type", "json")
                    addQueryParameter("numOfRows", "50")
                    addQueryParameter("pageNo", "1")
                }.build()

                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext emptyList()

                val items = JSONObject(body)
                    .optJSONObject("response")
                    ?.optJSONObject("body")
                    ?.optJSONObject("items")
                    ?.optJSONArray("item")
                    ?: return@withContext emptyList()

                val incidents = mutableListOf<RoadIncident>()
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val iLat = item.optDouble("coordY", 0.0)
                    val iLng = item.optDouble("coordX", 0.0)
                    if (iLat == 0.0 || iLng == 0.0) continue
                    if (haversine(lat, lng, iLat, iLng) > radius) continue

                    val typeStr = when (item.optString("incidentType", "")) {
                        "교통사고" -> "accident"
                        "공사" -> "construction"
                        "낙하물" -> "fallen_object"
                        "정체" -> "congestion"
                        else -> "accident"
                    }

                    incidents.add(RoadIncident(
                        id = item.optString("incidentId", "$i"),
                        type = typeStr,
                        description = item.optString("description", "돌발상황"),
                        lat = iLat,
                        lng = iLng,
                        routeName = item.optString("routeNm", ""),
                        startTime = System.currentTimeMillis()
                    ))
                }
                incidents
            } catch (e: Exception) {
                Log.e(TAG, "돌발정보 조회 실패: ${e.message}")
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
