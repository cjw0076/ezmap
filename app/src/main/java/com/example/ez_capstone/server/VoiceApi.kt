package com.example.ez_capstone.server

import com.example.ez_capstone.server.models.SttResponse
import com.example.ez_capstone.server.models.VoiceProcessResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface VoiceApi {
    @Multipart
    @POST("voice/process")
    suspend fun processVoice(
        @Part audio: MultipartBody.Part,
        @Part("conversation_id") conversationId: RequestBody? = null,
        @Part("context") context: RequestBody? = null
    ): VoiceProcessResponse

    @Multipart
    @POST("voice/stt")
    suspend fun speechToText(
        @Part audio: MultipartBody.Part
    ): SttResponse
}
