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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenWeatherMap 날씨 API.
 * 서버 weatherService.js 포팅.
 */
@Singleton
class WeatherApi @Inject constructor(
    private val apiKeyProvider: ApiKeyProvider
) {
    companion object {
        private const val TAG = "WeatherApi"
        private const val BASE = "https://api.openweathermap.org/data/2.5/weather"
    }

    private val client = OkHttpClient()

    // 간단한 인메모리 캐시 (30분)
    private var cachedResult: WeatherResult? = null
    private var cacheKey: String = ""
    private var cacheTime: Long = 0

    suspend fun getWeather(lat: Double, lng: Double): WeatherResult = withContext(Dispatchers.IO) {
        val key = apiKeyProvider.weatherKey
        // 실패를 삼키지 않고 던진다 → 중앙 catch가 분류(silent failure 방지).
        if (key.isBlank()) throw com.example.ez_capstone.agent.ToolFailureException(
            com.example.ez_capstone.agent.FailureKind.MISSING_KEY, "weather: API 키 없음")

        // 캐시 확인 (30분)
        val roundedKey = "${String.format("%.2f", lat)},${String.format("%.2f", lng)}"
        if (cacheKey == roundedKey && System.currentTimeMillis() - cacheTime < 30 * 60 * 1000) {
            cachedResult?.let { return@withContext it }
        }

        try {
            val url = BASE.toHttpUrl().newBuilder().apply {
                addQueryParameter("lat", lat.toString())
                addQueryParameter("lon", lng.toString())
                addQueryParameter("appid", key)
                addQueryParameter("units", "metric")
                addQueryParameter("lang", "kr")
            }.build()

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string()
                ?: throw com.example.ez_capstone.agent.ToolFailureException(
                    com.example.ez_capstone.agent.FailureKind.UPSTREAM, "weather: 빈 응답")

            if (!response.isSuccessful) {
                Log.e(TAG, "weather error ${response.code}")
                throw com.example.ez_capstone.agent.ToolFailureException(
                    com.example.ez_capstone.agent.FailureKind.fromHttp(response.code),
                    "weather HTTP ${response.code}")
            }

            val json = JSONObject(body)
            val main = json.optJSONObject("main")
            val weatherArr = json.optJSONArray("weather")
            val wind = json.optJSONObject("wind")
            val weatherId = weatherArr?.optJSONObject(0)?.optInt("id", 800) ?: 800
            val temp = main?.optDouble("temp")?.toInt()

            val condition = when (weatherId) {
                in 200..299 -> "thunderstorm"
                in 300..399 -> "rain"
                in 500..599 -> "rain"
                in 600..699 -> "snow"
                in 700..799 -> "fog"
                800 -> "clear"
                in 801..804 -> "cloudy"
                else -> "unknown"
            }

            val roadCondition = when {
                temp != null && temp <= 0 -> "icy"
                weatherId in 600..699 -> "snowy"
                weatherId in 500..599 -> "wet"
                weatherId in 700..799 -> "foggy"
                else -> "normal"
            }

            val result = WeatherResult(
                temp = temp,
                condition = condition,
                rainProbability = if (condition == "rain") 80 else null,
                windSpeed = wind?.optDouble("speed"),
                description = weatherArr?.optJSONObject(0)?.optString("description", "알 수 없음") ?: "알 수 없음",
                roadCondition = roadCondition
            )

            // 캐시 저장
            cachedResult = result
            cacheKey = roundedKey
            cacheTime = System.currentTimeMillis()

            result
        } catch (e: com.example.ez_capstone.agent.ToolFailureException) {
            throw e  // 분류된 실패는 삼키지 말고 중앙으로 전파
        } catch (e: Exception) {
            // 네트워크/파싱 등 미분류 예외도 분류해 던진다(빈값 위장 금지).
            Log.e(TAG, "weather fetch failed: ${e.message}")
            throw com.example.ez_capstone.agent.ToolFailureException(
                com.example.ez_capstone.agent.FailureKind.fromThrowable(e), e.message)
        }
    }

    private fun defaultWeather() = WeatherResult()
}
