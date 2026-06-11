package com.example.ez_capstone.navi

enum class NavigationCameraMode {
    GPS_WAITING,
    FOLLOW_USER,
    USER_PANNING,
    OVERVIEW,
    MANEUVER_PREVIEW
}

object NavigationCameraStateMachine {
    fun initial(hasLocation: Boolean): NavigationCameraMode {
        return if (hasLocation) NavigationCameraMode.FOLLOW_USER else NavigationCameraMode.GPS_WAITING
    }

    fun onMapGesture(current: NavigationCameraMode): NavigationCameraMode {
        return when (current) {
            NavigationCameraMode.FOLLOW_USER,
            NavigationCameraMode.MANEUVER_PREVIEW -> NavigationCameraMode.USER_PANNING
            else -> current
        }
    }

    fun onRecenter(current: NavigationCameraMode, hasLocation: Boolean): NavigationCameraMode {
        return if (hasLocation) NavigationCameraMode.FOLLOW_USER else NavigationCameraMode.GPS_WAITING
    }

    fun onOverview(current: NavigationCameraMode): NavigationCameraMode {
        return when (current) {
            NavigationCameraMode.GPS_WAITING -> current
            else -> NavigationCameraMode.OVERVIEW
        }
    }

    fun onTimeout(current: NavigationCameraMode, hasLocation: Boolean): NavigationCameraMode {
        return when (current) {
            NavigationCameraMode.USER_PANNING,
            NavigationCameraMode.MANEUVER_PREVIEW -> initial(hasLocation)
            else -> current
        }
    }

    fun shouldTrackUser(current: NavigationCameraMode, hasLocation: Boolean): Boolean {
        return hasLocation && (current == NavigationCameraMode.FOLLOW_USER || current == NavigationCameraMode.MANEUVER_PREVIEW)
    }
}
