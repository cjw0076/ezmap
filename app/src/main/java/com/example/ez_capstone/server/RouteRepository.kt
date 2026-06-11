package com.example.ez_capstone.server

import com.example.ez_capstone.server.models.FeedbackRequest
import com.example.ez_capstone.server.models.LocationPoint
import com.example.ez_capstone.server.models.RouteRecommendRequest
import com.example.ez_capstone.server.models.RouteRecommendResponse
import javax.inject.Inject

class RouteRepository @Inject constructor(private val api: RouteApi) {

    // Legacy no-arg constructor for existing Activities
    constructor() : this(ApiClient.routeApi)

    suspend fun recommendRoute(
        query: String,
        origin: LocationPoint,
        destination: LocationPoint
    ): RouteRecommendResponse = api.recommendRoute(RouteRecommendRequest(query, origin, destination))

    suspend fun submitFeedback(routeId: Int, rating: Int, comment: String?) {
        api.submitFeedback(routeId, FeedbackRequest(rating, comment))
    }
}
