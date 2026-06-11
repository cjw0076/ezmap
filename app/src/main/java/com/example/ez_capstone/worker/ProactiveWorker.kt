package com.example.ez_capstone.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ez_capstone.api.KakaoLocalApi
import com.example.ez_capstone.api.KakaoMobilityApi
import com.example.ez_capstone.config.ApiKeyProvider
import com.example.ez_capstone.db.dao.RouteHistoryDao
import com.example.ez_capstone.db.dao.ScheduleDao
import com.example.ez_capstone.helpers.CalendarHelper
import com.example.ez_capstone.helpers.NotificationHelper
import com.example.ez_capstone.skill.RoutineExecutor
import com.example.ez_capstone.db.dao.ProfileDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDateTime

/**
 * 프로액티브 알림 Worker — 30분 주기.
 * 서버 proactiveEngine.js 포팅.
 *
 * 1. 캘린더 체크: 30~90분 내 이벤트 → ETA 계산 → 알림
 * 2. 패턴 체크: 출퇴근 패턴 매칭 → 알림
 * 3. 스케줄 체크: Room 일정 매칭
 */
@HiltWorker
class ProactiveWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val calendarHelper: CalendarHelper,
    private val scheduleDao: ScheduleDao,
    private val routeHistoryDao: RouteHistoryDao,
    private val notificationHelper: NotificationHelper,
    private val kakaoMobilityApi: KakaoMobilityApi,
    private val kakaoLocalApi: KakaoLocalApi,
    private val apiKeyProvider: ApiKeyProvider,
    private val profileDao: ProfileDao,
    private val routineExecutor: RoutineExecutor
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ProactiveWorker"
        const val WORK_NAME = "proactive_check"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "ProactiveWorker 실행")

        if (!apiKeyProvider.isConfigured) {
            Log.d(TAG, "API 키 미설정 — 스킵")
            return Result.success()
        }

        try {
            checkCalendarEvents()
            checkRoutePatterns()
            checkRoutines()
        } catch (e: Exception) {
            Log.e(TAG, "ProactiveWorker 오류: ${e.message}")
        }

        return Result.success()
    }

    /** 30~90분 내 캘린더 이벤트 체크 */
    private suspend fun checkCalendarEvents() {
        val events = calendarHelper.getUpcomingEvents(withinMinutes = 90)
        val now = System.currentTimeMillis()

        for (event in events) {
            val minutesUntil = (event.startMs - now) / 60_000
            if (minutesUntil < 30 || minutesUntil > 90) continue
            if (event.location.isNullOrBlank()) continue

            try {
                // 장소 → 좌표 변환
                val geo = kakaoLocalApi.geocodePlace(event.location!!)

                // ETA 계산 — 홈 좌표가 저장된 경우 출발지로 사용
                val profile = profileDao.getProfile()
                val etaSuffix = if (profile?.homeLat != null && profile.homeLng != null) {
                    try {
                        val routes = kakaoMobilityApi.getDirections(
                            originLng = profile.homeLng, originLat = profile.homeLat,
                            destLng = geo.lng, destLat = geo.lat
                        )
                        val etaMin = (routes.firstOrNull()?.durationS ?: 0) / 60
                        if (etaMin > 0) " · 예상 소요 ${etaMin}분" else ""
                    } catch (_: Exception) { "" }
                } else ""

                val title = "📅 ${event.title}"
                val body = "${minutesUntil}분 후 시작 · ${event.location}$etaSuffix"

                notificationHelper.postProactive(title, body, mapOf(
                    "event_id" to event.id.toString(),
                    "dest_lat" to geo.lat.toString(),
                    "dest_lng" to geo.lng.toString()
                ))

                Log.d(TAG, "캘린더 알림: ${event.title} (${minutesUntil}분 후)")
            } catch (e: Exception) {
                Log.w(TAG, "캘린더 이벤트 처리 실패: ${event.title} - ${e.message}")
            }
        }
    }

    /** 출퇴근 패턴 체크 */
    private suspend fun checkRoutePatterns() {
        val now = LocalDateTime.now()
        val dayOfWeek = now.dayOfWeek.value  // 1(Mon) ~ 7(Sun)
        val hour = now.hour

        // 출근 시간대 (월~금, 6~10시)
        if (dayOfWeek in 1..5 && hour in 6..9) {
            val morningRoutes = routeHistoryDao.getByDayOfWeek(dayOfWeek)
                .filter { it.departedAt > 0 }
                .take(3)

            if (morningRoutes.isNotEmpty()) {
                val frequent = morningRoutes.first()
                notificationHelper.postProactive(
                    "🚗 출근 시간이에요",
                    "${frequent.destName ?: "회사"}까지 출발 준비하세요"
                )
            }
        }

        // 퇴근 시간대 (월~금, 17~20시)
        if (dayOfWeek in 1..5 && hour in 17..19) {
            val eveningRoutes = routeHistoryDao.getByDayOfWeek(dayOfWeek)
                .filter { it.departedAt > 0 }
                .take(3)

            if (eveningRoutes.size >= 2) {
                notificationHelper.postProactive(
                    "🏠 퇴근 시간이에요",
                    "집까지 경로를 확인해보세요"
                )
            }
        }
    }

    /** 커스텀 루틴 체크 */
    private suspend fun checkRoutines() {
        val now = LocalDateTime.now()
        val dow = now.dayOfWeek.value % 7  // 0=일 ~ 6=토
        val minute = now.hour * 60 + now.minute

        val response = routineExecutor.checkAndExecute(dow, minute)
        if (response != null) {
            notificationHelper.postProactive(
                "🔄 루틴 실행",
                response.replyText.take(80)
            )
            Log.d(TAG, "루틴 실행 완료: ${response.replyText.take(50)}")
        }
    }
}
