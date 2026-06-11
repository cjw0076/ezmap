package com.example.ez_capstone.navi

import android.util.Log
import com.example.ez_capstone.api.KmaWeatherApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EV 충전소 예열 알림 엔진.
 * 다음 충전소 ETA 15분 전 1회 알림 발생.
 * 겨울철(-5°C 이하) 배터리 보온 경고도 담당.
 */
@Singleton
class ChargingPrewarmAdvisor @Inject constructor(
    private val kmaWeatherApi: KmaWeatherApi
) {
    companion object {
        private const val TAG = "ChargingPrewarmAdvisor"
        private const val PREWARM_TRIGGER_MIN = 15
    }

    private var hasPrewarmed = false
    private var winterWarningShown = false

    /**
     * EvRoutePlan이 있을 때 onLocationUpdate에서 호출.
     * @param remainMinutesToCharger 다음 충전소까지 남은 시간 (분)
     */
    fun checkPrewarm(remainMinutesToCharger: Int): AiAlertUi? {
        if (hasPrewarmed || remainMinutesToCharger > PREWARM_TRIGGER_MIN) return null
        hasPrewarmed = true
        return AiAlertUi(
            type = AiAlertType.AI_RESPONSE,
            message = "충전소 ${remainMinutesToCharger}분 전 — 배터리 예열을 시작하세요 (충전 효율 +25%)",
            autoDismissMs = 20000
        )
    }

    /**
     * 경로 시작 시 또는 온도 급락 감지 시 호출.
     */
    suspend fun checkWinterWarning(lat: Double, lng: Double): AiAlertUi? {
        if (winterWarningShown) return null
        return try {
            val weather = kmaWeatherApi.getWeather(lat, lng)
            val tempC = weather.temp ?: return null
            if (tempC <= -5) {
                winterWarningShown = true
                AiAlertUi(
                    type = AiAlertType.AI_RESPONSE,
                    message = "기온 ${tempC}°C — 배터리 보온 ON 권장 (주행가능거리 감소 방지)",
                    autoDismissMs = 15000
                )
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "겨울 경고 체크 실패: ${e.message}")
            null
        }
    }

    fun reset() {
        hasPrewarmed = false
        winterWarningShown = false
    }
}
