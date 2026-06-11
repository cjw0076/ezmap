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
 * 에어코리아 대기질 API (data.go.kr).
 * Tool: get_air_quality
 * 3단계: WGS84→TM 변환 → 가까운 측정소 → 실시간 측정.
 */
@Singleton
class AirKoreaApi @Inject constructor(
    private val apiKeyProvider: ApiKeyProvider
) {
    companion object {
        private const val TAG = "AirKoreaApi"
        private const val STATION_BASE = "http://apis.data.go.kr/B552584/MsrstnInfoInqireSvc/getNearbyMsrstnList"
        private const val MEASURE_BASE = "http://apis.data.go.kr/B552584/ArpltnInforInqireSvc/getMsrstnAcctoRltmMesureDnsty"
        private const val KAKAO_TRANSCOORD = "https://dapi.kakao.com/v2/local/geo/transcoord.json"
    }

    private val client = OkHttpClient()

    // 30분 캐시 (측정소별)
    private var cache: Pair<String, AirQuality>? = null
    private var cacheTime = 0L

    data class AirQuality(
        val stationName: String = "",
        val pm10: Int? = null,
        val pm25: Int? = null,
        val khaiValue: Int? = null,
        val khaiGrade: String = "정보없음",
        val coValue: Double? = null,
        val o3Value: Double? = null,
        val dataTime: String = ""
    )

    suspend fun getAirQuality(lat: Double, lng: Double): AirQuality = withContext(Dispatchers.IO) {
        val dataGoKrKey = apiKeyProvider.dataGoKrKey
        if (dataGoKrKey.isBlank()) return@withContext AirQuality(khaiGrade = "API 키 없음")

        try {
            // Step 1: WGS84 → TM 좌표 변환 (카카오 API)
            val (tmX, tmY) = convertToTM(lng, lat) ?: return@withContext AirQuality(khaiGrade = "좌표 변환 실패")

            // Step 2: 가장 가까운 측정소 찾기
            val stationName = findNearestStation(tmX, tmY, dataGoKrKey)
                ?: return@withContext AirQuality(khaiGrade = "측정소 없음")

            // 캐시 확인 (30분)
            val now = System.currentTimeMillis()
            val cached = cache
            if (cached != null && cached.first == stationName && now - cacheTime < 1_800_000) {
                return@withContext cached.second
            }

            // Step 3: 실시간 측정 데이터
            val result = getMeasurement(stationName, dataGoKrKey)
            cache = stationName to result
            cacheTime = now
            result
        } catch (e: NotSubscribedException) {
            // data.go.kr이 JSON 대신 "Forbidden" 등을 반환 = 해당 서비스 미신청 키(스택트레이스 불필요)
            Log.w(TAG, "Air quality: 대기오염정보 서비스 미신청 (응답='${e.message}')")
            AirQuality(khaiGrade = "서비스 미신청")
        } catch (e: Exception) {
            Log.e(TAG, "Air quality fetch failed", e)
            val notSubscribed = (e.message ?: "").contains("Forbidden", ignoreCase = true)
            AirQuality(khaiGrade = if (notSubscribed) "서비스 미신청" else "조회 실패")
        }
    }

    /** data.go.kr 미신청 키가 JSON 대신 평문(Forbidden 등)을 반환할 때. */
    private class NotSubscribedException(body: String) : Exception(body)

    /** WGS84 (lng, lat) → TM (x, y) 변환 via 카카오 transcoord API */
    private fun convertToTM(lng: Double, lat: Double): Pair<Double, Double>? {
        val kakaoKey = apiKeyProvider.activeKakaoKey
        if (kakaoKey.isBlank()) return null

        val url = KAKAO_TRANSCOORD.toHttpUrl().newBuilder()
            .addQueryParameter("x", lng.toString())
            .addQueryParameter("y", lat.toString())
            .addQueryParameter("input_coord", "WGS84")
            .addQueryParameter("output_coord", "TM")
            .build()

        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "KakaoAK $kakaoKey")
            .build()

        val resp = client.newCall(req).execute()
        val body = resp.body?.string() ?: return null
        val json = JSONObject(body)
        val docs = json.optJSONArray("documents") ?: return null
        if (docs.length() == 0) return null

        val doc = docs.getJSONObject(0)
        return doc.getDouble("x") to doc.getDouble("y")
    }

    /** TM 좌표로 가장 가까운 측정소 이름 찾기 */
    private fun findNearestStation(tmX: Double, tmY: Double, key: String): String? {
        val url = STATION_BASE.toHttpUrl().newBuilder()
            .addQueryParameter("serviceKey", key)
            .addQueryParameter("tmX", tmX.toString())
            .addQueryParameter("tmY", tmY.toString())
            .addQueryParameter("returnType", "json")
            .build()

        val req = Request.Builder().url(url).build()
        val resp = client.newCall(req).execute()
        val body = resp.body?.string() ?: return null
        // data.go.kr 미신청 키는 JSON 대신 "Forbidden" 등 평문을 반환 → 파싱 전 차단(스택트레이스 방지)
        if (!body.trimStart().startsWith("{")) throw NotSubscribedException(body.take(40))
        val json = JSONObject(body)

        val items = json.optJSONObject("response")
            ?.optJSONObject("body")
            ?.optJSONArray("items") ?: return null

        if (items.length() == 0) return null
        return items.getJSONObject(0).optString("stationName")
    }

    /** 측정소의 실시간 대기질 데이터 */
    private fun getMeasurement(stationName: String, key: String): AirQuality {
        val url = MEASURE_BASE.toHttpUrl().newBuilder()
            .addQueryParameter("serviceKey", key)
            .addQueryParameter("stationName", stationName)
            .addQueryParameter("dataTerm", "DAILY")
            .addQueryParameter("returnType", "json")
            .addQueryParameter("ver", "1.5")
            .addQueryParameter("numOfRows", "1")
            .build()

        val req = Request.Builder().url(url).build()
        val resp = client.newCall(req).execute()
        val body = resp.body?.string() ?: return AirQuality(stationName = stationName)
        if (!body.trimStart().startsWith("{")) throw NotSubscribedException(body.take(40))
        val json = JSONObject(body)

        val items = json.optJSONObject("response")
            ?.optJSONObject("body")
            ?.optJSONArray("items") ?: return AirQuality(stationName = stationName)

        if (items.length() == 0) return AirQuality(stationName = stationName)
        val item = items.getJSONObject(0)

        val khaiGradeNum = item.optString("khaiGrade", "0").toIntOrNull() ?: 0
        val gradeText = when (khaiGradeNum) {
            1 -> "좋음"
            2 -> "보통"
            3 -> "나쁨"
            4 -> "매우나쁨"
            else -> "정보없음"
        }

        return AirQuality(
            stationName = stationName,
            pm10 = item.optString("pm10Value", "-").toIntOrNull(),
            pm25 = item.optString("pm25Value", "-").toIntOrNull(),
            khaiValue = item.optString("khaiValue", "-").toIntOrNull(),
            khaiGrade = gradeText,
            coValue = item.optString("coValue", "-").toDoubleOrNull(),
            o3Value = item.optString("o3Value", "-").toDoubleOrNull(),
            dataTime = item.optString("dataTime", "")
        )
    }
}
