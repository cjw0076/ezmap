package com.example.ez_capstone.server

import retrofit2.http.GET

interface HealthApi {
    @GET("health")
    suspend fun check(): HealthResponse
}

data class HealthResponse(val status: String)
