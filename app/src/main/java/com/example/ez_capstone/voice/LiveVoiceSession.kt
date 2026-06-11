package com.example.ez_capstone.voice

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 9 — Gemini Live API WebSocket 세션.
 *
 * 기존 STT→LLM→TTS 3단 직렬 파이프라인을 대체.
 * 오디오 PCM 스트림을 WebSocket으로 전송하고, 음성 응답을 스트리밍으로 수신.
 *
 * 레이턴시 목표: 발화 종료 → 첫 음성 출력 ≤ 300ms
 *
 * 생명주기:
 * - startStream()  : WebSocket 연결 + 오디오 전송 시작
 * - sendAudioChunk(): PCM 청크(100ms 단위) 전송
 * - endStream()    : 발화 종료 신호
 * - close()        : 세션 해제 (30초 유휴 자동 해제 포함)
 */
@Singleton
class LiveVoiceSession @Inject constructor() {

    companion object {
        private const val TAG = "LiveVoiceSession"

        // TODO: 실제 Gemini Live API 엔드포인트로 교체
        private const val LIVE_API_WS_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"

        private const val IDLE_TIMEOUT_MS = 30_000L
        private const val MAX_RECONNECT_ATTEMPTS = 3
        private const val PCM_SAMPLE_RATE = 16_000   // 16kHz mono
        private const val CHUNK_DURATION_MS = 100L
    }

    // ── 상태 ──────────────────────────────────────────────────────────────

    sealed class SessionEvent {
        /** Gemini가 생성하는 중간 텍스트 (UI 자막 표시용) */
        data class PartialText(val text: String) : SessionEvent()
        /** 음성 출력용 PCM 청크 */
        data class AudioChunk(val pcm: ByteArray) : SessionEvent()
        /** Function Call 요청 — LiveFunctionCallBridge로 전달 */
        data class ToolCall(val name: String, val argsJson: String, val callId: String) : SessionEvent()
        /** 응답 완료 */
        object TurnComplete : SessionEvent()
        /** 세션 오류 */
        data class Error(val message: String) : SessionEvent()
        /** 연결 상태 변경 */
        data class ConnectionChange(val connected: Boolean) : SessionEvent()
    }

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var reconnectAttempts = 0
    private var idleJob: Job? = null
    private var pendingToolDeclarations: JSONObject? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)   // 스트리밍 — 타임아웃 없음
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    // ── 공개 API ──────────────────────────────────────────────────────────

    /**
     * 세션 시작. apiKey와 modelName을 받아 WebSocket 연결.
     * 이미 연결 중이면 재사용.
     *
     * [toolDeclarations] Live setup에 포함할 tool JSONObject.
     *   형식: {"functionDeclarations": [...]}  null이면 tool 없이 연결.
     *
     * 보안: apiKey는 x-goog-api-key 헤더로 전송 (URL 파라미터 노출 방지).
     * 프로덕션 단계에서는 에페메럴 토큰 릴레이로 교체 필요.
     */
    fun startStream(
        apiKey: String,
        modelName: String = "gemini-2.0-flash-live-001",
        toolDeclarations: JSONObject? = null
    ) {
        if (isConnected) {
            Log.d(TAG, "이미 연결된 세션 재사용")
            resetIdleTimer()
            return
        }
        pendingToolDeclarations = toolDeclarations
        connect(apiKey, modelName)
    }

    /** PCM 오디오 청크 전송 (100ms 단위 권장) */
    fun sendAudioChunk(pcm: ByteArray) {
        if (!isConnected) return
        resetIdleTimer()
        val payload = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("mediaChunks", JSONArray().apply {
                    put(JSONObject().apply {
                        put("mimeType", "audio/pcm;rate=$PCM_SAMPLE_RATE")
                        put("data", android.util.Base64.encodeToString(pcm, android.util.Base64.NO_WRAP))
                    })
                })
            })
        }
        webSocket?.send(payload.toString())
    }

    /** 발화 종료 신호 전송 */
    fun endStream() {
        if (!isConnected) return
        val payload = JSONObject().apply {
            put("clientContent", JSONObject().apply {
                put("turnComplete", true)
            })
        }
        webSocket?.send(payload.toString())
    }

    /**
     * Tool 실행 결과를 세션에 주입.
     * LiveFunctionCallBridge가 호출.
     */
    fun sendToolResponse(callId: String, name: String, resultJson: String) {
        if (!isConnected) return
        val payload = buildToolResponsePayload(callId, name, resultJson)
        webSocket?.send(payload.toString())
    }

    internal fun buildToolResponsePayload(callId: String, name: String, resultJson: String): JSONObject =
        JSONObject().apply {
            put("toolResponse", JSONObject().apply {
                put("functionResponses", JSONArray().apply {
                    put(JSONObject().apply {
                        put("id", callId)
                        put("name", name)
                        put("response", JSONObject(resultJson))
                    })
                })
            })
        }

    /** 세션 종료 및 리소스 해제 */
    fun close() {
        idleJob?.cancel()
        webSocket?.close(1000, "정상 종료")
        webSocket = null
        isConnected = false
        reconnectAttempts = 0
        pendingToolDeclarations = null
        Log.d(TAG, "LiveVoiceSession 종료")
    }

    val connected: Boolean get() = isConnected

    // ── 내부 구현 ─────────────────────────────────────────────────────────

    private fun connect(apiKey: String, modelName: String) {
        val request = Request.Builder()
            .url(LIVE_API_WS_URL)
            .addHeader("x-goog-api-key", apiKey)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket 연결됨")
                isConnected = true
                reconnectAttempts = 0
                scope.launch { _events.emit(SessionEvent.ConnectionChange(true)) }
                sendSetupMessage(ws, modelName)
                resetIdleTimer()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                scope.launch { parseServerMessage(text) }
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                // 바이너리 오디오 청크
                scope.launch { _events.emit(SessionEvent.AudioChunk(bytes.toByteArray())) }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket 오류: ${t.message}")
                isConnected = false
                scope.launch {
                    _events.emit(SessionEvent.ConnectionChange(false))
                    _events.emit(SessionEvent.Error("연결 오류: ${t.javaClass.simpleName}"))
                }
                tryReconnect(apiKey, modelName)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket 종료: code=$code")
                isConnected = false
                scope.launch { _events.emit(SessionEvent.ConnectionChange(false)) }
            }
        })
    }

    /** 세션 초기화 메시지 — 모델 설정 + Tool 선언 */
    private fun sendSetupMessage(ws: WebSocket, modelName: String) {
        val setup = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", "models/$modelName")
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO").put("TEXT"))
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", "Charon")
                            })
                        })
                    })
                })
                pendingToolDeclarations?.let { toolDecl ->
                    put("tools", JSONArray().put(toolDecl))
                }
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", "당신은 EZmap의 AI 내비게이션 비서입니다. 한국어로 간결하게 답변하세요.")
                    }))
                })
            })
        }
        ws.send(setup.toString())
        Log.d(TAG, "setup 메시지 전송 완료")
    }

    private suspend fun parseServerMessage(json: String) {
        try {
            val root = JSONObject(json)

            // 텍스트 청크
            root.optJSONObject("serverContent")?.let { content ->
                content.optJSONArray("modelTurn")
                    ?.let { turns ->
                        for (i in 0 until turns.length()) {
                            val parts = turns.getJSONObject(i).optJSONArray("parts") ?: continue
                            for (j in 0 until parts.length()) {
                                val part = parts.getJSONObject(j)
                                part.optString("text").takeIf { it.isNotBlank() }?.let {
                                    _events.emit(SessionEvent.PartialText(it))
                                }
                                // 인라인 오디오 데이터 (base64)
                                part.optJSONObject("inlineData")?.let { inline ->
                                    if (inline.optString("mimeType").startsWith("audio/")) {
                                        val pcm = android.util.Base64.decode(
                                            inline.optString("data"), android.util.Base64.DEFAULT
                                        )
                                        _events.emit(SessionEvent.AudioChunk(pcm))
                                    }
                                }
                            }
                        }
                    }
                if (content.optBoolean("turnComplete")) {
                    _events.emit(SessionEvent.TurnComplete)
                }
            }

            // Function Call 요청
            root.optJSONObject("toolCall")?.let { toolCall ->
                val calls = toolCall.optJSONArray("functionCalls") ?: return@let
                for (i in 0 until calls.length()) {
                    val call = calls.getJSONObject(i)
                    _events.emit(SessionEvent.ToolCall(
                        name = call.optString("name"),
                        argsJson = call.optJSONObject("args")?.toString() ?: "{}",
                        callId = call.optString("id")
                    ))
                }
            }

        } catch (e: Exception) {
            Log.w(TAG, "서버 메시지 파싱 실패: ${e.message}")
        }
    }

    private fun tryReconnect(apiKey: String, modelName: String) {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "재연결 한도 초과 — 레거시 파이프라인으로 폴백 필요")
            return
        }
        reconnectAttempts++
        val delayMs = (1000L * (1 shl reconnectAttempts)).coerceAtMost(8000L)
        Log.d(TAG, "재연결 시도 $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS (${delayMs}ms 후)")
        scope.launch {
            delay(delayMs)
            connect(apiKey, modelName)
        }
    }

    private fun resetIdleTimer() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(IDLE_TIMEOUT_MS)
            Log.d(TAG, "유휴 타임아웃 — 세션 자동 종료")
            close()
        }
    }
}
