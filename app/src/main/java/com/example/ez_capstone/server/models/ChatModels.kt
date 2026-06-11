package com.example.ez_capstone.server.models

import com.google.gson.annotations.SerializedName

data class ChatMessageRequest(
    val conversation_id: String? = null,
    val message: String,
    val context: ChatContext? = null
)

data class ChatContext(
    val origin: LocationPoint? = null,
    val destination: LocationPoint? = null,
    val current_location: LocationPoint? = null,
    val current_route_id: Int? = null
)

data class ChatMessageResponse(
    val conversation_id: String? = null,
    val reply: String? = null,
    @SerializedName("reply_text")
    val replyText: String? = null,
    @SerializedName("tts_text")
    val ttsText: String? = null,
    @SerializedName("updated_routes")
    val updatedRoutes: List<RouteItem>? = null,
    @SerializedName("schedule_action")
    val scheduleAction: ScheduleActionPayload? = null,
    val action: String? = null,
    @SerializedName("ui_action")
    val uiAction: String? = null,
    @SerializedName("ui_data")
    val uiData: Map<String, Any>? = null,
    val card: AgentCardPayload? = null
) {
    /** reply 또는 reply_text 중 non-null 값 반환 */
    val displayText: String get() = reply ?: replyText ?: ""
    val effectiveAction: String get() = action ?: uiAction ?: "chat"
}

data class AgentCardPayload(
    val type: String,
    val title: String? = null,
    val body: String? = null,
    val actions: List<String>? = null
)

data class ConversationListResponse(
    val conversations: List<ConversationItem>
)

data class ConversationItem(
    val id: String,
    val title: String?,
    val created_at: String,
    val updated_at: String
)

data class ChatHistoryResponse(
    val conversation_id: String,
    val messages: List<ChatMessage>
)

data class ChatMessage(
    val id: Int,
    val role: String,
    val content: String,
    val metadata: Any? = null,
    val created_at: String
)

data class PlaceItem(
    val name: String,
    val address: String = "",
    val lat: Double,
    val lng: Double,
    val category: String = "",
    val distance_m: Int = 0,
    val price: Int? = null,    // 주유소 가격 (원/L)
    val brand: String? = null  // 주유소 브랜드
)

data class ScheduleEvent(
    val id: String = "",
    val title: String,
    val start_time: String = "",
    val location: String = ""
)
