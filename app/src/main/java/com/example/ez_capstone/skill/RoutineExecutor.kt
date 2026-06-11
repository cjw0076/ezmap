package com.example.ez_capstone.skill

import android.util.Log
import com.example.ez_capstone.agent.GeminiAgentEngine
import com.example.ez_capstone.agent.models.AgentContext
import com.example.ez_capstone.agent.models.AgentResponse
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 커스텀 루틴 실행기.
 * ProactiveWorker에서 호출되어 현재 요일/시간에 매칭되는 루틴을 실행.
 */
@Singleton
class RoutineExecutor @Inject constructor(
    private val routineDao: CustomRoutineDao,
    private val agentEngine: GeminiAgentEngine
) {
    companion object {
        private const val TAG = "RoutineExecutor"
        private const val DAY_MS = 86_400_000L
    }

    /**
     * 현재 요일/시간에 매칭되는 활성 루틴을 찾아 실행.
     * @return 실행된 루틴의 AgentResponse, 없으면 null
     */
    suspend fun checkAndExecute(currentDow: Int, currentMinute: Int): AgentResponse? {
        try {
            val routines = routineDao.getActive()
            if (routines.isEmpty()) return null

            val todayStart = System.currentTimeMillis() - (System.currentTimeMillis() % DAY_MS)

            for (routine in routines) {
                // 요일 매칭 (null이면 매일)
                if (routine.triggerDayOfWeek != null && routine.triggerDayOfWeek != currentDow) continue

                // 시간 매칭
                val start = routine.triggerTimeStart ?: 0
                val end = routine.triggerTimeEnd ?: 1440
                if (currentMinute !in start..end) continue

                // 오늘 이미 실행했으면 건너뛰기
                if ((routine.lastTriggeredAt ?: 0) > todayStart) continue

                Log.d(TAG, "Triggering routine: ${routine.name}")
                routineDao.markTriggered(routine.id)

                // 루틴 이름을 자연어로 에이전트에게 전달
                return agentEngine.chat(
                    "${routine.name} 실행해줘",
                    AgentContext()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Routine execution failed: ${e.message}")
        }
        return null
    }
}
