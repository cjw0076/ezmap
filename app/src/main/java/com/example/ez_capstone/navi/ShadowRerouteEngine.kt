package com.example.ez_capstone.navi

import android.util.Log
import com.example.ez_capstone.api.KakaoMobilityApi
import com.example.ez_capstone.models.Coord
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * Shadow Reroute — 주행 중 5분마다 백그라운드 경로 재탐색.
 * 3분 이상 절약 가능한 경로 발견 시 콜백.
 */
@Singleton
class ShadowRerouteEngine @Inject constructor(
    private val kakaoMobilityApi: KakaoMobilityApi
) {
    companion object {
        private const val TAG = "ShadowReroute"
        private const val CHECK_INTERVAL_MS = 10 * 60 * 1000L  // 10분 (Kakao API 절약)
        private const val MIN_SAVINGS_SECONDS = 180  // 3분
    }

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** voice 처리 중 Kakao API 호출을 건너뛰기 위한 플래그 */
    @Volatile var isPaused = false

    data class BetterRoute(
        val coords: List<Coord>,
        val distanceM: Int,
        val durationS: Int,
        val savingsSeconds: Int
    )

    /**
     * 모니터링 시작.
     * @param destLat 목적지 위도
     * @param destLng 목적지 경도
     * @param currentDurationS 현재 경로 잔여 소요시간(초)
     * @param getCurrentLocation 현재 위치 제공 콜백
     * @param onBetterRoute 더 빠른 경로 발견 시 콜백
     */
    fun startMonitoring(
        destLat: Double,
        destLng: Double,
        currentDurationS: Int,
        getCurrentLocation: () -> Pair<Double, Double>?,  // lat, lng
        onBetterRoute: (BetterRoute) -> Unit
    ) {
        stop()
        var remainingDuration = currentDurationS

        job = scope.launch {
            while (isActive) {
                delay(CHECK_INTERVAL_MS)

                if (isPaused) continue

                val (lat, lng) = getCurrentLocation() ?: continue

                try {
                    val routes = kakaoMobilityApi.getDirections(
                        originLng = lng, originLat = lat,
                        destLng = destLng, destLat = destLat,
                        priority = "TIME"
                    )

                    val best = routes.minByOrNull { it.durationS } ?: continue
                    val savings = remainingDuration - best.durationS

                    if (savings >= MIN_SAVINGS_SECONDS) {
                        val coords = best.coords.map { Coord(it.lat, it.lng) }
                        withContext(Dispatchers.Main) {
                            onBetterRoute(BetterRoute(
                                coords = coords,
                                distanceM = best.distanceM,
                                durationS = best.durationS,
                                savingsSeconds = savings
                            ))
                        }
                    }

                    // 잔여 시간 업데이트: 감산량을 실제 체크 인터벌과 일치(과대평가→거짓 "더 빠른 길" 방지).
                    remainingDuration = (remainingDuration - (CHECK_INTERVAL_MS / 1000).toInt()).coerceAtLeast(0)

                    Log.d(TAG, "Shadow check: best=${best.durationS}s, remaining=${remainingDuration}s, savings=${savings}s")
                } catch (e: Exception) {
                    Log.e(TAG, "Shadow reroute error: ${e.message}")
                }
            }
        }
        Log.d(TAG, "Monitoring started")
    }

    fun stop() {
        job?.cancel()
        job = null
        Log.d(TAG, "Monitoring stopped")
    }
}
