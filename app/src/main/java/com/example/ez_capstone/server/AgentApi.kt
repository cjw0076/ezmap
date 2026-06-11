package com.example.ez_capstone.server

import retrofit2.http.Body
import retrofit2.http.POST

interface AgentApi {
    @POST("api/agent/chat")
    suspend fun chat(@Body request: AgentChatRequest): AgentChatResponse
}

data class AgentChatRequest(
    val message: String,
    val conversation_id: String? = null,
    val context: AgentChatContext? = null
)

data class AgentChatContext(
    val current_location: LocationData? = null,
    val destination: LocationData? = null,
    val current_route_id: Int? = null
)

data class LocationData(
    val lat: Double,
    val lng: Double,
    val name: String? = null
)

data class AgentChatResponse(
    val conversation_id: String,
    val reply_text: String,
    val tts_text: String? = null,
    val ui_action: String? = null,
    val ui_data: Any? = null,
    val follow_up: String? = null
)
