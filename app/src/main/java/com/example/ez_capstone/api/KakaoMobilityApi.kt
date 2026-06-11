package com.example.ez_capstone.api

import android.util.Log
import com.example.ez_capstone.agent.models.CoordData
import com.example.ez_capstone.agent.models.DirectionsRoute
import com.example.ez_capstone.agent.models.FutureETAResult
import com.example.ez_capstone.agent.models.GuideData
import com.example.ez_capstone.agent.models.WaypointParam
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
 * 카카오모빌리티 길찾기 REST API.
 * 서버 kakaoDirections.js 포팅.
 */
@Singleton
class KakaoMobilityApi @Inject constructor(
    private val apiKeyProvider: ApiKeyProvider
) {
    companion object {
        private const val TAG = "KakaoMobilityApi"
        private const val DIRECTIONS_URL = "https://apis-navi.kakaomobility.com/v1/directions"
        private const val FUTURE_URL = "https://apis-navi.kakaomobility.com/v1/future/directions"
    }

    private val client = OkHttpClient()
    private fun authHeader() = "KakaoAK ${apiKeyProvider.activeKakaoKey}"

    /** 자동차 경로 탐색 (다중 경로 포함) */
    suspend fun getDirections(
        originLng: Double, originLat: Double,
        destLng: Double, destLat: Double,
        waypoints: List<WaypointParam> = emptyList(),
        priority: String = "RECOMMEND",
        alternatives: Boolean = true
    ): List<DirectionsRoute> = withContext(Dispatchers.IO) {
        val url = DIRECTIONS_URL.toHttpUrl().newBuilder().apply {
            addQueryParameter("origin", "$originLng,$originLat")
            addQueryParameter("destination", "$destLng,$destLat")
            addQueryParameter("priority", priority)
            if (alternatives) addQueryParameter("alternatives", "true")
            if (waypoints.isNotEmpty()) {
                val wp = waypoints.take(5).joinToString("|") { "${it.lng},${it.lat}" }
                addQueryParameter("waypoints", wp)
            }
        }.build()

        val request = Request.Builder().url(url).header("Authorization", authHeader()).build()
        val response = client.newCall(request).execute()
        val body = response.body?.string()
            ?: throw com.example.ez_capstone.agent.ToolFailureException(
                com.example.ez_capstone.agent.FailureKind.UPSTREAM, "directions: 빈 응답")

        // 실패를 삼키지 않고 분류해 던진다 → ToolExecutor 중앙 catch가 처리(silent failure 방지).
        if (!response.isSuccessful) {
            Log.e(TAG, "directions error ${response.code}: ${body.take(300)}")
            throw com.example.ez_capstone.agent.ToolFailureException(
                com.example.ez_capstone.agent.FailureKind.fromHttp(response.code),
                "directions HTTP ${response.code}")
        }

        // 200이지만 경로 없음 = 정당한 빈 결과(실패 아님) → emptyList 유지.
        val json = JSONObject(body)
        val routes = json.optJSONArray("routes") ?: return@withContext emptyList()

        (0 until routes.length())
            .map { routes.getJSONObject(it) }
            .filter { it.optInt("result_code", -1) == 0 }
            .map { parseRoute(it) }
            .filter { it.coords.isNotEmpty() }
    }

    /** 미래 ETA 조회 */
    suspend fun getFutureETA(
        originLng: Double, originLat: Double,
        destLng: Double, destLat: Double,
        departureTime: String  // yyyyMMddHHmm
    ): FutureETAResult = withContext(Dispatchers.IO) {
        val url = FUTURE_URL.toHttpUrl().newBuilder().apply {
            addQueryParameter("origin", "$originLng,$originLat")
            addQueryParameter("destination", "$destLng,$destLat")
            addQueryParameter("departure_time", departureTime)
        }.build()

        val request = Request.Builder().url(url).header("Authorization", authHeader()).build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Future ETA: empty response")

        val json = JSONObject(body)
        val routes = json.optJSONArray("routes")
        if (routes == null || routes.length() == 0) throw Exception("ETA 경로를 찾을 수 없습니다")

        val route = routes.getJSONObject(0)
        val summary = route.optJSONObject("summary") ?: JSONObject()

        FutureETAResult(
            durationS = summary.optInt("duration", 0),
            distanceM = summary.optInt("distance", 0),
            departureTime = departureTime
        )
    }

    /** 단일 경로 파싱 — 서버 parseRoute() 동일 로직 */
    private fun parseRoute(routeJson: JSONObject): DirectionsRoute {
        val summary = routeJson.optJSONObject("summary") ?: JSONObject()
        val sections = routeJson.optJSONArray("sections")

        val coords = mutableListOf<CoordData>()
        val guides = mutableListOf<GuideData>()

        if (sections != null) {
            for (s in 0 until sections.length()) {
                val section = sections.getJSONObject(s)

                // 좌표 추출: roads[].vertexes
                val roads = section.optJSONArray("roads")
                if (roads != null) {
                    for (r in 0 until roads.length()) {
                        val verts = roads.getJSONObject(r).optJSONArray("vertexes") ?: continue
                        var v = 0
                        while (v < verts.length() - 1) {
                            coords.add(CoordData(
                                lat = verts.getDouble(v + 1),
                                lng = verts.getDouble(v)
                            ))
                            v += 2
                        }
                    }
                }

                // 안내 추출: guides[]
                val guideArray = section.optJSONArray("guides")
                if (guideArray != null) {
                    for (g in 0 until guideArray.length()) {
                        val gObj = guideArray.getJSONObject(g)
                        guides.add(GuideData(
                            name = gObj.optString("name", ""),
                            guidance = gObj.optString("guidance", ""),
                            type = gObj.optInt("type", 0),
                            lat = gObj.optDouble("y", 0.0),
                            lng = gObj.optDouble("x", 0.0),
                            distance = gObj.optInt("distance", 0),
                            duration = gObj.optInt("duration", 0)
                        ))
                    }
                }
            }
        }

        return DirectionsRoute(
            coords = coords,
            guides = guides,
            distanceM = summary.optInt("distance", 0),
            durationS = summary.optInt("duration", 0),
            fare = summary.optJSONObject("fare")?.optInt("toll"),
            taxiFare = summary.optJSONObject("fare")?.optInt("taxi")
        )
    }
}
