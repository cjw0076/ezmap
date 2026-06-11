package com.example.ez_capstone.governance

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 에이전트 능력별 사용자 권한 관리.
 * "편리하다"와 "소름 끼친다"의 차이 = 사용자가 허락했는가.
 */
enum class AgentCapability {
    LOCATION_ACCESS,        // GPS 위치 읽기
    CALENDAR_ACCESS,        // 캘린더 일정 읽기
    PROACTIVE_NOTIFY,       // 먼저 알림 보내기
    PATTERN_LEARNING,       // 행동 패턴 학습
    MEMORY_PERSIST,         // 선호도/대화 장기 저장
    SKILL_AUTO_CHAIN,       // Skill 자동 체이닝 (확인 없이)
}

enum class PermissionLevel {
    ALWAYS,             // 항상 허용
    ASK_EVERY_TIME,     // 매번 물어보기
    NEVER               // 차단
}

sealed class PermissionResult {
    data object Granted : PermissionResult()
    data object Denied : PermissionResult()
    data class NeedConfirmation(val capability: AgentCapability, val message: String) : PermissionResult()
}

@Singleton
class PermissionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "PermissionManager"
        private const val PREFS_NAME = "ezmap_agent_permissions"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 기본 권한: 위치/캘린더는 항상 허용, 프로액티브/학습은 매번 물어보기 */
    private val defaults = mapOf(
        AgentCapability.LOCATION_ACCESS to PermissionLevel.ALWAYS,
        AgentCapability.CALENDAR_ACCESS to PermissionLevel.ALWAYS,
        AgentCapability.PROACTIVE_NOTIFY to PermissionLevel.ALWAYS,
        AgentCapability.PATTERN_LEARNING to PermissionLevel.ALWAYS,
        AgentCapability.MEMORY_PERSIST to PermissionLevel.ALWAYS,
        AgentCapability.SKILL_AUTO_CHAIN to PermissionLevel.ALWAYS,
    )

    /**
     * 에이전트가 특정 능력을 사용하기 전에 호출.
     */
    fun check(capability: AgentCapability): PermissionResult {
        val level = getLevel(capability)
        return when (level) {
            PermissionLevel.ALWAYS -> PermissionResult.Granted
            PermissionLevel.NEVER -> {
                Log.d(TAG, "Permission DENIED: $capability")
                PermissionResult.Denied
            }
            PermissionLevel.ASK_EVERY_TIME -> {
                PermissionResult.NeedConfirmation(
                    capability = capability,
                    message = describeCapability(capability)
                )
            }
        }
    }

    fun getLevel(capability: AgentCapability): PermissionLevel {
        val stored = prefs.getString(capability.name, null)
        return if (stored != null) {
            try { PermissionLevel.valueOf(stored) } catch (_: Exception) { defaults[capability] ?: PermissionLevel.ALWAYS }
        } else {
            defaults[capability] ?: PermissionLevel.ALWAYS
        }
    }

    fun setLevel(capability: AgentCapability, level: PermissionLevel) {
        prefs.edit().putString(capability.name, level.name).apply()
        Log.d(TAG, "Permission updated: $capability → $level")
    }

    /** 모든 능력의 현재 권한 수준 반환 (Settings UI용) */
    fun getAllLevels(): Map<AgentCapability, PermissionLevel> {
        return AgentCapability.entries.associateWith { getLevel(it) }
    }

    private fun describeCapability(capability: AgentCapability): String = when (capability) {
        AgentCapability.LOCATION_ACCESS -> "EZ가 현재 위치를 확인하려 합니다"
        AgentCapability.CALENDAR_ACCESS -> "EZ가 캘린더 일정을 읽으려 합니다"
        AgentCapability.PROACTIVE_NOTIFY -> "EZ가 먼저 알림을 보내려 합니다"
        AgentCapability.PATTERN_LEARNING -> "EZ가 이동 패턴을 학습하려 합니다"
        AgentCapability.MEMORY_PERSIST -> "EZ가 대화 내용을 기억하려 합니다"
        AgentCapability.SKILL_AUTO_CHAIN -> "EZ가 여러 기능을 자동으로 연결하려 합니다"
    }
}
