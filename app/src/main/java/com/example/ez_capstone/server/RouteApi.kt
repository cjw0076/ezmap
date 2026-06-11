package com.example.ez_capstone.server

import com.example.ez_capstone.server.models.FeedbackRequest
import com.example.ez_capstone.server.models.FeedbackResponse
import com.example.ez_capstone.server.models.RouteRecommendRequest
import com.example.ez_capstone.server.models.RouteRecommendResponse
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface RouteApi {
    @POST("route/recommend")
    suspend fun recommendRoute(@Body request: RouteRecommendRequest): RouteRecommendResponse

    @POST("route/{id}/feedback")
    suspend fun submitFeedback(
        @Path("id") routeId: Int,
        @Body request: FeedbackRequest
    ): FeedbackResponse
}
