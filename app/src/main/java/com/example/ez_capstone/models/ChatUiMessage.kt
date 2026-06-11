package com.example.ez_capstone.models

data class ChatUiMessage(
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    // 에이전트 의사결정 근거 요약(설명가능성). assistant 메시지에서 "왜?"로 펼쳐 봄.
    val explanation: String? = null
)
