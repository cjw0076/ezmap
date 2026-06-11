package com.example.ez_capstone.api

import android.util.Log
import com.example.ez_capstone.config.ApiKeyProvider
import com.example.ez_capstone.server.models.Coord
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
 * 공공데이터포털 과속·신호위반 단속카메라 표준데이터 API.
 * Tool: get_speed_cameras
 * 경로 상 구간단속/고정단속 카메라 조회.
 */
@Singleton
class SpeedCameraApi @Inject constructor(
    private val apiKeyProvider: ApiKeyProvider
) {
    companion object {
        private const val TAG = "SpeedCameraApi"
        private const val BASE = "http://apis.data.go.kr/B553174/SpeedViolationCamera"
    }

    private val client = OkHttpClient()

    data class SpeedCamera(
        val lat: Double,
        val lng: Double,
        val limitSpeed: Int,
        val type: String,               // "fixed" | "section_start" | "section_end"
        val direction: String,
        val sectionLengthM: Int = 0     // 구간단속 구간 거리(m), 고정단속=0
    )

    suspend fun getCamerasNear(lat: Double, lng: Double): List<SpeedCamera> =
        withContext(Dispatchers.IO) {
            val key = apiKeyProvider.dataGoKrKey
            if (key.isBlank()) return@withContext emptyList()

            try {
                val url = "$BASE/getCameraInfo".toHttpUrl().newBuilder().apply {
                    addQueryParameter("serviceKey", key)
                    addQueryParameter("numOfRows", "100")
                    addQueryParameter("pageNo", "1")
                    addQueryParameter("type", "json")
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

                val cameras = mutableListOf<SpeedCamera>()
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val cLat = item.optDouble("latitude", 0.0)
                    val cLng = item.optDouble("longitude", 0.0)
                    if (cLat == 0.0 || cLng == 0.0) continue
                    if (haversine(lat, lng, cLat, cLng) > 5000) continue  // 5km 필터

                    cameras.add(SpeedCamera(
                        lat = cLat,
                        lng = cLng,
                        limitSpeed = item.optInt("speedLimit", 60),
                        type = when (item.optString("cameraType", "")) {
                            "구간시작" -> "section_start"
                            "구간종료" -> "section_end"
                            else -> "fixed"
                        },
                        direction = item.optString("direction", ""),
                        sectionLengthM = item.optInt("sectionLength", 0) * 1000
                    ))
                }
                cameras
            } catch (e: Exception) {
                Log.e(TAG, "단속카메라 조회 실패: ${e.message}")
                emptyList()
            }
        }

    /** 경로 좌표 목록 기준 근처 카메라 필터링 */
    suspend fun getCamerasOnRoute(routeCoords: List<Coord>, bufferMeters: Int = 200): List<SpeedCamera> {
        if (routeCoords.isEmpty()) return emptyList()
        val center = routeCoords[routeCoords.size / 2]
        val allCameras = getCamerasNear(center.lat, center.lng)
        return allCameras.filter { cam ->
            routeCoords.any { coord -> haversine(coord.lat, coord.lng, cam.lat, cam.lng) <= bufferMeters }
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
