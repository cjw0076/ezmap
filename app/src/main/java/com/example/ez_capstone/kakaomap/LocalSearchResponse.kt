package com.example.ez_capstone.kakaomap

data class LocalSearchResponse(
    val documents: List<PlaceDocument>
)

data class PlaceDocument(
    val place_name: String,
    val address_name: String,
    val road_address_name: String?,
    val x: String,   // longitude
    val y: String    // latitude
)