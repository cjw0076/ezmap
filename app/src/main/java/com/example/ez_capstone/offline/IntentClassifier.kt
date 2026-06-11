package com.example.ez_capstone.offline

import javax.inject.Inject
import javax.inject.Singleton

enum class OfflineIntent {
    NAVIGATE_HOME,
    NAVIGATE_WORK,
    CHECK_WEATHER,
    GENERAL_CHAT
}

@Singleton
class IntentClassifier @Inject constructor() {

    private val homeKeywords = listOf("집", "귀가", "home", "돌아가", "집에")
    private val workKeywords = listOf("회사", "출근", "work", "사무실", "직장")
    private val weatherKeywords = listOf("날씨", "기온", "weather", "비", "눈", "맑", "흐림")

    // SAFETY: purely synchronous, no I/O, no coroutines — must return instantly in driving context
    fun classify(input: String): OfflineIntent {
        val lower = input.lowercase()
        return when {
            homeKeywords.any { lower.contains(it) } -> OfflineIntent.NAVIGATE_HOME
            workKeywords.any { lower.contains(it) } -> OfflineIntent.NAVIGATE_WORK
            weatherKeywords.any { lower.contains(it) } -> OfflineIntent.CHECK_WEATHER
            else -> OfflineIntent.GENERAL_CHAT
        }
    }
}
