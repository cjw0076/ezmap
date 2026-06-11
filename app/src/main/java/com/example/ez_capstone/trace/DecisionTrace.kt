package com.example.ez_capstone.trace

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 에이전트 판단 추적 기록.
 * 체이닝/fallback/안전 필터의 각 단계를 기록하여
 * "왜 이 추천인지" 설명 가능성을 제공한다.
 */
@Entity(
    tableName = "decision_traces",
    indices = [Index(value = ["sessionId", "createdAt"])]
)
data class DecisionTraceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val requestText: String,
    val stepsJson: String,          // JSON array of TraceStep
    val totalDurationMs: Long,
    val iterationCount: Int,
    val toolsUsed: String,          // comma-separated tool names
    val safetyDecision: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getSteps(): List<TraceStep> {
        return try {
            Gson().fromJson(stepsJson, object : TypeToken<List<TraceStep>>() {}.type)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun toHumanSummary(): String {
        val steps = getSteps()
        val tools = steps.filter { it.type == "tool_call" && it.error == null }
            .mapNotNull { it.toolName }
        val fallbacks = steps.count { it.fallbackUsed }

        return buildString {
            if (tools.isNotEmpty()) append("${tools.joinToString(" → ")} 호출")
            if (fallbacks > 0) append(" (대체 ${fallbacks}회)")
            if (safetyDecision != null && safetyDecision != "Allow") {
                append(" [안전 필터: $safetyDecision]")
            }
            append(" — ${totalDurationMs}ms")
        }
    }
}

/**
 * 에이전트 루프 내 각 단계 기록.
 */
data class TraceStep(
    val iteration: Int,
    val type: String,               // "llm_call", "tool_call", "safety_filter"
    val toolName: String? = null,
    val durationMs: Long,
    val output: String? = null,     // 축약된 결과 (최대 200자)
    val error: String? = null,
    val fallbackUsed: Boolean = false,
    val fallbackFrom: String? = null
)

/**
 * doChatLoop 내에서 trace를 수집하는 빌더.
 */
class DecisionTraceBuilder(
    private val sessionId: String,
    private val requestText: String
) {
    private val steps = mutableListOf<TraceStep>()
    private val startTime = System.currentTimeMillis()
    private val toolNames = mutableListOf<String>()

    fun addStep(step: TraceStep) {
        steps.add(step)
        if (step.type == "tool_call" && step.toolName != null) {
            toolNames.add(step.toolName)
        }
    }

    fun build(iterationCount: Int, safetyDecision: String? = null): DecisionTraceEntity {
        return DecisionTraceEntity(
            sessionId = sessionId,
            requestText = requestText.take(200),
            stepsJson = Gson().toJson(steps),
            totalDurationMs = System.currentTimeMillis() - startTime,
            iterationCount = iterationCount,
            toolsUsed = toolNames.joinToString(","),
            safetyDecision = safetyDecision
        )
    }
}
