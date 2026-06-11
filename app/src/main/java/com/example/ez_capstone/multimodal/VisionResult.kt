package com.example.ez_capstone.multimodal

data class VisionResult(
    val extractedText: String,
    val placeName: String?,
    val address: String?,
    val confidence: Float
)
