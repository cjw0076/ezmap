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
 * 심평원 약국/병원 API (data.go.kr).
 * Tool: search_pharmacies, search_hospitals
 * WGS84 좌표 직접 지원 — 좌표 변환 불필요.
 */
@Singleton
class MedicalApi @Inject constructor(
    private val apiKeyProvider: ApiKeyProvider
) {
    companion object {
        private const val TAG = "MedicalApi"
        private const val PHARMACY_BASE = "http://apis.data.go.kr/B552657/ErmctInsttInfoInqireService/getParmacyListInfoInqire"
        private const val HOSPITAL_BASE = "http://apis.data.go.kr/B552657/ErmctInsttInfoInqireService/getEgytListInfoInqire"
    }

    private val client = OkHttpClient()

    data class MedicalFacility(
        val name: String,
        val address: String,
        val phone: String,
        val lat: Double,
        val lng: Double,
        val type: String,  // "pharmacy" or "hospital"
        val distanceM: Int
    )

    suspend fun search(
        lat: Double,
        lng: Double,
        type: String = "pharmacy"  // "pharmacy" or "hospital"
    ): List<MedicalFacility> = withContext(Dispatchers.IO) {
        val key = apiKeyProvider.dataGoKrKey
        if (key.isBlank()) return@withContext emptyList()

        try {
            val base = if (type == "pharmacy") PHARMACY_BASE else HOSPITAL_BASE
            val url = base.toHttpUrl().newBuilder()
                .addQueryParameter("serviceKey", key)
                .addQueryParameter("WGS84_LON", lng.toString())
                .addQueryParameter("WGS84_LAT", lat.toString())
                .addQueryParameter("pageNo", "1")
                .addQueryParameter("numOfRows", "10")
                .build()

            val req = Request.Builder().url(url).build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: return@withContext emptyList()

            parseResponse(body, lat, lng, type)
        } catch (e: Exception) {
            Log.e(TAG, "Medical search failed ($type)", e)
            emptyList()
        }
    }

    private fun parseResponse(
        xml: String,
        centerLat: Double,
        centerLng: Double,
        type: String
    ): List<MedicalFacility> {
        // data.go.kr 응답이 XML인 경우 파싱
        // JSON 요청 시 &_type=json 추가 가능하지만, 기본은 XML
        val facilities = mutableListOf<MedicalFacility>()

        try {
            // JSON 시도 (XML이면 catch에서 XML 파싱)
            if (xml.trimStart().startsWith("{")) {
                val json = JSONObject(xml)
                val items = json.optJSONObject("response")
                    ?.optJSONObject("body")
                    ?.optJSONObject("items")
                    ?.optJSONArray("item") ?: return emptyList()

                for (i in 0 until minOf(items.length(), 10)) {
                    val item = items.getJSONObject(i)
                    val fLat = item.optDouble("wgs84Lat", 0.0)
                    val fLng = item.optDouble("wgs84Lon", 0.0)
                    val dist = haversineMeters(centerLat, centerLng, fLat, fLng)

                    facilities.add(
                        MedicalFacility(
                            name = item.optString("dutyName", ""),
                            address = item.optString("dutyAddr", ""),
                            phone = item.optString("dutyTel1", ""),
                            lat = fLat,
                            lng = fLng,
                            type = type,
                            distanceM = dist.toInt()
                        )
                    )
                }
            } else {
                // XML 파싱 (간단한 정규식 기반)
                val itemPattern = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
                val items = itemPattern.findAll(xml).toList()

                for (match in items.take(10)) {
                    val content = match.groupValues[1]
                    val name = extractXmlTag(content, "dutyName")
                    val addr = extractXmlTag(content, "dutyAddr")
                    val phone = extractXmlTag(content, "dutyTel1")
                    val fLat = extractXmlTag(content, "wgs84Lat").toDoubleOrNull() ?: 0.0
                    val fLng = extractXmlTag(content, "wgs84Lon").toDoubleOrNull() ?: 0.0
                    val dist = haversineMeters(centerLat, centerLng, fLat, fLng)

                    facilities.add(
                        MedicalFacility(
                            name = name,
                            address = addr,
                            phone = phone,
                            lat = fLat,
                            lng = fLng,
                            type = type,
                            distanceM = dist.toInt()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse error", e)
        }

        return facilities.sortedBy { it.distanceM }
    }

    private fun extractXmlTag(content: String, tag: String): String {
        val pattern = Regex("<$tag>(.*?)</$tag>")
        return pattern.find(content)?.groupValues?.get(1) ?: ""
    }

    private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
