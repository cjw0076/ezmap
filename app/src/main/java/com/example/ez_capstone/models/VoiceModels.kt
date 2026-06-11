package com.example.ez_capstone.models

import com.google.gson.annotations.SerializedName

data class VoiceProcessResponse(
    val transcript: String? = null,
    val reply: String? = null,
    @SerializedName("reply_text")
    val replyText: String? = null,
    @SerializedName("tts_text")
    val ttsText: String? = null,
    val tts: TtsData? = null,
    val conversation_id: String? = null,
    val action: String? = null,
    @SerializedName("ui_action")
    val uiAction: String? = null,
    @SerializedName("updated_routes")
    val updatedRoutes: List<RouteItem>? = null,
    val intent: IntentData? = null,
    @SerializedName("ui_data")
    val uiData: Map<String, Any>? = null,
    val card: AgentCardPayload? = null
) {
    val displayText: String get() = reply ?: replyText ?: ""
    val effectiveAction: String get() = action ?: uiAction ?: "chat"
}

data class TtsData(
    val text: String,
    val audio_base64: String? = null
)

data class SttResponse(
    val transcript: String? = null
)
