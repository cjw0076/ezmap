package com.example.ez_capstone.navi

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Method

/**
 * RestGuideEngine 순수 로직 테스트.
 * haversine()은 private이지만 reflection으로 접근.
 * getDrivingSummary()는 public — 초기 상태에서 0 반환 검증.
 */
class RestGuideEngineTest {

    private val mockContext: Context = mockk(relaxed = true)
    private val engine = RestGuideEngine(mockContext)

    private fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val method: Method = RestGuideEngine::class.java.getDeclaredMethod(
            "haversine",
            Double::class.java, Double::class.java, Double::class.java, Double::class.java
        )
        method.isAccessible = true
        return method.invoke(engine, lat1, lng1, lat2, lng2) as Double
    }

    @Test
    fun `haversine 같은 좌표는 0m`() {
        val dist = haversine(37.5, 127.0, 37.5, 127.0)
        assertEquals(0.0, dist, 0.001)
    }

    @Test
    fun `haversine 강남역에서 서울시청 약 8km`() {
        // 강남역 37.4979, 127.0276 → 서울시청 37.5663, 126.9779
        val dist = haversine(37.4979, 127.0276, 37.5663, 126.9779)
        // 실제 직선거리 약 8.4km, 허용 오차 ±500m
        assertTrue("예상 범위 7.9~9km: $dist", dist in 7900.0..9000.0)
    }

    @Test
    fun `haversine 대칭성 — A→B == B→A`() {
        val ab = haversine(37.4979, 127.0276, 37.5663, 126.9779)
        val ba = haversine(37.5663, 126.9779, 37.4979, 127.0276)
        assertEquals(ab, ba, 0.001)
    }

    @Test
    fun `getDrivingSummary 초기 상태 — 크래시 없이 반환`() {
        val summary = engine.getDrivingSummary()
        // start 전이라 totalDistanceTraveled == 0
        assertEquals(0, summary.totalDistance)
        assertTrue("경과 시간 >= 1초", summary.totalTime >= 1)
    }

    // ── ⑤ computeRemainTimeSec: Kakao duration 기반 잔여 ETA (companion 순수 함수) ──

    @Test
    fun `computeRemainTimeSec 폴백 — 총량 없으면 거리÷속도 (60kmh 7200m=432초)`() {
        val s = RestGuideEngine.computeRemainTimeSec(7200.0, routeDurationS = 0, routeDistanceM = 0, speedKmh = 60)
        assertEquals(432, s)
    }

    @Test
    fun `computeRemainTimeSec 폴백 — 속도 0은 30kmh (900m=108초)`() {
        val s = RestGuideEngine.computeRemainTimeSec(900.0, routeDurationS = 0, routeDistanceM = 0, speedKmh = 0)
        assertEquals(108, s)
    }

    @Test
    fun `computeRemainTimeSec Kakao 총시간을 잔여거리 비율로 스케일 (절반=절반)`() {
        // 총 11800m/1200초 경로의 절반(5900m) 남으면 600초
        val s = RestGuideEngine.computeRemainTimeSec(5900.0, routeDurationS = 1200, routeDistanceM = 11800, speedKmh = 0)
        assertEquals(600, s)
    }

    @Test
    fun `computeRemainTimeSec 가까우면 짧은 ETA — 855m 잔여가 4시간으로 안 뜸`() {
        // 정합성 회귀: 855m 잔여인데 4h45m 뜨던 류 방지
        val s = RestGuideEngine.computeRemainTimeSec(855.0, routeDurationS = 600, routeDistanceM = 11800, speedKmh = 0)
        assertTrue("짧아야 함(<120초): $s", s < 120)
    }

    @Test
    fun `computeRemainTimeSec 도착 시 0초`() {
        val s = RestGuideEngine.computeRemainTimeSec(0.0, routeDurationS = 1200, routeDistanceM = 11800, speedKmh = 0)
        assertEquals(0, s)
    }
}
