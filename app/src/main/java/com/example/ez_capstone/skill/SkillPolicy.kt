package com.example.ez_capstone.skill

data class SkillPolicy(
    val driving: DrivingPolicy? = null,
    val confirmation: ConfirmationPolicy? = null,
    val fallback: FallbackPolicy? = null
)

data class DrivingPolicy(
    val allowedWhileDriving: Boolean,
    val requiresParked: Boolean = false,
    val rationale: String? = null
)

data class ConfirmationPolicy(
    val required: Boolean = true,
    val prompt: String? = null
)

data class FallbackPolicy(
    val userMessage: String,
    val retryable: Boolean = true
)
