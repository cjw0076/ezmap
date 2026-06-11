package com.example.ez_capstone.server.models

data class KakaoAuthResponse(
    val jwt: String,
    val user: UserInfo
)
