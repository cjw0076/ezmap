package com.example.ez_capstone.server.models

data class UserInfo(
    val id: Int,
    val kakao_id: Long,
    val nickname: String?,
    val profile_image: String?
)
