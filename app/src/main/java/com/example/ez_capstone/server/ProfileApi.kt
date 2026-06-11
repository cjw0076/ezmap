package com.example.ez_capstone.server

import retrofit2.http.GET

interface ProfileApi {
    @GET("profile/stats")
    suspend fun getStats(): ProfileStatsResponse

    @GET("profile/preferences")
    suspend fun getPreferences(): ProfilePreferencesResponse
}

data class ProfileStatsResponse(
    val total_routes: Int,
    val total_distance: Int,
    val voice_usage_rate: Float,
    val frequent_routes: List<FrequentRouteData>
)

data class FrequentRouteData(
    val origin_name: String,
    val dest_name: String,
    val count: Int
)

data class ProfilePreferencesResponse(
    val preferences: List<PreferenceData>
)

data class PreferenceData(
    val label: String,
    val value: Float
)
