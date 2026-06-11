package com.example.ez_capstone.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapCameraPolicyTest {

    @Test
    fun `route auto fit is allowed before navigation tracking starts`() {
        assertTrue(MapCameraPolicy.shouldFitRoute(routePointCount = 2, trackUser = false))
    }

    @Test
    fun `route auto fit is blocked while navigation camera tracks the user`() {
        assertFalse(MapCameraPolicy.shouldFitRoute(routePointCount = 2, trackUser = true))
    }

    @Test
    fun `place auto fit is blocked while navigation camera tracks the user`() {
        assertFalse(MapCameraPolicy.shouldFitPlaces(placeCount = 3, trackUser = true))
    }

    @Test
    fun `single place camera center is blocked while navigation camera tracks the user`() {
        assertFalse(MapCameraPolicy.shouldCenterSinglePlace(placeCount = 1, trackUser = true))
    }
}
