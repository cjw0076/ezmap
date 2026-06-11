package com.example.ez_capstone.server

import com.example.ez_capstone.server.models.KakaoAuthRequest
import com.example.ez_capstone.server.models.KakaoAuthResponse
import com.example.ez_capstone.server.models.UserInfo
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/kakao")
    suspend fun loginWithKakao(@Body request: KakaoAuthRequest): KakaoAuthResponse

    @GET("auth/me")
    suspend fun getMe(): UserInfo
}
