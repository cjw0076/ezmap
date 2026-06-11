package com.example.ez_capstone.server

import com.example.ez_capstone.server.models.ChatHistoryResponse
import com.example.ez_capstone.server.models.ChatMessageRequest
import com.example.ez_capstone.server.models.ChatMessageResponse
import com.example.ez_capstone.server.models.ConversationListResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ChatApi {
    @POST("chat/message")
    suspend fun sendMessage(@Body request: ChatMessageRequest): ChatMessageResponse

    @GET("chat/history")
    suspend fun getConversations(): ConversationListResponse

    @GET("chat/history")
    suspend fun getChatHistory(
        @Query("conversation_id") conversationId: String,
        @Query("limit") limit: Int = 50
    ): ChatHistoryResponse
}
