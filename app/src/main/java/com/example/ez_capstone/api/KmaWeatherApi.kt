package com.example.ez_capstone.api

import android.util.Log
import com.example.ez_capstone.agent.models.WeatherResult
import com.example.ez_capstone.config.ApiKeyProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * 기상청 초단기실황 API.
 * Tool: get_weather_kma (OpenWeatherMap 대체)
 * 더 정확한 한국 날씨 데이터.
 */
@Singleton
class KmaWeatherApi @Inject constructor(
    private val apiKeyProvider: ApiKeyProvider
) {
    companion object {
        private const val TAG = "KmaWeatherApi"
        private const val BASE = "http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getUltraSrtNcst"
    }

    private val client = OkHttpClient()

    suspend fun getWeather(lat: Double, lng: Double): WeatherResult = withContext(Dispatchers.IO) {
        val key = apiKeyProvider.dataGoKrKey
        if (key.isBlank()) return@withContext WeatherResult()

        try {
            val (nx, ny) = toGridXY(lat, lng)
            val now = LocalDateTime.now()
            val baseDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            // 초단기실황: 매시 30분 발표 → 현재 시각의 정시로 조회
            val baseTime = now.format(DateTimeFormatter.ofPattern("HH")) + "00"

            val url = BASE.toHttpUrl().newBuilder().apply {
                addQueryParameter("serviceKey", key)
                addQueryParameter("numOfRows", "10")
                addQueryParameter("pageNo", "1")
                addQueryParameter("dataType", "JSON")
                addQueryParameter("base_date", baseDate)
                addQueryParameter("base_time", baseTime)
                addQueryParameter("nx", nx.toString())
                addQueryParameter("ny", ny.toString())
            }.build()

            val request = Request.Builder().url(url).build()
            val body = client.getBody(request)  // 중앙 헬퍼: HTTP 실패/네트워크 분류해 던짐

            val json = JSONObject(body)
            val items = json.optJSONObject("response")
                ?.optJSONObject("body")
                ?.optJSONObject("items")
                ?.optJSONArray("item")
                ?: return@withContext WeatherResult()

            var temp: Int? = null
            var pty = 0    // 강수형태 (0없음/1비/2비눈/3눈/5빗방울/6빗방울눈날림/7눈날림)
            var rn1 = 0.0  // 1시간 강수량
            var wsd = 0.0  // 풍속
            var reh = 0    // 습도

            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                when (item.optString("category")) {
                    "T1H" -> temp = item.optString("obsrValue").toDoubleOrNull()?.toInt()
                    "PTY" -> pty = item.optString("obsrValue").toIntOrNull() ?: 0
                    "RN1" -> rn1 = item.optString("obsrValue").toDoubleOrNull() ?: 0.0
                    "WSD" -> wsd = item.optString("obsrValue").toDoubleOrNull() ?: 0.0
                    "REH" -> reh = item.optString("obsrValue").toIntOrNull() ?: 0
                }
            }

            val condition = when (pty) {
                1, 5 -> "rain"
                2, 6 -> "sleet"
                3, 7 -> "snow"
                else -> if (rn1 > 0) "rain" else "clear"
            }

            val roadCondition = when {
                temp != null && temp <= 0 && pty in listOf(2, 3, 6, 7) -> "icy"
                pty in listOf(3, 7) -> "snowy"
                pty in listOf(1, 2, 5, 6) -> "wet"
                wsd > 10 -> "windy"
                else -> "normal"
            }

            val description = buildDescription(temp, pty, rn1, wsd)

            WeatherResult(
                temp = temp,
                condition = condition,
                rainProbability = if (pty > 0) 90 else null,
                windSpeed = wsd,
                description = description,
                roadCondition = roadCondition
            )
        } catch (e: com.example.ez_capstone.agent.ToolFailureException) {
            throw e  // 분류된 실패는 삼키지 말고 중앙으로 전파
        } catch (e: Exception) {
            Log.e(TAG, "기상청 API 실패: ${e.message}")
            throw com.example.ez_capstone.agent.ToolFailureException(
                com.example.ez_capstone.agent.FailureKind.fromThrowable(e), e.message)
        }
    }

    private fun buildDescription(temp: Int?, pty: Int, rn1: Double, wsd: Double): String {
        val parts = mutableListOf<String>()
        temp?.let { parts.add("기온 ${it}°C") }
        when (pty) {
            1 -> parts.add("비")
            2 -> parts.add("비/눈")
            3 -> parts.add("눈")
            5 -> parts.add("빗방울")
            else -> parts.add("맑음")
        }
        if (rn1 > 0) parts.add("강수 ${rn1}mm")
        if (wsd > 5) parts.add("바람 ${wsd}m/s")
        return parts.joinToString(", ")
    }

    /**
     * 위경도 → 기상청 격자 좌표 변환 (Lambert Conformal Conic).
     */
    fun toGridXY(lat: Double, lng: Double): Pair<Int, Int> {
        val RE = 6371.00877
        val GRID = 5.0
        val SLAT1 = 30.0
        val SLAT2 = 60.0
        val OLON = 126.0
        val OLAT = 38.0
        val XO = 43.0
        val YO = 136.0

        val DEGRAD = Math.PI / 180.0
        val re = RE / GRID
        val slat1 = SLAT1 * DEGRAD
        val slat2 = SLAT2 * DEGRAD
        val olon = OLON * DEGRAD
        val olat = OLAT * DEGRAD

        var sn = tan(Math.PI * 0.25 + slat2 * 0.5) / tan(Math.PI * 0.25 + slat1 * 0.5)
        sn = ln(cos(slat1) / cos(slat2)) / ln(sn)
        var sf = tan(Math.PI * 0.25 + slat1 * 0.5)
        sf = sf.pow(sn) * cos(slat1) / sn
        var ro = tan(Math.PI * 0.25 + olat * 0.5)
        ro = re * sf / ro.pow(sn)

        var ra = tan(Math.PI * 0.25 + lat * DEGRAD * 0.5)
        ra = re * sf / ra.pow(sn)
        var theta = lng * DEGRAD - olon
        if (theta > Math.PI) theta -= 2.0 * Math.PI
        if (theta < -Math.PI) theta += 2.0 * Math.PI
        theta *= sn

        val x = (ra * sin(theta) + XO + 0.5).toInt()
        val y = (ro - ra * cos(theta) + YO + 0.5).toInt()
        return Pair(x, y)
    }
}
