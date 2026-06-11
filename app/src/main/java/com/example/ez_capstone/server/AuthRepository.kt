package com.example.ez_capstone.server

import com.example.ez_capstone.server.models.KakaoAuthRequest
import com.example.ez_capstone.server.models.KakaoAuthResponse
import javax.inject.Inject

class AuthRepository @Inject constructor(private val api: AuthApi) {

    // Legacy no-arg constructor for existing Activities
    constructor() : this(ApiClient.authApi)

    suspend fun loginWithKakao(accessToken: String): KakaoAuthResponse {
        val response = api.loginWithKakao(KakaoAuthRequest(accessToken = accessToken))
        TokenManager.saveToken(response.jwt)
        return response
    }
}
