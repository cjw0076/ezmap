package com.example.ez_capstone.navi

import android.util.Log
import com.example.ez_capstone.agent.GeminiAgentEngine
import com.example.ez_capstone.agent.models.AgentContext
import com.example.ez_capstone.api.KmaWeatherApi
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemini 기반 AI 자연어 주행 안내 엔진.
 * - enrichGuideText(): 기존 TTS 텍스트를 맥락 기반 자연어로 강화
 * - proactiveCheck(): 5분 간격 날씨-경로 연동 선제 알림
 * 실패 시 항상 원본 텍스트/null 반환 → TTS 중단 없음.
 */
@Singleton
class AiDrivingAdvisor @Inject constructor(
    private val agentEngine: GeminiAgentEngine,
    private val kmaWeatherApi: KmaWeatherApi
) {
    companion object {
        private const val TAG = "AiDrivingAdvisor"
        private const val AI_CHECK_INTERVAL_MS = 5 * 60 * 1000L
        private const val ENRICH_TIMEOUT_MS = 2000L  // 응답 느리면 원본 사용
    }

    private var lastAiCheckMs = 0L
    private var lastWeatherDesc = ""

    /**
     * TTS 안내 텍스트 AI 강화.
     * 2초 내 응답 없으면 원본 반환.
     */
    suspend fun enrichGuideText(
        baseGuideText: String,
        distanceM: Int,
        nearbyPoi: String?,
        speedKmh: Int,
        remainMinutes: Int
    ): String {
        if (distanceM > 300) return baseGuideText  // 300m 이상은 AI 미호출
        if (nearbyPoi.isNullOrBlank() && lastWeatherDesc.isBlank()) return baseGuideText

        return try {
            val prompt = buildEnrichPrompt(baseGuideText, distanceM, nearbyPoi, speedKmh, remainMinutes)
            val context = AgentContext(drivingState = "driving")
            val response = agentEngine.chat(prompt, context)
            val enriched = response.ttsText ?: response.replyText
            if (enriched.length in 5..100) enriched else baseGuideText
        } catch (e: Exception) {
            Log.w(TAG, "AI 안내 강화 실패, 원본 사용: ${e.message}")
            baseGuideText
        }
    }

    /**
     * 5분 간격 날씨-경로 연동 프로액티브 체크.
     * NavigationViewModel.checkRouteContext()에서 호출.
     */
    suspend fun proactiveCheck(
        lat: Double,
        lng: Double,
        speedKmh: Int,
        remainDistanceM: Int,
        remainMinutes: Int
    ): AiAlertUi? {
        val now = System.currentTimeMillis()
        if (now - lastAiCheckMs < AI_CHECK_INTERVAL_MS) return null
        lastAiCheckMs = now

        // 음성 명령 처리 중이거나 다른 chat()이 실행 중이면 건너뜀
        if (agentEngine.isVoiceCommandActive || agentEngine.isChatBusy) {
            Log.d(TAG, "Chat busy — skipping proactive check")
            lastAiCheckMs = 0L  // 다음 주기에 재시도
            return null
        }

        return try {
            val weather = kmaWeatherApi.getWeather(lat, lng)
            lastWeatherDesc = weather.description

            val condition = weather.condition.lowercase()
            val isBad = condition.contains("rain") || condition.contains("snow") || condition.contains("storm")
            if (!isBad || remainDistanceM < 5000) return null

            val prompt = """
                운전 중 안내 메시지를 생성하라.
                현재 날씨: ${weather.description}, 기온 ${weather.temp}°C
                남은 거리: ${remainDistanceM / 1000}km, 예상 시간: ${remainMinutes}분
                20자 이내의 짧은 안전 안내 한 문장만 출력하라.
            """.trimIndent()

            val context = AgentContext(locationX = lng, locationY = lat, drivingState = "driving")
            // 백그라운드 호출은 4초 타임아웃 — 음성 응답을 절대 블록하지 않음
            val response = withTimeoutOrNull(4_000) {
                agentEngine.chat(prompt, context)
            } ?: return null
            val msg = response.ttsText ?: response.replyText
            if (msg.isBlank()) return null

            AiAlertUi(
                type = AiAlertType.HAZARD,
                message = msg,
                autoDismissMs = 8000
            )
        } catch (e: Exception) {
            Log.w(TAG, "프로액티브 체크 실패: ${e.message}")
            null
        }
    }

    private fun buildEnrichPrompt(
        base: String,
        distanceM: Int,
        nearbyPoi: String?,
        speedKmh: Int,
        remainMinutes: Int
    ): String {
        val poiHint = if (!nearbyPoi.isNullOrBlank()) ", 근처 랜드마크: $nearbyPoi" else ""
        return """
            내비게이션 안내 텍스트를 자연스럽게 바꿔라.
            원본: "$base"
            맥락: 거리 ${distanceM}m$poiHint, 현재 속도 ${speedKmh}km/h
            20자 이내, 랜드마크 언급 포함, 단 하나의 문장만 출력하라.
        """.trimIndent()
    }
}
