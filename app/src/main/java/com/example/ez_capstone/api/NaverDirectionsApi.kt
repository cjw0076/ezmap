package com.example.ez_capstone.api

import android.util.Log
import com.example.ez_capstone.agent.models.CoordData
import com.example.ez_capstone.agent.models.DirectionsRoute
import com.example.ez_capstone.agent.models.GuideData
import com.example.ez_capstone.config.ApiKeyProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 네이버 클라우드 Directions 5 API.
 * Tool: get_directions_naver
 * 카카오와 교차 검증용 듀얼 엔진.
 */
@Singleton
class NaverDirectionsApi @Inject constructor(
    private val apiKeyProvider: ApiKeyProvider
) {
    companion object {
        private const val TAG = "NaverDirectionsApi"
        private const val BASE = "https://naveropenapi.apigw.ntruss.com/map-direction/v1/driving"
    }

    private val client = OkHttpClient()

    data class NaverRouteResult(
        val distanceM: Int,
        val durationMs: Int,
        val tollFare: Int,
        val taxiFare: Int,
        val fuelPrice: Int,
        val coords: List<CoordData>,
        val guides: List<GuideData>
    )

    /**
     * 네이버 경로 탐색.
     * @param option traoptimal(추천), trafast(빠른), tracomfort(편한), traavoidtoll(무료)
     */
    suspend fun getDirections(
        originLng: Double, originLat: Double,
        destLng: Double, destLat: Double,
        option: String = "traoptimal"
    ): NaverRouteResult? = withContext(Dispatchers.IO) {
        val keyId = apiKeyProvider.naverClientId
        val keySecret = apiKeyProvider.naverClientSecret
        if (keyId.isBlank() || keySecret.isBlank()) return@withContext null

        try {
            val url = BASE.toHttpUrl().newBuilder().apply {
                addQueryParameter("start", "$originLng,$originLat")
                addQueryParameter("goal", "$destLng,$destLat")
                addQueryParameter("option", option)
            }.build()

            val request = Request.Builder()
                .url(url)
                .header("X-NCP-APIGW-API-KEY-ID", keyId)
                .header("X-NCP-APIGW-API-KEY", keySecret)
                .build()

            val body = client.getBody(request)  // 중앙 헬퍼: HTTP 실패/네트워크 분류해 던짐

            val json = JSONObject(body)
            val route = json.optJSONObject("route")
                ?.optJSONArray(option)
                ?.optJSONObject(0)
                ?: return@withContext null

            val summary = route.optJSONObject("summary") ?: JSONObject()

            // 좌표 추출 (path 배열)
            val pathArr = route.optJSONArray("path")
            val coords = mutableListOf<CoordData>()
            if (pathArr != null) {
                for (i in 0 until pathArr.length()) {
                    val point = pathArr.optJSONArray(i) ?: continue
                    coords.add(CoordData(lat = point.getDouble(1), lng = point.getDouble(0)))
                }
            }

            // 가이드 추출
            val guideArr = route.optJSONArray("guide")
            val guides = mutableListOf<GuideData>()
            if (guideArr != null) {
                for (i in 0 until guideArr.length()) {
                    val g = guideArr.getJSONObject(i)
                    guides.add(GuideData(
                        name = g.optString("name", ""),
                        guidance = g.optString("instructions", ""),
                        type = g.optInt("type", 0),
                        lat = 0.0, lng = 0.0,  // 네이버는 pointIndex로 좌표 참조
                        distance = g.optInt("distance", 0),
                        duration = g.optInt("duration", 0)
                    ))
                }
            }

            NaverRouteResult(
                distanceM = summary.optInt("distance", 0),
                durationMs = summary.optInt("duration", 0),
                tollFare = summary.optInt("tollFare", 0),
                taxiFare = summary.optInt("taxiFare", 0),
                fuelPrice = summary.optInt("fuelPrice", 0),
                coords = coords,
                guides = guides
            )
        } catch (e: com.example.ez_capstone.agent.ToolFailureException) {
            throw e  // 분류된 실패 전파
        } catch (e: Exception) {
            Log.e(TAG, "Naver directions failed: ${e.message}")
            throw com.example.ez_capstone.agent.ToolFailureException(
                com.example.ez_capstone.agent.FailureKind.fromThrowable(e), e.message)
        }
    }
}
