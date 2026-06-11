package com.example.ez_capstone.analytics

import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class AgentAnalytics @Inject constructor(
    @Named("analytics_prefs") private val prefs: SharedPreferences
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private fun today() = dateFormat.format(Date())

    fun recordConversation() {
        val key = "conv_${today()}"
        prefs.edit().putInt(key, (prefs.getInt(key, 0) + 1)).apply()
    }

    fun recordSkillCall(name: String) {
        val key = "skill_${name}_${today()}"
        prefs.edit().putInt(key, (prefs.getInt(key, 0) + 1)).apply()
    }

    /**
     * 도구 실패를 도구·원인(kind)별로 집계 — "무엇이·왜 실패했는지" 시간에 걸쳐 알기 위한 원장.
     * 예: fail_get_directions_AUTH_FAILED, fail_get_weather_MISSING_KEY.
     * 운영/디버그에서 "어떤 키가 자꾸 죽는지", "어떤 API가 불안정한지"를 데이터로 본다.
     */
    fun recordToolFailure(toolName: String, kindCode: String) {
        val key = "fail_${toolName}_${kindCode}_${today()}"
        prefs.edit().putInt(key, (prefs.getInt(key, 0) + 1)).apply()
    }

    fun recordChainDepth(depth: Int) {
        val key = "chain_depth_${today()}"
        val existing = prefs.getString(key, "") ?: ""
        val depths = if (existing.isEmpty()) mutableListOf()
                     else existing.split(",").map { it.toInt() }.toMutableList()
        depths.add(depth)
        prefs.edit().putString(key, depths.joinToString(",")).apply()
    }

    fun recordVoiceUsage() {
        val key = "voice_${today()}"
        prefs.edit().putInt(key, (prefs.getInt(key, 0) + 1)).apply()
    }

    // ── Phase B-2: L3 계층 계측 (SELF_LEARNING_AGENT.md §9.1) ──
    // 비전 R3 프라이버시: fingerprint/slot/skillId 등 raw 데이터는 절대 저장하지 않음.
    // 집계된 카운터만 기록.

    /**
     * 첫 사용 시각 기록. Day 0 KPI 계산용.
     */
    private fun ensureFirstUseStamp() {
        if (!prefs.contains("first_use_ms")) {
            prefs.edit().putLong("first_use_ms", System.currentTimeMillis()).apply()
        }
    }

    /**
     * L3 매칭 hit 1회 기록. tier는 "SAFE" / "STATEFUL" 등 ToolRiskTier.code.
     * Day 0 윈도우 여부는 자동 판별해서 별도 카운터에도 누적.
     */
    fun recordL3Hit(tier: String) {
        ensureFirstUseStamp()
        val date = today()
        prefs.edit().apply {
            // 일별 tier별 L3 hit
            val tierKey = "l3_hit_${tier}_$date"
            putInt(tierKey, prefs.getInt(tierKey, 0) + 1)
            // 일별 전체 L3 hit
            val totalKey = "l3_hit_total_$date"
            putInt(totalKey, prefs.getInt(totalKey, 0) + 1)
            // Day 0 누적 (첫 24시간 이내면 별도 기록)
            val elapsedMs = System.currentTimeMillis() - prefs.getLong("first_use_ms", 0L)
            if (elapsedMs in 0..(24 * 60 * 60 * 1000L)) {
                putInt("day0_l3_hit", prefs.getInt("day0_l3_hit", 0) + 1)
            }
            apply()
        }
    }

    /**
     * LearnedSkill 실행 후 피드백 집계. positive=성공, negative=부정(취소/재질문/"아니야").
     * Phase B-3 보수적 학습 가드레일 + 결정 5(B-4 연기) 기반 — 글로벌 임계 조정 X,
     * UI(AgentMemoryScreen)에서만 표시.
     */
    fun recordLearnedSkillFeedback(positive: Boolean) {
        val date = today()
        val key = if (positive) "skill_fb_pos_$date" else "skill_fb_neg_$date"
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    /**
     * Day 0 L3 hit 비율. 전체 대화 대비 L3로 처리된 비율.
     * KPI 목표: ≥ 15% (스켈레톤 템플릿 효과)
     */
    fun getDay0L3HitRate(): Float {
        val day0Hit = prefs.getInt("day0_l3_hit", 0)
        val firstUse = prefs.getLong("first_use_ms", 0L)
        if (firstUse == 0L) return 0f
        // Day 0 conv: 해당 날짜(first_use 날짜)의 conversation 카운트와 근사
        val day0Date = dateFormat.format(Date(firstUse))
        val day0Conv = prefs.getInt("conv_$day0Date", 0)
        return if (day0Conv > 0) day0Hit.toFloat() / day0Conv else 0f
    }

    /**
     * 최근 windowDays 기간의 L3 hit 비율 (전체 대화 대비).
     */
    fun getL3HitRate(windowDays: Int = 7): Float {
        val cal = java.util.Calendar.getInstance()
        var totalHit = 0
        var totalConv = 0
        for (i in 0 until windowDays) {
            val date = dateFormat.format(cal.time)
            totalHit += prefs.getInt("l3_hit_total_$date", 0)
            totalConv += prefs.getInt("conv_$date", 0)
            cal.add(java.util.Calendar.DAY_OF_MONTH, -1)
        }
        return if (totalConv > 0) totalHit.toFloat() / totalConv else 0f
    }

    /**
     * 최근 windowDays 기간의 LearnedSkill 부정 피드백율.
     * 결정 5 연기된 "적응형 임계"의 근거 수집용. UI 표시 + 향후 분석용.
     */
    fun getLearnedSkillFeedbackRate(windowDays: Int = 30): Float {
        val cal = java.util.Calendar.getInstance()
        var pos = 0
        var neg = 0
        for (i in 0 until windowDays) {
            val date = dateFormat.format(cal.time)
            pos += prefs.getInt("skill_fb_pos_$date", 0)
            neg += prefs.getInt("skill_fb_neg_$date", 0)
            cal.add(java.util.Calendar.DAY_OF_MONTH, -1)
        }
        val total = pos + neg
        return if (total > 0) neg.toFloat() / total else 0f
    }

    data class DailyStats(
        val date: String,
        val conversations: Int,
        val skillCallsTop5: List<Pair<String, Int>>,
        val avgChainDepth: Float,
        val voiceCount: Int,
        val l3HitCount: Int = 0,
        val day0L3HitRate: Float = 0f,
        val learnedSkillFeedbackRate: Float = 0f
    )

    fun getTodayStats(): DailyStats {
        val date = today()
        val conversations = prefs.getInt("conv_$date", 0)
        val voiceCount = prefs.getInt("voice_$date", 0)
        // "skill_" prefix 중 "skill_fb_" (feedback)는 제외해야 skillCalls에 안 섞임
        val skillKeys = prefs.all.keys.filter {
            it.startsWith("skill_") && !it.startsWith("skill_fb_") && it.endsWith("_$date")
        }
        val skillCalls = skillKeys.map { key ->
            val name = key.removePrefix("skill_").removeSuffix("_$date")
            name to (prefs.getInt(key, 0))
        }.sortedByDescending { it.second }.take(5)
        val depthStr = prefs.getString("chain_depth_$date", "") ?: ""
        val avgDepth = if (depthStr.isEmpty()) 0f
                       else depthStr.split(",").map { it.toFloat() }.average().toFloat()
        val l3HitCount = prefs.getInt("l3_hit_total_$date", 0)
        return DailyStats(
            date = date,
            conversations = conversations,
            skillCallsTop5 = skillCalls,
            avgChainDepth = avgDepth,
            voiceCount = voiceCount,
            l3HitCount = l3HitCount,
            day0L3HitRate = getDay0L3HitRate(),
            learnedSkillFeedbackRate = getLearnedSkillFeedbackRate(30)
        )
    }
}
