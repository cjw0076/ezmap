package com.example.ez_capstone.agent

import org.json.JSONArray
import org.json.JSONObject

/**
 * LLM 프로바이더 추상화 인터페이스.
 * 현재: GeminiProvider (GeminiApiClient 래핑)
 * 향후: ClaudeProvider, OpenAiProvider 등 추가 가능.
 *
 * AgentEngine이 이 인터페이스에만 의존하여,
 * LLM 교체 시 Engine 코드 변경 없이 Provider만 교체.
 */
interface LlmProvider {
    val providerName: String   // "gemini", "claude", "openai"
    val modelName: String      // "gemini-2.5-flash", "claude-sonnet-4", "gpt-4o"

    /**
     * LLM 호출.
     * @param systemInstruction 시스템 프롬프트
     * @param contents 대화 히스토리 (Gemini 포맷: role + parts)
     * @param tools Function calling tool 선언 배열
     * @param config 생성 파라미터
     * @return 응답 parts + 사용량 메타데이터
     */
    suspend fun generateContent(
        systemInstruction: String?,
        contents: List<JSONObject>,
        tools: JSONArray?,
        config: GenerationConfig = GenerationConfig()
    ): LlmResponse

    /** 세션 토큰 사용량 */
    val totalTokens: Int
    fun resetTokenCount()
}

data class GenerationConfig(
    val temperature: Double = 0.3,
    val maxOutputTokens: Int = 1024,
    val topP: Double = 0.9
)

data class LlmResponse(
    val parts: List<JSONObject>,
    val role: String = "model",
    val usage: UsageMetadata? = null
)

data class UsageMetadata(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0
) {
    val totalTokens: Int get() = promptTokens + completionTokens
}
