package com.example.ez_capstone.context

import android.util.Log
import com.example.ez_capstone.agent.models.AgentContext
import com.example.ez_capstone.db.dao.RouteHistoryDao
import com.example.ez_capstone.db.dao.ScheduleDao
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 주변 컨텍스트 엔진.
 * GPS, 시간, 날씨, 캘린더, 이동 패턴 등을 수집하여
 * SystemPrompt와 ProactiveWorker에 제공.
 */
data class ContextSnapshot(
    // 위치
    val locationX: Double?,
    val locationY: Double?,
    val drivingState: String,

    // 시간
    val time: LocalDateTime,
    val dayOfWeek: String,
    val timeOfDay: String,
    val isWeekend: Boolean,
    val isRushHour: Boolean,

    // 일정
    val nextEvent: String?,          // "14:00 팀미팅 (회사)"
    val minutesToNextEvent: Int?,

    // 패턴
    val usualActivity: String?,      // "보통 이 시간에 출근"
    val frequentDestination: String? // "이 시간대에 자주 가는 곳: 회사"
)

@Singleton
class AmbientContextEngine @Inject constructor(
    private val scheduleDao: ScheduleDao,
    private val routeHistoryDao: RouteHistoryDao
) {
    companion object {
        private const val TAG = "AmbientContext"
        private val DAY_NAMES = arrayOf("일", "월", "화", "수", "목", "금", "토")
    }

    /**
     * 현재 상태의 전체 스냅샷 생성.
     */
    suspend fun buildSnapshot(agentContext: AgentContext): ContextSnapshot {
        val now = LocalDateTime.now()
        val hour = now.hour
        val dayOfWeek = DAY_NAMES[now.dayOfWeek.value % 7]
        val isWeekend = now.dayOfWeek in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        val isRushHour = hour in 7..9 || hour in 17..19

        val timeOfDay = classifyTime(hour)

        // 다음 일정
        val nextEvent = getNextEvent()

        // 이동 패턴
        val pattern = detectPattern(now)

        return ContextSnapshot(
            locationX = agentContext.locationX,
            locationY = agentContext.locationY,
            drivingState = agentContext.drivingState,
            time = now,
            dayOfWeek = dayOfWeek,
            timeOfDay = timeOfDay,
            isWeekend = isWeekend,
            isRushHour = isRushHour,
            nextEvent = nextEvent?.first,
            minutesToNextEvent = nextEvent?.second,
            usualActivity = pattern?.first,
            frequentDestination = pattern?.second
        )
    }

    /**
     * SystemPrompt 주입용 컨텍스트 문자열 생성.
     */
    suspend fun toPromptString(agentContext: AgentContext): String {
        val s = buildSnapshot(agentContext)
        val timeStr = s.time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

        return buildString {
            append("위치: ${if (s.locationX != null) "${s.locationX},${s.locationY}" else "없음"}")
            append(" | 시각: $timeStr(${s.dayOfWeek}) ${s.timeOfDay}")
            append(" | 상태: ${s.drivingState}")

            if (s.isRushHour) append(" | 출퇴근 시간대")
            if (s.isWeekend) append(" | 주말")

            s.nextEvent?.let { append(" | 다음 일정: $it (${s.minutesToNextEvent}분 후)") }
            s.usualActivity?.let { append(" | 패턴: $it") }
            s.frequentDestination?.let { append(" | 자주 가는 곳: $it") }
        }
    }

    fun classifyTime(hour: Int): String = when {
        hour < 6 -> "새벽"
        hour < 10 -> "아침출근"
        hour < 12 -> "오전"
        hour < 14 -> "점심"
        hour < 17 -> "오후"
        hour < 20 -> "저녁퇴근"
        else -> "야간"
    }

    // ── 내부 ──

    private suspend fun getNextEvent(): Pair<String, Int>? {
        return try {
            val schedules = scheduleDao.getActive()
            if (schedules.isEmpty()) return null

            val now = LocalDateTime.now()
            val today = now.toLocalDate()

            // ScheduleEntity.time: "HH:mm" 형식, date: "yyyy-MM-dd" 형식
            // 가장 가까운 미래 일정을 찾아 실제 minutesToNext 계산
            val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
            val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

            val nextWithMinutes = schedules
                .mapNotNull { entity ->
                    val timeStr = entity.time ?: return@mapNotNull null
                    val parsedTime = runCatching { LocalTime.parse(timeStr, timeFormatter) }
                        .getOrNull() ?: return@mapNotNull null
                    val parsedDate = entity.date
                        ?.let { runCatching { LocalDate.parse(it, dateFormatter) }.getOrNull() }
                        ?: today
                    val eventDateTime = LocalDateTime.of(parsedDate, parsedTime)
                    if (eventDateTime.isBefore(now)) return@mapNotNull null  // 이미 지난 일정 제외
                    val minutesUntil = java.time.Duration.between(now, eventDateTime).toMinutes()
                        .toInt().coerceAtLeast(0)
                    entity to minutesUntil
                }
                .minByOrNull { it.second }

            nextWithMinutes?.let { (entity, minutes) ->
                val desc = "${entity.title}${entity.destinationName?.let { d -> " ($d)" } ?: ""}"
                desc to minutes
            }
        } catch (e: Exception) {
            Log.e(TAG, "getNextEvent failed: ${e.message}")
            null
        }
    }

    private suspend fun detectPattern(now: LocalDateTime): Pair<String, String?>? {
        return try {
            val dayIdx = now.dayOfWeek.value % 7
            val histories = routeHistoryDao.getByDayOfWeek(dayIdx)
            if (histories.size < 3) return null  // 최소 3회 이상이어야 패턴

            // 가장 빈번한 목적지
            val topDest = histories
                .groupBy { it.destName }
                .maxByOrNull { it.value.size }

            val activity = when {
                now.hour in 7..9 -> "보통 이 시간에 출근"
                now.hour in 17..19 -> "보통 이 시간에 퇴근"
                else -> null
            }

            if (activity != null || topDest != null) {
                activity.orEmpty() to topDest?.key
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "detectPattern failed: ${e.message}")
            null
        }
    }
}
