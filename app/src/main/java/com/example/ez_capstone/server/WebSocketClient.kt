package com.example.ez_capstone.server

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "WebSocketClient"
        private const val WS_BASE_URL = "ws://52.79.105.63/ws/"
        private const val NORMAL_CLOSE = 1000
    }

    sealed class WsMessage {
        data class AgentResponse(
            val conversationId: String?,
            val replyText: String,
            val ttsText: String?,
            val uiAction: String?,
            val uiData: Any?
        ) : WsMessage()

        data class DrivingResponse(
            val event: String,
            val replyText: String?,
            val ttsText: String?,
            val uiAction: String?,
            val uiData: Any?,
            val suggestion: Any?
        ) : WsMessage()

        data object LocationAck : WsMessage()

        data class Error(val message: String) : WsMessage()
    }

    private var ws: WebSocket? = null
    private val _messages = MutableSharedFlow<WsMessage>(extraBufferCapacity = 64)
    val messages: SharedFlow<WsMessage> = _messages.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    fun connect(userId: String, token: String) {
        disconnect()

        val url = "${WS_BASE_URL}${userId}?token=${token}"
        val request = Request.Builder().url(url).build()

        ws = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected")
                _connected.value = true
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closing: $code $reason")
                webSocket.close(NORMAL_CLOSE, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code $reason")
                _connected.value = false
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                _connected.value = false
                _messages.tryEmit(WsMessage.Error(t.message ?: "WebSocket 연결 실패"))
            }
        })
    }

    fun disconnect() {
        ws?.close(NORMAL_CLOSE, "Client disconnect")
        ws = null
        _connected.value = false
    }

    fun sendVoiceText(text: String, conversationId: String?, context: Map<String, Any>?) {
        val payload = JsonObject().apply {
            addProperty("type", "voice_text")
            addProperty("text", text)
            conversationId?.let { addProperty("conversation_id", it) }
            context?.let { add("context", gson.toJsonTree(it)) }
        }
        send(payload)
    }

    fun sendLocationUpdate(lat: Double, lng: Double, speedKmh: Int, heading: Float) {
        val payload = JsonObject().apply {
            addProperty("type", "location_update")
            addProperty("lat", lat)
            addProperty("lng", lng)
            addProperty("speed_kmh", speedKmh)
            addProperty("heading", heading)
        }
        send(payload)
    }

    fun sendDrivingEvent(event: String, data: Map<String, Any>?) {
        val payload = JsonObject().apply {
            addProperty("type", "driving_event")
            addProperty("event", event)
            data?.let { add("data", gson.toJsonTree(it)) }
        }
        send(payload)
    }

    private fun send(payload: JsonObject) {
        val json = gson.toJson(payload)
        val sent = ws?.send(json) ?: false
        if (!sent) {
            Log.w(TAG, "Failed to send message (ws=${ws != null}, connected=${_connected.value})")
        }
    }

    private fun parseMessage(text: String) {
        try {
            val json = gson.fromJson(text, JsonObject::class.java)
            val type = json.get("type")?.asString

            val message = when (type) {
                "agent_response" -> WsMessage.AgentResponse(
                    conversationId = json.get("conversation_id")?.asString,
                    replyText = json.get("reply_text")?.asString ?: "",
                    ttsText = json.get("tts_text")?.asString,
                    uiAction = json.get("ui_action")?.asString,
                    uiData = json.get("ui_data")
                )
                "driving_response" -> WsMessage.DrivingResponse(
                    event = json.get("event")?.asString ?: "",
                    replyText = json.get("reply_text")?.asString,
                    ttsText = json.get("tts_text")?.asString,
                    uiAction = json.get("ui_action")?.asString,
                    uiData = json.get("ui_data"),
                    suggestion = json.get("suggestion")
                )
                "location_ack" -> WsMessage.LocationAck
                else -> {
                    Log.w(TAG, "Unknown message type: $type")
                    null
                }
            }
            message?.let { _messages.tryEmit(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}")
            _messages.tryEmit(WsMessage.Error("메시지 파싱 실패: ${e.message}"))
        }
    }
}
