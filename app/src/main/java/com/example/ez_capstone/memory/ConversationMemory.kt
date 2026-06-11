package com.example.ez_capstone.memory

import android.util.Log
import com.example.ez_capstone.db.dao.ConversationDao
import com.example.ez_capstone.db.dao.PreferenceDao
import com.example.ez_capstone.db.entity.PreferenceEntity
import com.example.ez_capstone.profile.MultiProfileManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 대화 기억 관리.
 * - 최근 N턴 대화를 SystemPrompt에 주입할 문자열로 변환
 * - 대화 종료 후 선호도 자동 추출 (Gemini 호출 없이 규칙 기반)
 * - "아까 그 식당" 같은 참조 해결
 */
@Singleton
class ConversationMemory @Inject constructor(
    private val conversationDao: ConversationDao,
    private val preferenceDao: PreferenceDao,
    private val multiProfileManager: MultiProfileManager
) {
    companion object {
        private const val TAG = "ConversationMemory"
        private const val MAX_CONTEXT_TURNS = 10
        private const val MAX_CONTEXT_CHARS = 3000  // 토큰 ~2000 근사

        /** Triple(패턴, 키, 값) — 값은 Float이므로 1.0=true, 0.0=false 식으로 */
        val FOOD_PATTERNS = listOf(
            Triple("매운", "spicy_avoid", 0f),
            Triple("매운 거 좋", "spicy_prefer", 1f),
            Triple("채식", "vegetarian", 1f),
            Triple("해산물 싫", "seafood_avoid", 0f),
            Triple("고기", "meat_prefer", 1f),
        )

        val TRANSPORT_PATTERNS = listOf(
            Triple("대중교통", "public_transit_prefer", 1f),
            Triple("택시", "taxi_prefer", 1f),
            Triple("자차", "car_prefer", 1f),
            Triple("걸어", "walk_prefer", 1f),
            Triple("지하철", "subway_prefer", 1f),
        )
    }

    /**
     * 최근 N턴 대화를 LLM 컨텍스트 주입용 문자열로 변환.
     * @param sessionId 현재 세션 ID
     * @return 포맷팅된 대화 요약 문자열 (빈 문자열이면 주입하지 않음)
     */
    suspend fun getRecentContext(sessionId: String): String {
        return try {
            val messages = conversationDao.getSession(sessionId, MAX_CONTEXT_TURNS)
            if (messages.isEmpty()) return ""

            val formatted = messages
                .sortedBy { it.createdAt }  // 오래된 순서대로
                .joinToString("\n") { msg ->
                    val role = if (msg.role == "user") "사용자" else "EZ"
                    "$role: ${msg.text.take(150)}"  // 각 턴 150자 제한
                }

            // 전체 길이 제한
            if (formatted.length > MAX_CONTEXT_CHARS) {
                formatted.takeLast(MAX_CONTEXT_CHARS)
            } else {
                formatted
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get recent context: ${e.message}")
            ""
        }
    }

    /**
     * 최근 대화에서 참조를 해결.
     * "아까 그 식당", "방금 말한 곳", "그 경로" 등의 참조를 실제 값으로 변환.
     * @return 해결된 문자열 또는 null (해결 불가)
     */
    suspend fun resolveReference(reference: String, sessionId: String): String? {
        return try {
            val messages = conversationDao.getSession(sessionId, 20)
                .sortedByDescending { it.createdAt }

            // "아까 그 식당/가게/곳" → 최근 agent 응답에서 장소명 추출
            if (reference.contains(Regex("(아까|방금|그|저).*(식당|가게|곳|장소|카페|음식점)"))) {
                for (msg in messages) {
                    if (msg.role == "model" && msg.toolsUsed?.contains("search_places") == true) {
                        return extractPlaceName(msg.text)
                    }
                }
            }

            // "지난번 경로/그 경로" → 최근 경로 요약 추출
            if (reference.contains(Regex("(지난번|이전|그).*(경로|길)"))) {
                for (msg in messages) {
                    if (msg.role == "model" && msg.uiAction in listOf("show_route", "start_navigation")) {
                        return extractRouteSummary(msg.text)
                    }
                }
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "Reference resolution failed: ${e.message}")
            null
        }
    }

    /**
     * 규칙 기반 선호도 추출.
     * 대화에서 명시적 선호 표현을 감지하여 PreferenceEntity로 저장.
     */
    suspend fun extractAndSavePreferences(sessionId: String) {
        if (!multiProfileManager.getActiveProfile().memoryEnabled) return
        try {
            val messages = conversationDao.getSession(sessionId, 20)
                .filter { it.role == "user" }
                .sortedByDescending { it.createdAt }

            for (msg in messages) {
                val text = msg.text.lowercase()

                // 음식 선호
                for ((pattern, key, value) in FOOD_PATTERNS) {
                    if (text.contains(pattern)) {
                        preferenceDao.upsert(PreferenceEntity(
                            category = "food",
                            key = key,
                            value = value
                        ))
                        Log.d(TAG, "Extracted preference: food.$key=$value")
                    }
                }

                // 이동수단 선호
                for ((pattern, key, value) in TRANSPORT_PATTERNS) {
                    if (text.contains(pattern)) {
                        preferenceDao.upsert(PreferenceEntity(
                            category = "transport",
                            key = key,
                            value = value
                        ))
                        Log.d(TAG, "Extracted preference: transport.$key=$value")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Preference extraction failed: ${e.message}")
        }
    }

    /**
     * 선호도를 SystemPrompt 주입용 문자열로 포맷.
     */
    suspend fun getPreferenceSummary(): String {
        return try {
            val prefs = preferenceDao.getAll()
            if (prefs.isEmpty()) return ""

            prefs.groupBy { it.category }.entries.joinToString("; ") { (cat, items) ->
                "$cat: ${items.joinToString(", ") { "${it.key}=${it.value}" }}"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Preference summary failed: ${e.message}")
            ""
        }
    }

    // ── 내부 유틸 ──

    private fun extractPlaceName(agentText: String): String? {
        // 간단한 패턴: "OO 식당", "OO 카페" 등에서 명사 추출
        val patterns = listOf(
            Regex("\"([^\"]+)\""),             // 따옴표로 감싼 이름
            Regex("([가-힣]+(?:식당|카페|맛집|음식점|가게))"), // 한글+카테고리
        )
        for (p in patterns) {
            p.find(agentText)?.let { return it.groupValues[1] }
        }
        return null
    }

    private fun extractRouteSummary(agentText: String): String? {
        // "A에서 B까지" 패턴
        val match = Regex("([가-힣\\w]+)에서\\s+([가-힣\\w]+)까지").find(agentText)
        return match?.value
    }

}
