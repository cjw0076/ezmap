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

/**
 * ODsay 대중교통 경로 API.
 * Tool: get_transit_route
 * 버스+지하철 최적 경로, 환승 정보, 소요시간.
 */
@Singleton
class OdsayApi @Inject constructor(
    private val apiKeyProvider: ApiKeyProvider
) {
    companion object {
        private const val TAG = "OdsayApi"
        private const val BASE = "https://api.odsay.com/v1/api/searchPubTransPathT"
    }

    private val client = OkHttpClient()

    data class TransitRoute(
        val totalTime: Int,       // 총 소요시간(분)
        val totalDistance: Int,    // 총 거리(m)
        val transferCount: Int,   // 환승 횟수
        val fare: Int,            // 요금
        val pathType: String,     // "bus", "subway", "bus+subway"
        val steps: List<TransitStep>
    )

    data class TransitStep(
        val mode: String,         // "bus", "subway", "walk"
        val lineName: String,     // 버스번호 or 지하철노선
        val startName: String,    // 출발 정류장/역
        val endName: String,      // 도착 정류장/역
        val stationCount: Int,    // 정거장 수
        val sectionTime: Int      // 구간 소요시간(분)
    )

    /**
     * 대중교통 경로 탐색.
     * @param searchType 0=추천, 1=최소시간, 2=최소환승
     */
    suspend fun searchTransitPath(
        startLng: Double, startLat: Double,
        endLng: Double, endLat: Double,
        searchType: Int = 0
    ): List<TransitRoute> = withContext(Dispatchers.IO) {
        val key = apiKeyProvider.odsayKey
        if (key.isBlank()) return@withContext emptyList()

        try {
            val url = BASE.toHttpUrl().newBuilder().apply {
                addQueryParameter("apiKey", key)
                addQueryParameter("SX", startLng.toString())
                addQueryParameter("SY", startLat.toString())
                addQueryParameter("EX", endLng.toString())
                addQueryParameter("EY", endLat.toString())
                addQueryParameter("SearchType", searchType.toString())
                addQueryParameter("output", "json")
            }.build()

            val request = Request.Builder().url(url).build()
            val body = client.getBody(request)  // 중앙 헬퍼: HTTP 실패/네트워크 분류해 던짐

            val json = JSONObject(body)
            val result = json.optJSONObject("result") ?: return@withContext emptyList()
            val paths = result.optJSONArray("path") ?: return@withContext emptyList()

            (0 until paths.length().coerceAtMost(3)).map { i ->
                val path = paths.getJSONObject(i)
                val info = path.optJSONObject("info") ?: JSONObject()
                val subPaths = path.optJSONArray("subPath")

                val steps = mutableListOf<TransitStep>()
                if (subPaths != null) {
                    for (s in 0 until subPaths.length()) {
                        val sub = subPaths.getJSONObject(s)
                        val trafficType = sub.optInt("trafficType", 3)  // 1=지하철, 2=버스, 3=도보

                        steps.add(TransitStep(
                            mode = when (trafficType) { 1 -> "subway"; 2 -> "bus"; else -> "walk" },
                            lineName = sub.optString("lane")
                                .ifBlank { sub.optJSONArray("lane")?.optJSONObject(0)?.optString("name", "") ?: "" },
                            startName = sub.optString("startName", ""),
                            endName = sub.optString("endName", ""),
                            stationCount = sub.optInt("stationCount", 0),
                            sectionTime = sub.optInt("sectionTime", 0)
                        ))
                    }
                }

                val pathType = when (path.optInt("pathType", 0)) {
                    1 -> "subway"
                    2 -> "bus"
                    3 -> "bus+subway"
                    else -> "unknown"
                }

                TransitRoute(
                    totalTime = info.optInt("totalTime", 0),
                    totalDistance = info.optInt("totalDistance", 0),
                    transferCount = info.optInt("transitCount", 0),
                    fare = info.optInt("payment", 0),
                    pathType = pathType,
                    steps = steps
                )
            }
        } catch (e: com.example.ez_capstone.agent.ToolFailureException) {
            throw e  // 분류된 실패 전파
        } catch (e: Exception) {
            Log.e(TAG, "ODsay search failed: ${e.message}")
            throw com.example.ez_capstone.agent.ToolFailureException(
                com.example.ez_capstone.agent.FailureKind.fromThrowable(e), e.message)
        }
    }
}
