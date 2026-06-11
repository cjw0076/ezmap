package com.example.ez_capstone.ui.components

object MapCameraPolicy {
    fun shouldFitRoute(routePointCount: Int, trackUser: Boolean): Boolean {
        return routePointCount >= 2 && !trackUser
    }

    fun shouldFitPlaces(placeCount: Int, trackUser: Boolean): Boolean {
        return placeCount >= 2 && !trackUser
    }

    fun shouldCenterSinglePlace(placeCount: Int, trackUser: Boolean): Boolean {
        return placeCount == 1 && !trackUser
    }
}
