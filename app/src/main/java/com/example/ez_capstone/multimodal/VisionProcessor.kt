package com.example.ez_capstone.multimodal

import com.example.ez_capstone.agent.GeminiApiClient
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class VisionProcessor @Inject constructor(
    private val apiClient: GeminiApiClient,
    @Named("gemini_api_key") private val apiKey: String
) {
    private val gson = Gson()

    private val visionPrompt = """
        이 이미지에서 가게 이름, 간판 텍스트, 주소를 추출하라.
        반드시 다음 JSON 형식으로만 응답하라 (다른 텍스트 없이):
        {"placeName":"이름 또는 null","address":"주소 또는 null","extractedText":"추출된 텍스트","confidence":0.0~1.0}
    """.trimIndent()

    suspend fun processImageBase64(imageBase64: String): VisionResult {
        val rawResponse = apiClient.generateContentWithImage(visionPrompt, imageBase64, apiKey)
        return try {
            @Suppress("UNCHECKED_CAST")
            val parsed = gson.fromJson(rawResponse.trim(), Map::class.java) as Map<String, Any?>
            VisionResult(
                extractedText = parsed["extractedText"]?.toString() ?: rawResponse,
                placeName = parsed["placeName"]?.toString()?.takeIf { it != "null" },
                address = parsed["address"]?.toString()?.takeIf { it != "null" },
                confidence = (parsed["confidence"] as? Double)?.toFloat() ?: 0.5f
            )
        } catch (e: JsonSyntaxException) {
            VisionResult(extractedText = rawResponse, placeName = null, address = null, confidence = 0.0f)
        }
    }
}
