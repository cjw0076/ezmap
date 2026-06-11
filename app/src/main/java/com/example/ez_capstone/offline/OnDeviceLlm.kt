package com.example.ez_capstone.offline

import com.example.ez_capstone.agent.models.AgentContext
import com.example.ez_capstone.agent.models.AgentResponse
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline LLM fallback for driving context.
 * SAFETY: handleOffline() is synchronous — no network, no coroutine suspension.
 * Speed is as critical as accuracy when the device is offline mid-navigation.
 */
@Singleton
class OnDeviceLlm @Inject constructor(
    private val classifier: IntentClassifier,
    private val offlineCache: OfflineCache
) {
    fun handleOffline(userText: String, context: AgentContext): AgentResponse {
        return when (classifier.classify(userText)) {
            OfflineIntent.NAVIGATE_HOME -> {
                val cached = offlineCache.getCachedRoute("집")
                if (cached != null) {
                    AgentResponse(
                        replyText = "오프라인 모드입니다. 이전에 저장된 집 경로를 사용합니다. 약 ${cached.durationMin}분 예상입니다.",
                        ttsText = "오프라인 모드. 캐시된 집 경로 안내를 시작합니다.",
                        uiAction = "navigate_cached"
                    )
                } else offlineNotice()
            }
            OfflineIntent.NAVIGATE_WORK -> {
                val cached = offlineCache.getCachedRoute("회사")
                if (cached != null) {
                    AgentResponse(
                        replyText = "오프라인 모드입니다. 이전에 저장된 회사 경로를 사용합니다. 약 ${cached.durationMin}분 예상입니다.",
                        ttsText = "오프라인 모드. 캐시된 회사 경로 안내를 시작합니다.",
                        uiAction = "navigate_cached"
                    )
                } else offlineNotice()
            }
            OfflineIntent.CHECK_WEATHER -> {
                // AgentContext: locationX = longitude, locationY = latitude
                val cached = offlineCache.getCachedWeather(
                    context.locationY ?: 37.5665,
                    context.locationX ?: 126.9780
                )
                if (cached != null) {
                    AgentResponse(
                        replyText = "오프라인 모드입니다. 마지막으로 조회된 날씨: ${cached.desc}, ${cached.temp.toInt()}°C.",
                        ttsText = "캐시된 날씨 정보: ${cached.desc}, ${cached.temp.toInt()}도.",
                        uiAction = "none"
                    )
                } else offlineNotice()
            }
            OfflineIntent.GENERAL_CHAT -> offlineNotice()
        }
    }

    private fun offlineNotice() = AgentResponse(
        replyText = "현재 인터넷에 연결되어 있지 않습니다. 인터넷 연결 후 다시 말씀해주세요.",
        ttsText = "인터넷 연결이 필요합니다.",
        uiAction = "none"
    )
}
