package com.example.ez_capstone.skill

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.ez_capstone.config.ApiKeyProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Skill 생명주기 관리.
 * 매 호출 후 성공/실패/지연 기록, 연속 실패 시 자동 비활성화,
 * 키 만료 감지, 건강 점수 계산.
 */
enum class SkillStatus {
    ACTIVE,          // 정상 동작
    DEGRADED,        // 성공률 낮음 (최근 실패 있음)
    FAILING,         // 연속 3회+ 실패
    AUTO_DISABLED,   // 연속 5회 실패로 자동 비활성화
    KEY_MISSING,     // API 키 미등록
}

data class SkillHealth(
    val skillName: String,
    val status: SkillStatus,
    val successRate: Float,      // 0~1 (최근 20회 기준)
    val consecutiveFailures: Int,
    val totalCalls: Int,
    val avgLatencyMs: Long,
    val lastError: String? = null
)

@Singleton
class SkillLifecycleManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiKeyProvider: ApiKeyProvider
) {
    companion object {
        private const val TAG = "SkillLifecycle"
        private const val PREFS_NAME = "ezmap_skill_health"
        private const val MAX_HISTORY = 20
        private const val AUTO_DISABLE_THRESHOLD = 5
        // half-open: 자동 비활성화 후 이 시간이 지나면 1회 재시도 허용(자가 회복) → 영구 차단 방지
        private const val RECOVERY_COOLDOWN_MS = 10 * 60 * 1000L
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Skill 호출 결과 기록.
     * @return 자동 비활성화되면 true
     */
    fun recordExecution(skillName: String, success: Boolean, latencyMs: Long, error: String? = null): Boolean {
        val key = "skill_$skillName"
        val existing = prefs.getString(key, null)
        val data = if (existing != null) JSONObject(existing) else JSONObject()

        val totalCalls = data.optInt("totalCalls", 0) + 1
        val totalSuccess = data.optInt("totalSuccess", 0) + if (success) 1 else 0
        val consecutiveFailures = if (success) 0 else data.optInt("consecutiveFailures", 0) + 1
        val totalLatency = data.optLong("totalLatency", 0L) + latencyMs

        data.put("totalCalls", totalCalls)
        data.put("totalSuccess", totalSuccess)
        data.put("consecutiveFailures", consecutiveFailures)
        data.put("totalLatency", totalLatency)
        data.put("lastCallAt", System.currentTimeMillis())
        if (error != null) data.put("lastError", error)

        // 상태 결정
        val status = when {
            consecutiveFailures >= AUTO_DISABLE_THRESHOLD -> SkillStatus.AUTO_DISABLED
            consecutiveFailures >= 3 -> SkillStatus.FAILING
            consecutiveFailures >= 1 -> SkillStatus.DEGRADED
            else -> SkillStatus.ACTIVE
        }
        data.put("status", status.name)

        prefs.edit().putString(key, data.toString()).apply()

        if (status == SkillStatus.AUTO_DISABLED) {
            Log.w(TAG, "AUTO_DISABLED: $skillName (연속 ${consecutiveFailures}회 실패)")
            return true
        }

        return false
    }

    /**
     * 특정 Skill의 건강 상태 조회.
     */
    fun getHealth(skillName: String): SkillHealth {
        val key = "skill_$skillName"
        val existing = prefs.getString(key, null)

        if (existing == null) {
            return SkillHealth(
                skillName = skillName,
                status = SkillStatus.ACTIVE,
                successRate = 1f,
                consecutiveFailures = 0,
                totalCalls = 0,
                avgLatencyMs = 0
            )
        }

        val data = JSONObject(existing)
        val totalCalls = data.optInt("totalCalls", 0)
        val totalSuccess = data.optInt("totalSuccess", 0)
        val totalLatency = data.optLong("totalLatency", 0L)
        val consecutiveFailures = data.optInt("consecutiveFailures", 0)
        val statusStr = data.optString("status", "ACTIVE")

        return SkillHealth(
            skillName = skillName,
            status = try { SkillStatus.valueOf(statusStr) } catch (_: Exception) { SkillStatus.ACTIVE },
            successRate = if (totalCalls > 0) totalSuccess.toFloat() / totalCalls else 1f,
            consecutiveFailures = consecutiveFailures,
            totalCalls = totalCalls,
            avgLatencyMs = if (totalCalls > 0) totalLatency / totalCalls else 0,
            lastError = data.optString("lastError", null)
        )
    }

    /**
     * 모든 등록된 Skill의 건강 대시보드.
     */
    fun getAllHealth(): List<SkillHealth> {
        val allSkills = listOf(
            "search_places", "geocode", "get_directions", "get_directions_naver",
            "get_transit_route", "get_weather", "get_weather_kma", "get_air_quality",
            "get_gas_stations", "get_ev_chargers", "get_parking", "get_realtime_parking",
            "search_knowledge", "get_exchange_rate", "search_pharmacies", "search_hospitals",
            "get_traffic_speed", "get_traffic_incidents", "get_highway_alerts", "get_road_risk"
        )

        return allSkills.map { getHealth(it) }
    }

    /**
     * 자동 비활성화된 Skill 복구.
     */
    fun resetHealth(skillName: String) {
        prefs.edit().remove("skill_$skillName").apply()
        Log.d(TAG, "Health reset: $skillName")
    }

    /** 모든 도구 건강 상태 초기화(디버그/복구용). */
    fun resetAll() {
        prefs.edit().clear().apply()
        Log.d(TAG, "All skill health reset")
    }

    /**
     * Skill이 현재 사용 가능한지 확인 (auto-disabled가 아닌지).
     */
    fun isAvailable(skillName: String): Boolean {
        val health = getHealth(skillName)
        if (health.status != SkillStatus.AUTO_DISABLED) return true
        // half-open: 쿨다운 경과 시 1회 재시도 허용 → 성공하면 recordExecution이 ACTIVE로 복구.
        val data = prefs.getString("skill_$skillName", null)?.let { JSONObject(it) } ?: return true
        val sinceLast = System.currentTimeMillis() - data.optLong("lastCallAt", 0L)
        return sinceLast > RECOVERY_COOLDOWN_MS
    }

    /**
     * API 키 건강 체크 — 등록된 키가 있는데 최근 호출이 모두 실패인 skill 감지.
     * WorkManager에서 일 1회 호출.
     * @return 키 문제가 있는 skill 목록
     */
    fun checkKeyHealth(): List<SkillHealth> {
        return getAllHealth().filter { health ->
            // 총 호출이 5회 이상이고 최근 성공률이 0%인 경우 키 문제 의심
            health.totalCalls >= 5 && health.successRate == 0f
        }.also { problems ->
            problems.forEach { skill ->
                val key = "skill_${skill.skillName}"
                val existing = prefs.getString(key, null) ?: return@forEach
                val data = JSONObject(existing)
                data.put("status", SkillStatus.KEY_MISSING.name)
                prefs.edit().putString(key, data.toString()).apply()
                Log.w(TAG, "Key health issue: ${skill.skillName} (0% success over ${skill.totalCalls} calls)")
            }
        }
    }
}
