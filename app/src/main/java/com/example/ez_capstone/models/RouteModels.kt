package com.example.ez_capstone.models

data class RouteRecommendRequest(
    val query: String,
    val origin: LocationPoint,
    val destination: LocationPoint
)

data class LocationPoint(
    val lat: Double,
    val lng: Double,
    val name: String
)

data class RouteRecommendResponse(
    val route_id: Int,
    val routes: List<RouteItem>,
    val agent_message: String,
    val intent: IntentData?
)

data class RouteItem(
    val coords: List<Coord>,
    val distance_m: Int,
    val duration_s: Int,
    val waypoints: List<Waypoint>?,
    val guides: List<Guide>?
)

data class Coord(
    val lat: Double,
    val lng: Double
)

data class Waypoint(
    val name: String,
    val lat: Double,
    val lng: Double
)

data class Guide(
    val lat: Double,
    val lng: Double,
    val name: String,
    val type: Int,
    val guidance: String,
    val distance: Int
)

data class IntentData(
    val transport_mode: String,
    val waypoint_keywords: List<String>,
    val preferences: List<String>,
    val time_constraints: String?
)

data class FeedbackRequest(
    val rating: Int,
    val comment: String?
)

data class FeedbackResponse(val success: Boolean)
