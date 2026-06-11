package com.example.ez_capstone.navi

import com.example.ez_capstone.server.models.Coord
import com.example.ez_capstone.server.models.Guide
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * RouteProgressTracker 단위 테스트 — spec §7 기준 8개 케이스.
 * 순수 JUnit (기기 불필요, Android SDK 의존 0).
 */
class RouteProgressTrackerTest {

    private lateinit var tracker: RouteProgressTracker

    // 남북 직선 경로: 위도 0.00 → 0.01 → 0.02 (각 세그먼트 ≈ 1111m, 총 ≈ 2222m)
    private val straightCoords = listOf(
        Coord(0.0, 0.0), Coord(0.01, 0.0), Coord(0.02, 0.0)
    )

    private fun fix(lat: Double, lng: Double = 0.0) = RouteFix(lat, lng, 60, 0f)

    @Before fun setUp() { tracker = RouteProgressTracker() }

    // ── 1. 직선 세그먼트 중간 fix → 스냅 정확 ──────────────────────────────────
    @Test fun `mid-segment fix snaps onto route segment`() {
        tracker.init(straightCoords, emptyList())
        // lat=0.005, lng=0.001 → 경로에서 동쪽으로 약 110m 이탈
        val p = tracker.update(fix(0.005, 0.001))!!
        // 스냅된 경도는 경로(lng=0) 위여야 함
        assertEquals("snappedLng should be on route (lng=0)", 0.0, p.snappedLng, 1e-6)
        // 스냅된 위도는 0.005 근처
        assertTrue("snappedLat ≈ 0.005, got ${p.snappedLat}", p.snappedLat in 0.004..0.006)
        // 이탈 거리 > 0
        assertTrue("offRouteM > 0", p.offRouteM > 0.0)
    }

    // ── 2. fix 전진 → distanceAlongM 단조 증가 ────────────────────────────────
    @Test fun `forward fixes produce monotonically increasing distanceAlong`() {
        tracker.init(straightCoords, emptyList())
        val checkpoints = listOf(0.002, 0.005, 0.008, 0.012, 0.018)
        var prev = -1.0
        for (lat in checkpoints) {
            val p = tracker.update(fix(lat))!!
            assertTrue(
                "distanceAlongM must increase: $prev → ${p.distanceAlongM} at lat=$lat",
                p.distanceAlongM > prev
            )
            prev = p.distanceAlongM
        }
    }

    // ── 3. 이탈 fix → offRouteM 큼 ─────────────────────────────────────────────
    @Test fun `off-route fix has large offRouteM`() {
        tracker.init(straightCoords, emptyList())
        // lng=0.009 → 적도 기준 약 1km 이탈
        val p = tracker.update(fix(0.01, 0.009))!!
        assertTrue("offRouteM should be > 500m, got ${p.offRouteM}", p.offRouteM > 500.0)
    }

    // ── 4. distanceRemainingM == totalM − distanceAlongM ──────────────────────
    @Test fun `along plus remaining equals total distance`() {
        tracker.init(straightCoords, emptyList())
        // 두 서로 다른 위치에서 along + remaining이 동일한 totalM 을 가리키는지
        val p1 = tracker.update(fix(0.003))!!
        val p2 = tracker.update(fix(0.012))!!
        val total1 = p1.distanceAlongM + p1.distanceRemainingM
        val total2 = p2.distanceAlongM + p2.distanceRemainingM
        assertEquals("totalM should be consistent across fixes", total1, total2, 5.0) // 5m 허용
        assertTrue("remaining >= 0", p1.distanceRemainingM >= 0.0)
    }

    // ── 5. guide distanceAlong 사전계산 정확 ──────────────────────────────────
    @Test fun `guide distanceAlong precomputed correctly`() {
        // 세그먼트 0 중간(lat=0.005)에 guide 배치 → along ≈ 556m (세그먼트 0 총 ≈ 1111m 의 절반)
        val guides = listOf(Guide(lat = 0.005, lng = 0.0, name = "mid", type = 0, guidance = "", distance = 0))
        tracker.init(straightCoords, guides)
        val along = tracker.guideDistanceAlong(0)
        assertTrue("guide[0] along ≈ 556m, got $along", along in 400.0..700.0)
    }

    // ── 6. 윈도우 전진 + 점프 시 전수 재앵커 ────────────────────────────────────
    @Test fun `GPS jump beyond window triggers full re-anchor`() {
        // 101개 좌표 직선 (각 세그먼트 ≈ 111m)
        val manyCoords = (0..100).map { i -> Coord(i * 0.001, 0.0) }
        tracker.init(manyCoords, emptyList())
        // coord[50] 근처까지 전진
        for (i in 0..50) tracker.update(fix(i * 0.001))
        // coord[5] 근처로 GPS 역방향 점프 (DEVIATION_TOL 기준 훨씬 벗어남)
        val p = tracker.update(fix(0.005))!!
        // 전수 재앵커 후 segmentIndex는 5 근처여야 함 (단조 가드 없이 재앵커)
        assertTrue(
            "after jump, segmentIndex should be near 5, got ${p.segmentIndex}",
            p.segmentIndex in 0..15
        )
    }

    // ── 7. 도착 — 마지막 좌표에서 잔여 ≈ 0 ────────────────────────────────────
    @Test fun `arrival at last coordinate gives near-zero remaining`() {
        tracker.init(straightCoords, emptyList())
        val p = tracker.update(fix(0.02, 0.0))!! // 마지막 좌표 정확히
        assertTrue(
            "remaining should be < 50m at destination, got ${p.distanceRemainingM}",
            p.distanceRemainingM < 50.0
        )
    }

    // ── 8. 단조 가드 — 역행 fix(BACKSTEP_TOL 이내) 에서 distanceAlongM 유지 ──
    @Test fun `backward fix within BACKSTEP_TOL preserves distanceAlong`() {
        tracker.init(straightCoords, emptyList())
        val p1 = tracker.update(fix(0.01))!!   // 세그먼트 1 시작 ≈ 1111m
        // BACKSTEP_TOL(20m) 이내 역행: 위도를 약 10m(≈0.00009°) 후퇴
        val p2 = tracker.update(fix(0.00991))!!
        // distanceAlong이 BACKSTEP_TOL 이상 감소하지 않아야 함
        assertTrue(
            "distanceAlong must not decrease > BACKSTEP_TOL. was ${p1.distanceAlongM}, now ${p2.distanceAlongM}",
            p1.distanceAlongM - p2.distanceAlongM <= RouteProgressTracker.BACKSTEP_TOL
        )
    }
}
