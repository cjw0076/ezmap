package com.example.ez_capstone.agent

import android.util.Log
import com.example.ez_capstone.config.ApiKeyProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Gemini REST API 직접 호출 클라이언트.
 * generativelanguage.googleapis.com/v1beta 엔드포인트 사용.
 */
@Singleton
class GeminiApiClient @Inject constructor(
    private val apiKeyProvider: ApiKeyProvider,
    @Named("gemini") private val client: OkHttpClient
) {
    companion object {
        private const val TAG = "GeminiApiClient"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val DEFAULT_MODEL = "gemini-2.5-flash"
        // [DEBUG A/B] 런타임 모델 오버라이드 — Flash↔Pro 통제 비교용. null이면 기본(Flash).
        // 디버그 broadcast "__MODEL__:gemini-2.5-pro" 로만 설정. 프로덕션 경로 불변.
        @Volatile @JvmStatic var modelOverride: String? = null
        val activeModel: String get() = modelOverride ?: DEFAULT_MODEL
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        // 일시적 5xx(예: 503 "high demand") 자동 재시도 — 짧은 지수 백오프
        private const val MAX_SERVER_RETRIES = 2
        private const val SERVER_RETRY_BACKOFF_MS = 800L
        // 429(RPM 초과)는 분당 윈도우 회복에 더 긴 백오프 필요 — 2s, 4s
        private const val RATE_LIMIT_BACKOFF_MS = 2000L
    }

    // 세션 토큰 사용량 추적 (AtomicInteger: Dispatchers.IO 동시 호출 안전)
    private val _promptTokens = AtomicInteger(0)
    private val _completionTokens = AtomicInteger(0)
    val totalPromptTokens: Int get() = _promptTokens.get()
    val totalCompletionTokens: Int get() = _completionTokens.get()
    val totalTokens: Int get() = _promptTokens.get() + _completionTokens.get()
    fun resetTokenCount() { _promptTokens.set(0); _completionTokens.set(0) }

    /**
     * Gemini generateContent 호출.
     * @return candidates[0].content.parts JSONArray
     */
    suspend fun generateContent(
        systemInstruction: String?,
        contents: List<JSONObject>,
        tools: JSONArray?,
        functionCallingMode: String? = null  // "ANY"=도구호출 강제(그라운딩), "NONE"=요약만, null=AUTO
    ): Pair<List<JSONObject>, String> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider.activeGeminiKey
        if (apiKey.isBlank()) throw GeminiApiException.InvalidKey()

        val url = "$BASE_URL/$activeModel:generateContent?key=$apiKey"

        // Request body 구성
        val body = JSONObject().apply {
            // System instruction
            if (!systemInstruction.isNullOrBlank()) {
                put("system_instruction", JSONObject().put(
                    "parts", JSONArray().put(JSONObject().put("text", systemInstruction))
                ))
            }

            // Contents (대화 히스토리)
            put("contents", JSONArray().apply {
                contents.forEach { put(it) }
            })

            // Tools (function declarations)
            if (tools != null && tools.length() > 0) {
                put("tools", JSONArray().put(
                    JSONObject().put("functionDeclarations", tools)
                ))
                // 강제 그라운딩: mode=ANY면 모델이 (intent로 이미 한정된) 도구를 반드시 호출 →
                // 프롬프트 안에서 지어내거나 거절하지 못하고 실제로 앱을 건드려 수행/조회한다.
                if (functionCallingMode != null) {
                    put("tool_config", JSONObject().put(
                        "function_calling_config",
                        JSONObject().put("mode", functionCallingMode)
                    ))
                }
            }

            // Generation config — 토큰 절약 + 응답 일관성
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)        // 낮은 temperature → 일관된 응답
                put("maxOutputTokens", 8192)   // thinking off라 전부 출력 예산. 복잡 멀티툴/긴 응답 여유 확보
                put("topP", 0.9)
                // gemini-2.5-flash는 thinking 모델 — thinking 토큰이 maxOutputTokens를 잠식해
                // 복잡한 요청(경로/다툴)에서 출력 0(빈 응답)이 되는 문제 방지. function-calling
                // 에이전트는 자체 Tool 루프를 돌므로 per-call thinking 불필요.
                // (thinkingBudget=512 실험 결과 tool 후속 JSON 복원 효과 없음 확인 → 0 유지)
                put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
            })

            // Safety settings — 과도한 차단 방지
            put("safetySettings", JSONArray().apply {
                listOf("HARM_CATEGORY_HARASSMENT", "HARM_CATEGORY_HATE_SPEECH",
                    "HARM_CATEGORY_SEXUALLY_EXPLICIT", "HARM_CATEGORY_DANGEROUS_CONTENT").forEach { cat ->
                    put(JSONObject().apply {
                        put("category", cat)
                        put("threshold", "BLOCK_ONLY_HIGH")
                    })
                }
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        Log.d(TAG, "→ Gemini API call [$activeModel] (contents=${contents.size}, tools=${tools?.length() ?: 0})")

        try {
            // 일시적 서버 과부하(5xx, 특히 503 UNAVAILABLE)는 짧은 백오프로 재시도
            var responseBody = ""
            for (attempt in 0..MAX_SERVER_RETRIES) {
                try {
                    responseBody = client.newCall(request).execute().use { response ->
                        val b = response.body?.string() ?: ""
                        if (!response.isSuccessful) handleError(response.code, b)
                        b
                    }
                    break
                } catch (e: GeminiApiException.ServerError) {
                    if (attempt >= MAX_SERVER_RETRIES) throw e
                    Log.w(TAG, "Server error ${e.code} — retry ${attempt + 1}/$MAX_SERVER_RETRIES")
                    kotlinx.coroutines.delay(SERVER_RETRY_BACKOFF_MS * (attempt + 1))
                } catch (e: GeminiApiException.RateLimited) {
                    // 429: 일시적 RPM 초과는 보통 몇 초 뒤 회복 → 사용자가 다시 말 안 해도 자동 재시도
                    if (attempt >= MAX_SERVER_RETRIES) throw e
                    Log.w(TAG, "Rate limited (429) — retry ${attempt + 1}/$MAX_SERVER_RETRIES")
                    kotlinx.coroutines.delay(RATE_LIMIT_BACKOFF_MS * (attempt + 1))
                }
            }

            val json = try {
                JSONObject(responseBody)
            } catch (e: org.json.JSONException) {
                throw IOException("Gemini 응답 파싱 실패: ${e.message}")
            }
            val candidates = json.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                throw GeminiApiException.EmptyResponse()
            }

            val candidate = candidates.getJSONObject(0)
            val finishReason = candidate.optString("finishReason", "")
            if (finishReason.isNotEmpty()) {
                Log.d(TAG, "finishReason=$finishReason")
            }

            val content = candidate.optJSONObject("content")
                ?: throw GeminiApiException.EmptyResponse()

            val role = content.optString("role", "model")
            val partsArray = content.optJSONArray("parts") ?: JSONArray()

            val parts = mutableListOf<JSONObject>()
            for (i in 0 until partsArray.length()) {
                parts.add(partsArray.getJSONObject(i))
            }

            // 토큰 사용량 추적
            val usage = json.optJSONObject("usageMetadata")
            val promptTokens = usage?.optInt("promptTokenCount", 0) ?: 0
            val completionTokens = usage?.optInt("candidatesTokenCount", 0) ?: 0
            _promptTokens.addAndGet(promptTokens)
            _completionTokens.addAndGet(completionTokens)
            Log.d(TAG, "← Gemini response: ${parts.size} parts, role=$role [tokens: prompt=$promptTokens, completion=$completionTokens, session_total=${totalTokens}]")
            Pair(parts, role)
        } catch (e: IOException) {
            throw GeminiApiException.NetworkError(e)
        }
    }

    /**
     * Gemini streamGenerateContent SSE 호출.
     * alt=sse 파라미터로 chunked text를 Flow로 emit.
     */
    fun streamGenerateContent(
        systemInstruction: String?,
        contents: List<JSONObject>
    ): Flow<String> = flow {
        val apiKey = apiKeyProvider.activeGeminiKey
        if (apiKey.isBlank()) throw GeminiApiException.InvalidKey()

        val url = "$BASE_URL/$activeModel:streamGenerateContent?alt=sse&key=$apiKey"

        val body = JSONObject().apply {
            if (!systemInstruction.isNullOrBlank()) {
                put("system_instruction", JSONObject().put(
                    "parts", JSONArray().put(JSONObject().put("text", systemInstruction))
                ))
            }
            put("contents", JSONArray().apply { contents.forEach { put(it) } })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)
                put("maxOutputTokens", 4096)
                put("topP", 0.9)
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .header("Accept", "text/event-stream")
            .build()

        withContext(Dispatchers.IO) {
            // use{} 로 response 보장 닫힘 — 코루틴 취소 시에도 connection 누수 없음
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    handleError(response.code, response.body?.string() ?: "")
                }
                response.body?.source()?.let { source ->
                    val buf = okio.Buffer()
                    while (!source.exhausted()) {
                        source.read(buf, 8192)
                        val line = buf.readUtf8()
                        line.split("\n").forEach { raw ->
                            if (raw.startsWith("data: ") && raw != "data: [DONE]") {
                                try {
                                    val json = JSONObject(raw.removePrefix("data: "))
                                    val text = json
                                        .optJSONArray("candidates")
                                        ?.optJSONObject(0)
                                        ?.optJSONObject("content")
                                        ?.optJSONArray("parts")
                                        ?.optJSONObject(0)
                                        ?.optString("text") ?: ""
                                    if (text.isNotEmpty()) emit(text)
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Gemini Vision API 호출 — 이미지 + 텍스트 프롬프트.
     * @param prompt 텍스트 지시문
     * @param imageBase64 JPEG 이미지 Base64 인코딩
     * @param apiKey Gemini API 키
     * @return candidates[0].content.parts[0].text
     */
    suspend fun generateContentWithImage(
        prompt: String,
        imageBase64: String,
        apiKey: String
    ): String = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/$activeModel:generateContent?key=$apiKey"

        val body = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().apply {
                    put(JSONObject().put("text", prompt))
                    put(JSONObject().put(
                        "inlineData", JSONObject().apply {
                            put("mimeType", "image/jpeg")
                            put("data", imageBase64)
                        }
                    ))
                })
            ))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)
                put("maxOutputTokens", 4096)
                // thinking 토큰이 출력 예산을 잠식하지 않도록 비활성화 (빈 응답 방지)
                put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        Log.d(TAG, "→ Gemini Vision API call")

        try {
            val responseBody = client.newCall(request).execute().use { response ->
                val b = response.body?.string() ?: ""
                if (!response.isSuccessful) handleError(response.code, b)
                b
            }

            val json = try {
                JSONObject(responseBody)
            } catch (e: org.json.JSONException) {
                throw IOException("Gemini 응답 파싱 실패: ${e.message}")
            }
            val candidates = json.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                throw GeminiApiException.EmptyResponse()
            }

            // opt* 체인 — finishReason=SAFETY 등 content 필드 없는 응답에서 JSONException 방지
            val text = candidates.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                .takeUnless { it.isNullOrBlank() }
                ?: throw GeminiApiException.EmptyResponse()
            text
        } catch (e: IOException) {
            throw GeminiApiException.NetworkError(e)
        }
    }

    private fun handleError(code: Int, body: String): Nothing {
        Log.e(TAG, "Gemini API error $code: ${body.take(500)}")
        when (code) {
            429 -> throw GeminiApiException.RateLimited()
            400 -> {
                if (body.contains("API_KEY_INVALID") || body.contains("API key not valid")) {
                    throw GeminiApiException.InvalidKey()
                }
                throw GeminiApiException.ServerError(code, body.take(200))
            }
            401, 403 -> throw GeminiApiException.InvalidKey()
            in 500..599 -> throw GeminiApiException.ServerError(code, "Server error")
            else -> throw GeminiApiException.ServerError(code, body.take(200))
        }
    }
}

sealed class GeminiApiException(message: String) : Exception(message) {
    class RateLimited : GeminiApiException("요청이 너무 많습니다. 잠시 후 다시 시도해주세요.")
    class InvalidKey : GeminiApiException("Gemini API 키가 유효하지 않습니다. 설정에서 확인해주세요.")
    class NetworkError(cause: Throwable) : GeminiApiException("네트워크 오류: ${cause.message}")
    class ServerError(val code: Int, detail: String) : GeminiApiException("서버 오류($code): $detail")
    class EmptyResponse : GeminiApiException("AI 응답이 비어있습니다.")
    class Timeout : GeminiApiException("응답 시간이 초과되었습니다.")
}
