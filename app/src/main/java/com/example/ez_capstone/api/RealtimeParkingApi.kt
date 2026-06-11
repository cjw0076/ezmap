package com.example.ez_capstone.api

import android.util.Log
import com.example.ez_capstone.config.ApiKeyProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * 서울시 실시간 주차 잔여석 API.
 * Tool: get_realtime_parking (서울 전용)
 */
@Singleton
class RealtimeParkingApi @Inject constructor(
    private val apiKeyProvider: ApiKeyProvider
) {
    companion object {
        private const val TAG = "RealtimeParkingApi"
        private const val BASE = "http://openapi.seoul.go.kr:8088"
    }

    private val client = OkHttpClient()

    data class RealtimeParking(
        val name: String,
        val address: String,
        val lat: Double,
        val lng: Double,
        val capacity: Int,
        val currentParking: Int,
        val availableSpaces: Int,
        val feeInfo: String,
        val updateTime: String
    )

    suspend fun getRealtimeParking(lat: Double, lng: Double, radius: Int = 3000): List<RealtimeParking> =
        withContext(Dispatchers.IO) {
            val key = apiKeyProvider.dataGoKrKey
            if (key.isBlank()) return@withContext emptyList()

            try {
                val url = "$BASE/$key/json/GetParkingInfo/1/50/"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext emptyList()

                val json = JSONObject(body)
                val parkingInfo = json.optJSONObject("GetParkingInfo")
                    ?: return@withContext emptyList()
                val items = parkingInfo.optJSONArray("row")
                    ?: return@withContext emptyList()

                val results = mutableListOf<RealtimeParking>()
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val pLat = item.optDouble("LAT", 0.0)
                    val pLng = item.optDouble("LNG", 0.0)

                    if (pLat == 0.0 || pLng == 0.0) continue
                    if (haversine(lat, lng, pLat, pLng) > radius) continue

                    val capacity = item.optInt("CAPACITY", 0)
                    val curParking = item.optInt("CUR_PARKING", 0)
                    val available = (capacity - curParking).coerceAtLeast(0)

                    results.add(RealtimeParking(
                        name = item.optString("PARKING_NAME", ""),
                        address = item.optString("ADDR", ""),
                        lat = pLat,
                        lng = pLng,
                        capacity = capacity,
                        currentParking = curParking,
                        availableSpaces = available,
                        feeInfo = "${item.optInt("RATES", 0)}원/${item.optInt("TIME_RATE", 0)}분",
                        updateTime = item.optString("CUR_PARKING_TIME", "")
                    ))
                }
                results.sortedBy { haversine(lat, lng, it.lat, it.lng) }.take(10)
            } catch (e: Exception) {
                Log.e(TAG, "서울 실시간 주차 API 실패: ${e.message}")
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
