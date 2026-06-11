package com.example.ez_capstone.navi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationCameraStateMachineTest {

    @Test
    fun `starts waiting until first location fix`() {
        assertEquals(NavigationCameraMode.GPS_WAITING, NavigationCameraStateMachine.initial(hasLocation = false))
        assertEquals(NavigationCameraMode.FOLLOW_USER, NavigationCameraStateMachine.initial(hasLocation = true))
    }

    @Test
    fun `map gesture pauses follow and recenter resumes follow`() {
        val panning = NavigationCameraStateMachine.onMapGesture(NavigationCameraMode.FOLLOW_USER)
        assertEquals(NavigationCameraMode.USER_PANNING, panning)

        val following = NavigationCameraStateMachine.onRecenter(panning, hasLocation = true)
        assertEquals(NavigationCameraMode.FOLLOW_USER, following)
    }

    @Test
    fun `overview stops tracking until recentered`() {
        val overview = NavigationCameraStateMachine.onOverview(NavigationCameraMode.FOLLOW_USER)
        assertEquals(NavigationCameraMode.OVERVIEW, overview)
        assertFalse(NavigationCameraStateMachine.shouldTrackUser(overview, hasLocation = true))

        val following = NavigationCameraStateMachine.onRecenter(overview, hasLocation = true)
        assertTrue(NavigationCameraStateMachine.shouldTrackUser(following, hasLocation = true))
    }

    @Test
    fun `timeout restores follow when location exists`() {
        assertEquals(
            NavigationCameraMode.FOLLOW_USER,
            NavigationCameraStateMachine.onTimeout(NavigationCameraMode.USER_PANNING, hasLocation = true)
        )
        assertEquals(
            NavigationCameraMode.GPS_WAITING,
            NavigationCameraStateMachine.onTimeout(NavigationCameraMode.USER_PANNING, hasLocation = false)
        )
    }
}
