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
 * 전기차 충전소 API (한국환경공단).
 * Tool: get_ev_chargers
 * 실시간 충전 가능 여부 포함.
 */
@Singleton
class EvChargerApi @Inject constructor(
    private val apiKeyProvider: ApiKeyProvider
) {
    companion object {
        private const val TAG = "EvChargerApi"
        private const val BASE = "http://apis.data.go.kr/B552584/EvCharger/getChargerInfo"
    }

    private val client = OkHttpClient()

    data class EvCharger(
        val name: String,
        val address: String,
        val lat: Double,
        val lng: Double,
        val chargerType: String,
        val status: String,      // "available", "charging", "broken", "checking"
        val statusText: String,
        val operator: String = ""
    )

    suspend fun getChargers(
        lat: Double, lng: Double,
        radius: Int = 5000,
        statusFilter: Boolean = false  // true = 충전가능만
    ): List<EvCharger> = withContext(Dispatchers.IO) {
        val key = apiKeyProvider.dataGoKrKey
        if (key.isBlank()) return@withContext emptyList()

        try {
            val url = BASE.toHttpUrl().newBuilder().apply {
                addQueryParameter("serviceKey", key)
                addQueryParameter("numOfRows", "20")
                addQueryParameter("pageNo", "1")
                addQueryParameter("dataType", "JSON")
            }.build()

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()

            val json = JSONObject(body)
            val items = json.optJSONObject("items")?.optJSONArray("item")
                ?: return@withContext emptyList()

            val results = mutableListOf<EvCharger>()
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val cLat = item.optDouble("lat", 0.0)
                val cLng = item.optDouble("lng", 0.0)

                // 반경 필터 (API가 좌표 필터 미지원이므로 앱에서 후처리)
                if (cLat == 0.0 || cLng == 0.0) continue
                val dist = haversine(lat, lng, cLat, cLng)
                if (dist > radius) continue

                val stat = item.optString("stat", "9")
                val statusPair = parseStatus(stat)

                if (statusFilter && statusPair.first != "available") continue

                results.add(EvCharger(
                    name = item.optString("statNm", ""),
                    address = item.optString("addr", ""),
                    lat = cLat,
                    lng = cLng,
                    chargerType = parseChargerType(item.optString("chgerType", "")),
                    status = statusPair.first,
                    statusText = statusPair.second,
                    operator = item.optString("busiNm", "")
                ))
            }

            results.take(15)
        } catch (e: Exception) {
            Log.e(TAG, "충전소 검색 실패: ${e.message}")
            emptyList()
        }
    }

    private fun parseStatus(code: String): Pair<String, String> = when (code) {
        "1" -> "error" to "통신이상"
        "2" -> "available" to "충전가능"
        "3" -> "charging" to "충전중"
        "4" -> "offline" to "운영중지"
        "5" -> "checking" to "점검중"
        else -> "unknown" to "알 수 없음"
    }

    private fun parseChargerType(code: String): String = when (code) {
        "01" -> "DC차데모"
        "02" -> "AC완속"
        "03" -> "DC차데모+AC3상"
        "04" -> "DC콤보"
        "05" -> "DC차데모+DC콤보"
        "06" -> "DC차데모+AC3상+DC콤보"
        "07" -> "AC3상"
        else -> code
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
