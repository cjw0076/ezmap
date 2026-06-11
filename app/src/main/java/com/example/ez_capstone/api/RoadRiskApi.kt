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
import kotlin.math.*

/**
 * 도로교통공단(KOROAD) 사고위험구간 API.
 * Tool: get_road_risk
 */
@Singleton
class RoadRiskApi @Inject constructor(
    private val apiKeyProvider: ApiKeyProvider
) {
    companion object {
        private const val TAG = "RoadRiskApi"
        private const val BASE = "http://apis.data.go.kr/B552061/AccidentDeath/getRestAccidentDeath"
    }

    private val client = OkHttpClient()

    data class RoadRisk(
        val segmentName: String,
        val riskLevel: String,
        val accidentCount: Int,
        val deathCount: Int,
        val lat: Double,
        val lng: Double,
        val description: String
    )

    suspend fun getRoadRisk(lat: Double, lng: Double, radius: Int = 5000): List<RoadRisk> =
        withContext(Dispatchers.IO) {
            val key = apiKeyProvider.dataGoKrKey
            if (key.isBlank()) return@withContext emptyList()

            try {
                val url = BASE.toHttpUrl().newBuilder().apply {
                    addQueryParameter("ServiceKey", key)
                    addQueryParameter("searchYearCd", "2024")
                    addQueryParameter("siDo", "")
                    addQueryParameter("guGun", "")
                    addQueryParameter("type", "json")
                    addQueryParameter("numOfRows", "30")
                    addQueryParameter("pageNo", "1")
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

                val results = mutableListOf<RoadRisk>()
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val iLat = item.optDouble("la_crd", 0.0)
                    val iLng = item.optDouble("lo_crd", 0.0)

                    if (iLat == 0.0 || iLng == 0.0) continue
                    if (haversine(lat, lng, iLat, iLng) > radius) continue

                    val accCount = item.optInt("dth_dnv_cnt", 0) + item.optInt("injpsn_cnt", 0)
                    val deathCount = item.optInt("dth_dnv_cnt", 0)
                    val riskLevel = when {
                        deathCount >= 3 -> "매우높음"
                        deathCount >= 1 -> "높음"
                        accCount >= 5 -> "보통"
                        else -> "낮음"
                    }

                    results.add(RoadRisk(
                        segmentName = item.optString("spot_nm", "위험구간"),
                        riskLevel = riskLevel,
                        accidentCount = accCount,
                        deathCount = deathCount,
                        lat = iLat,
                        lng = iLng,
                        description = "${item.optString("occrrnc_lc_nm", "")} - 사고 ${accCount}건"
                    ))
                }
                results.sortedByDescending { it.accidentCount }.take(10)
            } catch (e: Exception) {
                Log.e(TAG, "도로위험도 API 실패: ${e.message}")
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
