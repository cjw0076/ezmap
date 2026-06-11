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
 * 공공데이터 주차장 API.
 * Tool: get_parking
 * 근처 주차장, 요금, 운영시간 조회.
 */
@Singleton
class ParkingApi @Inject constructor(
    private val apiKeyProvider: ApiKeyProvider
) {
    companion object {
        private const val TAG = "ParkingApi"
        private const val BASE = "http://apis.data.go.kr/B553881/Parking"
    }

    private val client = OkHttpClient()

    data class ParkingLot(
        val name: String,
        val address: String,
        val lat: Double,
        val lng: Double,
        val totalSpaces: Int,
        val feeInfo: String,
        val operatingHours: String,
        val type: String  // "public", "private"
    )

    suspend fun getParkingLots(
        lat: Double, lng: Double,
        radius: Int = 3000
    ): List<ParkingLot> = withContext(Dispatchers.IO) {
        val key = apiKeyProvider.dataGoKrKey
        if (key.isBlank()) return@withContext emptyList()

        try {
            val url = "$BASE/ParkingInfoService/getParkingInfo".toHttpUrl().newBuilder().apply {
                addQueryParameter("serviceKey", key)
                addQueryParameter("numOfRows", "20")
                addQueryParameter("pageNo", "1")
                addQueryParameter("type", "json")
            }.build()

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()

            val json = JSONObject(body)
            val items = json.optJSONObject("response")
                ?.optJSONObject("body")
                ?.optJSONObject("items")
                ?.optJSONArray("item")
                ?: return@withContext emptyList()

            val results = mutableListOf<ParkingLot>()
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val pLat = item.optDouble("lat", 0.0)
                val pLng = item.optDouble("lng", 0.0)

                if (pLat == 0.0 || pLng == 0.0) continue
                val dist = haversine(lat, lng, pLat, pLng)
                if (dist > radius) continue

                results.add(ParkingLot(
                    name = item.optString("pkNam", ""),
                    address = item.optString("addr", ""),
                    lat = pLat,
                    lng = pLng,
                    totalSpaces = item.optInt("tpkct", 0),
                    feeInfo = "${item.optInt("bscCharge", 0)}원/${item.optInt("bscTime", 0)}분",
                    operatingHours = "${item.optString("svcSrtTe", "")}~${item.optString("svcEndTe", "")}",
                    type = if (item.optString("pkGubun", "") == "공영") "public" else "private"
                ))
            }

            results.sortedBy { haversine(lat, lng, it.lat, it.lng) }.take(10)
        } catch (e: Exception) {
            Log.e(TAG, "주차장 검색 실패: ${e.message}")
            emptyList()
        }
    }

    private fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = kotlin.math.sin(dLat / 2).let { it * it } +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLng / 2).let { it * it }
        return r * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    }
}
