package com.example.ez_capstone.navi

import android.util.Log
import com.example.ez_capstone.agent.models.WeatherResult
import com.example.ez_capstone.api.KmaWeatherApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 결빙·안개·폭설 도로 위험 경보 엔진.
 * KmaWeatherApi 기상 데이터 기반으로 위험 유형 감지.
 * RestGuideEngine에서 5분 쿨다운으로 checkHazards() 호출.
 */
@Singleton
class RoadHazardEngine @Inject constructor(
    private val kmaWeatherApi: KmaWeatherApi
) {
    companion object {
        private const val TAG = "RoadHazardEngine"
    }

    suspend fun checkHazards(lat: Double, lng: Double): List<RoadHazardAlert> {
        return try {
            val weather = kmaWeatherApi.getWeather(lat, lng)
            parseHazards(weather)
        } catch (e: Exception) {
            Log.w(TAG, "날씨 기반 위험 감지 실패: ${e.message}")
            emptyList()
        }
    }

    private fun parseHazards(weather: WeatherResult): List<RoadHazardAlert> {
        val alerts = mutableListOf<RoadHazardAlert>()
        val tempC = weather.temp ?: return alerts
        val condition = weather.condition.lowercase()
        val windSpeedMs = weather.windSpeed ?: 0.0
        val roadCondition = weather.roadCondition.lowercase()

        // 결빙: 기온 0°C 이하 + 비/눈 또는 roadCondition=icy
        if (tempC <= 0 && (condition.contains("rain") || condition.contains("snow") || roadCondition == "icy")) {
            alerts.add(RoadHazardAlert(
                hazardType = HazardType.ICY_ROAD,
                message = "도로 결빙 위험 — 서행 권고 (기온 ${tempC}°C)",
                distanceM = 0,
                advisorySpeed = 40
            ))
        }

        // 폭설: 눈 + 기온 -5°C 이하
        if (condition.contains("snow") && tempC <= -5) {
            alerts.add(RoadHazardAlert(
                hazardType = HazardType.HEAVY_SNOW,
                message = "폭설 — 고속도로 진입 전 체인 확인",
                distanceM = 0,
                advisorySpeed = 30
            ))
        }

        // 짙은 안개: roadCondition=foggy 또는 description에 안개 포함
        if (roadCondition.contains("fog") || weather.description.contains("안개")) {
            alerts.add(RoadHazardAlert(
                hazardType = HazardType.FOG,
                message = "짙은 안개 — 상향등 금지, 안개등 사용",
                distanceM = 0,
                advisorySpeed = 50
            ))
        }

        // 강풍: 풍속 20m/s 이상
        if (windSpeedMs >= 20.0) {
            alerts.add(RoadHazardAlert(
                hazardType = HazardType.STRONG_WIND,
                message = "강풍 주의 (${windSpeedMs.toInt()}m/s) — 고속 주행 자제",
                distanceM = 0,
                advisorySpeed = 60
            ))
        }

        return alerts
    }
}
