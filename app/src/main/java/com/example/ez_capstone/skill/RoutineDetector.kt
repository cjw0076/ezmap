package com.example.ez_capstone.skill

import android.util.Log
import com.example.ez_capstone.db.dao.ConversationDao
import com.example.ez_capstone.db.dao.RouteHistoryDao
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 사용자 반복 패턴 감지.
 * 최근 30일 경로 히스토리에서 요일/시간대별 반복을 찾아
 * 루틴 후보를 제안한다.
 */
data class RoutineCandidate(
    val name: String,                // 제안 이름: "금요 장보기"
    val dayOfWeek: Int,              // 0=일 ~ 6=토
    val timeStartMin: Int,           // 시작 시간 (분)
    val timeEndMin: Int,             // 종료 시간 (분)
    val destination: String,         // 목적지 이름
    val frequency: Int,              // 감지 횟수
    val confidence: Float            // 신뢰도 0~1
)

@Singleton
class RoutineDetector @Inject constructor(
    private val routeHistoryDao: RouteHistoryDao,
    private val conversationDao: ConversationDao,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "RoutineDetector"
        private const val MIN_FREQUENCY = 3  // 최소 3회 이상 반복이어야 패턴
    }

    /**
     * 경로 히스토리에서 반복 패턴을 감지.
     * @return 루틴 후보 리스트 (confidence 내림차순)
     */
    suspend fun detectPatterns(): List<RoutineCandidate> {
        val candidates = mutableListOf<RoutineCandidate>()

        try {
            // 요일별로 분석 (0=일 ~ 6=토)
            for (dow in 0..6) {
                val histories = routeHistoryDao.getByDayOfWeek(dow)
                if (histories.size < MIN_FREQUENCY) continue

                // 목적지별 그룹화
                val byDest = histories.groupBy { it.destName ?: "unknown" }

                for ((dest, routes) in byDest) {
                    if (dest == "unknown" || routes.size < MIN_FREQUENCY) continue

                    // 출발 시간 분석 (시간대 클러스터링)
                    val departureMins = routes.mapNotNull { route ->
                        val cal = java.util.Calendar.getInstance().apply {
                            timeInMillis = route.departedAt
                        }
                        cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
                    }

                    if (departureMins.isEmpty()) continue

                    // 평균 출발 시간 ± 1시간 윈도우
                    val avgMin = departureMins.average().toInt()
                    val windowStart = (avgMin - 60).coerceAtLeast(0)
                    val windowEnd = (avgMin + 60).coerceAtMost(1440)

                    // 윈도우 내 경로 수
                    val inWindow = departureMins.count { it in windowStart..windowEnd }
                    if (inWindow < MIN_FREQUENCY) continue

                    val confidence = (inWindow.toFloat() / histories.size).coerceAtMost(1f)
                    val dayName = arrayOf("일", "월", "화", "수", "목", "금", "토")[dow]
                    val timeStr = "${avgMin / 60}시"

                    candidates.add(RoutineCandidate(
                        name = "${dayName}요일 $timeStr $dest",
                        dayOfWeek = dow,
                        timeStartMin = windowStart,
                        timeEndMin = windowEnd,
                        destination = dest,
                        frequency = inWindow,
                        confidence = confidence
                    ))
                }
            }

            Log.d(TAG, "Detected ${candidates.size} routine candidates")
        } catch (e: Exception) {
            Log.e(TAG, "Pattern detection failed: ${e.message}")
        }

        return candidates.sortedByDescending { it.confidence }
    }

    /**
     * RoutineCandidate를 CustomRoutineEntity로 변환.
     */
    fun candidateToEntity(candidate: RoutineCandidate): CustomRoutineEntity {
        val steps = listOf(
            mapOf("tool" to "get_directions", "args" to mapOf("destination" to candidate.destination))
        )
        return CustomRoutineEntity(
            name = candidate.name,
            triggerDayOfWeek = candidate.dayOfWeek,
            triggerTimeStart = candidate.timeStartMin,
            triggerTimeEnd = candidate.timeEndMin,
            stepsJson = gson.toJson(steps)
        )
    }
}
