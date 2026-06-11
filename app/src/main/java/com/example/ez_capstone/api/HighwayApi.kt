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
 * 한국도로공사 고속도로 실시간 문자정보.
 * Tool: get_highway_alerts
 */
@Singleton
class HighwayApi @Inject constructor(
    private val apiKeyProvider: ApiKeyProvider
) {
    companion object {
        private const val TAG = "HighwayApi"
        private const val BASE = "http://data.ex.co.kr/openapi/restinfo/restBestfoodList"
        private const val REALTIME_BASE = "http://data.ex.co.kr/openapi/odtraffic/trafficAmountByRealtime"
    }

    private val client = OkHttpClient()

    data class HighwayAlert(
        val routeName: String,
        val direction: String,
        val message: String,
        val congestionLevel: String,
        val eventType: String
    )

    suspend fun getHighwayAlerts(routeName: String? = null): List<HighwayAlert> =
        withContext(Dispatchers.IO) {
            val key = apiKeyProvider.dataGoKrKey
            if (key.isBlank()) return@withContext emptyList()

            try {
                val url = REALTIME_BASE.toHttpUrl().newBuilder().apply {
                    addQueryParameter("key", key)
                    addQueryParameter("type", "json")
                    addQueryParameter("numOfRows", "20")
                    addQueryParameter("pageNo", "1")
                }.build()

                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext emptyList()

                val json = JSONObject(body)
                val items = json.optJSONArray("realTrafficAmountList")
                    ?: json.optJSONObject("response")?.optJSONObject("body")
                        ?.optJSONArray("items")
                    ?: return@withContext emptyList()

                val results = mutableListOf<HighwayAlert>()
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val rName = item.optString("routeName", "")

                    if (routeName != null && !rName.contains(routeName)) continue

                    val congestion = item.optString("congestion", "원활")
                    val eventType = when (congestion) {
                        "정체" -> "정체"
                        "서행" -> "서행"
                        else -> "정보"
                    }

                    results.add(HighwayAlert(
                        routeName = rName,
                        direction = item.optString("direction", ""),
                        message = "${rName} ${item.optString("sectionName", "")} 구간 $congestion",
                        congestionLevel = congestion,
                        eventType = eventType
                    ))
                }
                results.take(15)
            } catch (e: Exception) {
                Log.e(TAG, "고속도로 API 실패: ${e.message}")
                emptyList()
            }
        }
}
