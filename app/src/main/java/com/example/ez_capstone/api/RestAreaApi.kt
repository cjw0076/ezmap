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
 * 한국도로공사 고속도로 휴게소 정보 API.
 * Tool: get_rest_areas
 * 경로 상 휴게소 위치/편의시설 조회.
 */
@Singleton
class RestAreaApi @Inject constructor(
    private val apiKeyProvider: ApiKeyProvider
) {
    companion object {
        private const val TAG = "RestAreaApi"
        private const val BASE = "http://data.ex.co.kr/openapi/restinfo"
    }

    private val client = OkHttpClient()

    data class RestArea(
        val name: String,
        val lat: Double,
        val lng: Double,
        val routeName: String,
        val direction: String,
        val hasGasStation: Boolean,
        val hasEvCharger: Boolean,
        val hasRestaurant: Boolean,
        val hasConvenienceStore: Boolean,
        val distanceFromStartM: Int = 0
    )

    suspend fun getRestAreas(routeName: String? = null): List<RestArea> =
        withContext(Dispatchers.IO) {
            val key = apiKeyProvider.dataGoKrKey
            if (key.isBlank()) return@withContext emptyList()

            try {
                val url = "$BASE/restBestfoodList".toHttpUrl().newBuilder().apply {
                    addQueryParameter("key", key)
                    addQueryParameter("type", "json")
                    addQueryParameter("numOfRows", "100")
                    addQueryParameter("pageNo", "1")
                    if (!routeName.isNullOrBlank()) addQueryParameter("routeNm", routeName)
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

                val areas = mutableListOf<RestArea>()
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val aLat = item.optDouble("yValue", 0.0)
                    val aLng = item.optDouble("xValue", 0.0)
                    if (aLat == 0.0 || aLng == 0.0) continue

                    val facilities = item.optString("facility", "")
                    areas.add(RestArea(
                        name = item.optString("restNm", "휴게소"),
                        lat = aLat,
                        lng = aLng,
                        routeName = item.optString("routeNm", ""),
                        direction = item.optString("dir", ""),
                        hasGasStation = facilities.contains("주유"),
                        hasEvCharger = facilities.contains("충전"),
                        hasRestaurant = facilities.contains("식당") || facilities.contains("푸드코트"),
                        hasConvenienceStore = facilities.contains("편의점"),
                        distanceFromStartM = item.optInt("distance", 0)
                    ))
                }
                areas
            } catch (e: Exception) {
                Log.e(TAG, "휴게소 정보 조회 실패: ${e.message}")
                emptyList()
            }
        }
}
