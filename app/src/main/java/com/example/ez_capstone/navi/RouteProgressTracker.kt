package com.example.ez_capstone.navi

import com.example.ez_capstone.server.models.Coord
import com.example.ez_capstone.server.models.Guide
import kotlin.math.*

data class RouteFix(
    val lat: Double,
    val lng: Double,
    val speedKmh: Int,
    val rawBearing: Float
)

data class RouteProgress(
    val snappedLat: Double,
    val snappedLng: Double,
    val distanceAlongM: Double,
    val distanceRemainingM: Double,
    val segmentIndex: Int,
    val offRouteM: Double,
    val bearingAlongRoute: Float
)

/**
 * 순수 Kotlin 세그먼트 투영 맵매칭 — Android/SDK 의존 0 → JUnit으로 단위 잠금.
 *
 * GPS fix를 경로 세그먼트에 직교 투영해 진행거리(distance-along) 단일 스칼라를 얻고,
 * 그로부터 잔여거리·이탈·TTS 트리거·카메라 스냅 위치를 일관 파생한다.
 *
 * 사용처: RestGuideEngine이 매 GPS 틱에서 update()를 호출하고 RouteProgress를 소비.
 */
class RouteProgressTracker {

    companion object {
        const val WINDOW = 60          // 전방 탐색 윈도우 (세그먼트 수)
        const val DEVIATION_TOL = 50.0 // m — 이 이상이면 GPS 점프로 간주 → 전수 re-anchor
        const val BACKSTEP_TOL = 20.0  // m — 역행 GPS 노이즈 허용 마진
        private const val EARTH_R = 6_371_000.0
    }

    private var coords: List<Coord> = emptyList()
    // cumDist[i] = 출발(coord[0])부터 coord[i]까지 경로 누적 거리(m)
    private var cumDist: DoubleArray = DoubleArray(0)
    private var totalM = 0.0
    // 각 guide의 distance-along (init 시 사전 투영)
    private var guideAlongM: DoubleArray = DoubleArray(0)

    private var progressIdx = 0   // 마지막으로 확정된 세그먼트 인덱스(단조 전진)
    private var lastAlongM = 0.0  // 단조 가드용 마지막 distance-along

    // ── public API ──────────────────────────────────────────────────────────

    /**
     * 경로 초기화. start() 및 updateRoute() 시 1회 호출.
     * cumDist와 guideAlongM 사전 계산, 상태 리셋.
     */
    fun init(coords: List<Coord>, guides: List<Guide>) {
        this.coords = coords
        cumDist = buildCumDist(coords)
        totalM = if (cumDist.isNotEmpty()) cumDist.last() else 0.0
        guideAlongM = DoubleArray(guides.size) { g -> projectGuide(guides[g]) }
        progressIdx = 0
        lastAlongM = 0.0
    }

    /** guide[index]의 distance-along(m). init 후 유효. 범위 밖이면 0 반환. */
    fun guideDistanceAlong(index: Int): Double = guideAlongM.getOrElse(index) { 0.0 }

    /**
     * GPS fix를 경로에 투영해 RouteProgress 반환.
     * coords < 2면 null (경로 없음 → 엔진이 원시 GPS로 폴백).
     */
    fun update(fix: RouteFix): RouteProgress? {
        if (coords.size < 2) return null

        val lo = (progressIdx - 2).coerceAtLeast(0)
        val hi = (progressIdx + WINDOW).coerceAtMost(coords.size - 2)

        var bestSeg = lo
        var bestT = 0.0
        var bestSnappedLat = coords[lo].lat
        var bestSnappedLng = coords[lo].lng
        var bestOff = Double.MAX_VALUE

        for (seg in lo..hi) {
            val (t, sLat, sLng, off) = projectOnSegment(fix.lat, fix.lng, coords[seg], coords[seg + 1])
            if (off < bestOff) {
                bestOff = off; bestSeg = seg; bestT = t
                bestSnappedLat = sLat; bestSnappedLng = sLng
            }
        }

        // 윈도우 내 최솟값 > DEVIATION_TOL → GPS 점프 의심 → 전수 재앵커(드묾)
        if (bestOff > DEVIATION_TOL) {
            for (seg in 0 until coords.size - 1) {
                val (t, sLat, sLng, off) = projectOnSegment(fix.lat, fix.lng, coords[seg], coords[seg + 1])
                if (off < bestOff) {
                    bestOff = off; bestSeg = seg; bestT = t
                    bestSnappedLat = sLat; bestSnappedLng = sLng
                }
            }
        }

        var along = cumDist[bestSeg] + bestT * segLen(bestSeg)

        // 단조 가드: BACKSTEP_TOL 이상 역행이면 직전 값 유지 (GPS 노이즈 역행 방지)
        if (along < lastAlongM - BACKSTEP_TOL) along = lastAlongM
        lastAlongM = along
        progressIdx = bestSeg

        val remaining = (totalM - along).coerceAtLeast(0.0)

        return RouteProgress(
            snappedLat = bestSnappedLat,
            snappedLng = bestSnappedLng,
            distanceAlongM = along,
            distanceRemainingM = remaining,
            segmentIndex = bestSeg,
            offRouteM = bestOff,
            bearingAlongRoute = segBearing(bestSeg)
        )
    }

    // ── 내부 계산 ────────────────────────────────────────────────────────────

    private fun buildCumDist(coords: List<Coord>): DoubleArray {
        if (coords.isEmpty()) return DoubleArray(0)
        val arr = DoubleArray(coords.size)
        for (i in 1 until coords.size) {
            arr[i] = arr[i - 1] + haversine(
                coords[i - 1].lat, coords[i - 1].lng,
                coords[i].lat, coords[i].lng
            )
        }
        return arr
    }

    /** guide를 경로 세그먼트에 투영해 distance-along 반환. */
    private fun projectGuide(guide: Guide): Double {
        if (coords.size < 2) return 0.0
        var bestSeg = 0; var bestT = 0.0; var bestOff = Double.MAX_VALUE
        for (seg in 0 until coords.size - 1) {
            val (t, _, _, off) = projectOnSegment(guide.lat, guide.lng, coords[seg], coords[seg + 1])
            if (off < bestOff) { bestOff = off; bestSeg = seg; bestT = t }
        }
        return cumDist[bestSeg] + bestT * segLen(bestSeg)
    }

    /**
     * 점 P를 세그먼트 AB에 직교 투영.
     * @return (t, snappedLat, snappedLng, offRouteM)  t∈[0,1]
     */
    private fun projectOnSegment(
        pLat: Double, pLng: Double, a: Coord, b: Coord
    ): Quad {
        val abLat = b.lat - a.lat; val abLng = b.lng - a.lng
        val apLat = pLat - a.lat; val apLng = pLng - a.lng
        val ab2 = abLat * abLat + abLng * abLng
        val t = if (ab2 == 0.0) 0.0 else ((apLat * abLat + apLng * abLng) / ab2).coerceIn(0.0, 1.0)
        val sLat = a.lat + t * abLat
        val sLng = a.lng + t * abLng
        return Quad(t, sLat, sLng, haversine(pLat, pLng, sLat, sLng))
    }

    private fun segLen(seg: Int): Double =
        haversine(coords[seg].lat, coords[seg].lng, coords[seg + 1].lat, coords[seg + 1].lng)

    /** 세그먼트 AB의 진행 방위(0–360°). */
    private fun segBearing(seg: Int): Float {
        val a = coords[seg]
        val b = coords.getOrNull(seg + 1) ?: return 0f
        val dLng = Math.toRadians(b.lng - a.lng)
        val lat1 = Math.toRadians(a.lat); val lat2 = Math.toRadians(b.lat)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
    }

    private fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return EARTH_R * 2 * asin(sqrt(a))
    }

    private data class Quad(val t: Double, val lat: Double, val lng: Double, val off: Double)
}
