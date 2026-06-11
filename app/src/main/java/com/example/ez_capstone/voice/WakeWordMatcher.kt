package com.example.ez_capstone.voice

/**
 * 웨이크워드 "이지야" 감지 + 명령 추출.
 *
 * 2단계 매칭:
 * 1. 정확 매칭 — 하드코딩 변형 21개 (빠름)
 * 2. 유사도 매칭 — Levenshtein 거리 기반 (폴백, Vosk 오인식 대응)
 */
object WakeWordMatcher {

    private const val SIMILARITY_THRESHOLD = 0.65f

    private val WAKE_NAMES = listOf(
        // 기본형 + 호격 변형
        "이지야", "이지아", "이지얌", "이지여",
        "이지아야", "이지야아", "이지아아",
        // 띄어쓰기 변형
        "이지 야", "이지 아",
        // STT 오인식 변형 (Vosk 한국어 모델)
        "이지여어", "이지얘", "이지예",
        "이거야", "이기야", "이기아",
        "이지요", "이지오", "이지랴",
        "이진아", "이지나", "이지니",
        "이지이", "이지에",
        // 영문 변형
        "ez야", "ez아", "easy야", "easy아",
    )

    // 정규화된 웨이크워드 (공백 제거) — 캐시
    private val NORMALIZED_WAKES = WAKE_NAMES.map { it.replace(" ", "").lowercase() }.distinct()

    fun containsWakeWord(text: String): Boolean {
        val normalized = text.lowercase().replace("\\s+".toRegex(), "")
        if (normalized.length < 2) return false

        // 1단계: 정확 매칭 (빠름)
        if (NORMALIZED_WAKES.any { normalized.contains(it) }) return true

        // 2단계: 유사도 매칭 (Vosk 오인식 대응)
        return NORMALIZED_WAKES.any { wake ->
            containsFuzzy(normalized, wake)
        }
    }

    fun extractCommand(originalText: String): String? {
        val lower = originalText.lowercase()
        for (wake in WAKE_NAMES) {
            val wakeLower = wake.lowercase()
            val idx = lower.indexOf(wakeLower)
            if (idx >= 0) {
                val after = originalText.substring(idx + wake.length).trim()
                return if (after.isBlank()) null else after
            }
        }
        // 유사도 매칭으로 찾은 경우 — 가장 유사한 부분 이후를 추출
        val normalized = lower.replace("\\s+".toRegex(), "")
        for (wake in NORMALIZED_WAKES) {
            val match = findFuzzyMatch(normalized, wake)
            if (match != null) {
                val afterIdx = match.second
                if (afterIdx < originalText.length) {
                    val after = originalText.substring(
                        minOf(afterIdx, originalText.length)
                    ).trim()
                    return if (after.isBlank()) null else after
                }
            }
        }
        return null
    }

    // ── 유사도 매칭 ──

    private fun containsFuzzy(text: String, target: String): Boolean {
        if (target.length < 2) return false
        val windowSize = target.length + 1  // 1글자 여유
        if (text.length < target.length - 1) return false

        for (i in 0..maxOf(0, text.length - target.length + 1)) {
            val end = minOf(i + windowSize, text.length)
            val sub = text.substring(i, end)
            if (similarity(sub, target) >= SIMILARITY_THRESHOLD) return true
        }
        return false
    }

    private fun findFuzzyMatch(text: String, target: String): Pair<Int, Int>? {
        if (target.length < 2) return null
        val windowSize = target.length + 1

        for (i in 0..maxOf(0, text.length - target.length + 1)) {
            val end = minOf(i + windowSize, text.length)
            val sub = text.substring(i, end)
            if (similarity(sub, target) >= SIMILARITY_THRESHOLD) {
                return Pair(i, end)
            }
        }
        return null
    }

    private fun similarity(s1: String, s2: String): Float {
        val maxLen = maxOf(s1.length, s2.length)
        if (maxLen == 0) return 1f
        return 1f - levenshteinDistance(s1, s2).toFloat() / maxLen
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // 삭제
                    dp[i][j - 1] + 1,       // 삽입
                    dp[i - 1][j - 1] + cost  // 치환
                )
            }
        }
        return dp[m][n]
    }
}
