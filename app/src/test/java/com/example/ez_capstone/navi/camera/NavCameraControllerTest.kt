package com.example.ez_capstone.navi.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NavCameraController 동적 카메라 정책 회귀 테스트.
 * 멀미/펌핑/마커이탈을 막는 불변식(단조성·우선순위·클램프·freeze·deadzone)을 잠근다.
 */
class NavCameraControllerTest {

    // ── 속도 → 줌 ──

    @Test
    fun speedToZoom_정지는_최대줌_고속은_최소줌() {
        assertEquals(NavCameraController.ZOOM_MAX, NavCameraController.speedToZoom(0f), 1e-3f)
        assertEquals(NavCameraController.ZOOM_MIN, NavCameraController.speedToZoom(100f), 1e-3f)
        assertEquals(NavCameraController.ZOOM_MIN, NavCameraController.speedToZoom(140f), 1e-3f) // cap
    }

    @Test
    fun speedToZoom_속도오를수록_단조감소() {
        var prev = NavCameraController.speedToZoom(0f)
        for (kmh in intArrayOf(20, 40, 60, 80, 100)) {
            val z = NavCameraController.speedToZoom(kmh.toFloat())
            assertTrue("속도 $kmh 에서 줌이 안 줄어듦: $z >= $prev", z <= prev)
            prev = z
        }
    }

    // ── 다음 턴 줌인 ──

    @Test
    fun maneuverZoom_범위밖이면_null() {
        assertNull(NavCameraController.maneuverZoom(null))
        assertNull(NavCameraController.maneuverZoom(301))
    }

    @Test
    fun maneuverZoom_가까울수록_줌인() {
        val far = NavCameraController.maneuverZoom(300)!!
        val near = NavCameraController.maneuverZoom(50)!!
        assertTrue("코앞이 더 줌인이어야: near=$near far=$far", near > far)
        assertTrue(near <= NavCameraController.MANEUVER_PEAK_ZOOM + 1e-3)
    }

    // ── compute: 우선순위/클램프/freeze/패닝 ──

    @Test
    fun compute_maneuver와_speed는_max우선_합산아님() {
        // 고속(speedZoom 낮음) + 임박 턴(turnZoom 높음) → 더 줌인(max) 선택
        val input = NavCameraInput(speedKmh = 90f, bearingDeg = 0f, distToTurnM = 40)
        val prev = NavCameraTarget(zoom = 18f, tiltDeg = 45.0, bearingDeg = 0f)
        val out = NavCameraController.compute(input, prev)
        val speedZoom = NavCameraController.speedToZoom(90f)
        assertTrue("max(speed,turn) 우선이어야: ${out.zoom} > $speedZoom", out.zoom > speedZoom)
    }

    @Test
    fun compute_줌_변화율_클램프() {
        // 직전 18 → 목표(고속) 약 14.5, 한 스텝에 MAX_ZOOM_DELTA 이상 못 변함
        val input = NavCameraInput(speedKmh = 100f, bearingDeg = 0f)
        val prev = NavCameraTarget(zoom = 18f, tiltDeg = 45.0, bearingDeg = 0f)
        val out = NavCameraController.compute(input, prev)
        assertEquals(18f - NavCameraController.MAX_ZOOM_DELTA, out.zoom, 1e-3f)
    }

    @Test
    fun compute_저속이면_bearing_동결() {
        val input = NavCameraInput(speedKmh = 2f, bearingDeg = 270f) // 노이즈성 bearing
        val prev = NavCameraTarget(zoom = 18f, tiltDeg = 30.0, bearingDeg = 90f)
        val out = NavCameraController.compute(input, prev)
        assertEquals("저속 bearing freeze", 90f, out.bearingDeg, 1e-3f)
    }

    @Test
    fun compute_주행중이면_bearing_반영() {
        val input = NavCameraInput(speedKmh = 50f, bearingDeg = 270f)
        val prev = NavCameraTarget(zoom = 16f, tiltDeg = 45.0, bearingDeg = 90f)
        val out = NavCameraController.compute(input, prev)
        assertEquals(270f, out.bearingDeg, 1e-3f)
    }

    @Test
    fun compute_패닝중이면_직전유지() {
        val input = NavCameraInput(speedKmh = 80f, bearingDeg = 180f, isPanning = true)
        val prev = NavCameraTarget(zoom = 17f, tiltDeg = 40.0, bearingDeg = 10f)
        val out = NavCameraController.compute(input, prev)
        assertEquals(prev, out)
    }

    @Test
    fun compute_첫프레임은_클램프없이_목표값() {
        val input = NavCameraInput(speedKmh = 0f, bearingDeg = 45f)
        val out = NavCameraController.compute(input, null)
        assertEquals(NavCameraController.ZOOM_MAX, out.zoom, 1e-3f)
        assertEquals(45f, out.bearingDeg, 1e-3f)
    }

    // ── 데드존 ──

    @Test
    fun shouldApply_미세변화는_스킵_큰변화는_적용() {
        val base = NavCameraTarget(zoom = 16f, tiltDeg = 45.0, bearingDeg = 100f)
        val tiny = NavCameraTarget(zoom = 16.01f, tiltDeg = 45.1, bearingDeg = 100.5f)
        val big = NavCameraTarget(zoom = 16.5f, tiltDeg = 45.0, bearingDeg = 100f)
        assertTrue("미세변화는 스킵", !NavCameraController.shouldApply(base, tiny))
        assertTrue("큰 줌변화는 적용", NavCameraController.shouldApply(base, big))
        assertTrue("첫 프레임은 항상 적용", NavCameraController.shouldApply(null, base))
    }

    @Test
    fun angleDiffDeg_경계처리() {
        assertEquals(20f, NavCameraController.angleDiffDeg(350f, 10f), 1e-3f) // 350↔10 = 20
        assertEquals(0f, NavCameraController.angleDiffDeg(180f, 180f), 1e-3f)
    }
}
