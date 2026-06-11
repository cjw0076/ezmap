package com.example.ez_capstone.agent

import com.example.ez_capstone.agent.models.AgentContext
import com.example.ez_capstone.agent.models.AgentDrivingState
import com.example.ez_capstone.agent.models.AgentResponse
import com.example.ez_capstone.trace.DecisionTraceEntity
import java.util.UUID

enum class AgentRunInputMode {
    TEXT,
    VOICE,
    MCP,
    A2A
}

data class AgentRunContextSnapshot(
    val drivingState: String,
    val hasLocation: Boolean,
    val hasDestination: Boolean
) {
    companion object {
        fun from(context: AgentContext): AgentRunContextSnapshot =
            AgentRunContextSnapshot(
                drivingState = AgentDrivingState.normalize(context.drivingState),
                hasLocation = context.locationX != null && context.locationY != null &&
                    !(context.locationX == 0.0 && context.locationY == 0.0),
                hasDestination = context.destX != null && context.destY != null
            )
    }
}

data class AgentRunToolCall(
    val toolName: String,
    val argKeys: List<String>,
    val status: String,
    val durationMs: Long,
    val outputPreview: String? = null,
    val errorPreview: String? = null,
    val fallbackFrom: String? = null
)

data class AgentRunDecision(
    val kind: String,
    val value: String,
    val reasonPreview: String? = null
)

data class AgentRunObservation(
    val kind: String,
    val value: String,
    val preview: String? = null
)

data class AgentRunFinalResponse(
    val replyPreview: String,
    val uiAction: String,
    val toolsUsed: List<String>
)

data class AgentRunPlanSnapshot(
    val planId: String,
    val source: AgentPlanSource,
    val sourceId: String,
    val routeLabel: String,
    val stepToolNames: List<String>
)

data class AgentRun(
    val id: String,
    val inputMode: AgentRunInputMode,
    val requestPreview: String,
    val contextSnapshot: AgentRunContextSnapshot,
    val routeLabel: String,
    val plan: AgentRunPlanSnapshot?,
    val toolCalls: List<AgentRunToolCall>,
    val decisions: List<AgentRunDecision>,
    val observations: List<AgentRunObservation>,
    val finalResponse: AgentRunFinalResponse?,
    val decisionTraceId: Long?,
    val decisionTraceSessionId: String?,
    val startedAt: Long,
    val completedAt: Long?
) {
    val isComplete: Boolean get() = finalResponse != null && completedAt != null
}

class AgentRunRecorder private constructor(
    private val id: String,
    private val inputMode: AgentRunInputMode,
    private val requestPreview: String,
    private val contextSnapshot: AgentRunContextSnapshot,
    private val startedAt: Long
) {
    private var routeLabel: String = "unrouted"
    private var plan: AgentRunPlanSnapshot? = null
    private val toolCalls = mutableListOf<AgentRunToolCall>()
    private val decisions = mutableListOf<AgentRunDecision>()
    private val observations = mutableListOf<AgentRunObservation>()
    private var decisionTraceId: Long? = null
    private var decisionTraceSessionId: String? = null

    fun recordRoute(label: String): AgentRunRecorder = apply {
        routeLabel = label.ifBlank { "unrouted" }
        observations += AgentRunObservation("route", routeLabel)
    }

    fun recordObservation(kind: String, value: String, preview: String? = null): AgentRunRecorder = apply {
        observations += AgentRunObservation(kind.take(MAX_KIND), bound(value), preview?.let(::bound))
    }

    fun recordPermissionDecision(capability: String, allowed: Boolean): AgentRunRecorder = apply {
        decisions += AgentRunDecision(
            kind = "permission",
            value = "$capability:${if (allowed) "allow" else "deny"}"
        )
    }

    fun recordSafetyDecision(decision: String, reason: String? = null): AgentRunRecorder = apply {
        decisions += AgentRunDecision("safety", decision.ifBlank { "Unknown" }, reason?.let(::bound))
    }

    fun recordFallback(kind: String, reason: String? = null): AgentRunRecorder = apply {
        observations += AgentRunObservation("fallback", kind.ifBlank { "unknown" }, reason?.let(::bound))
    }

    fun recordPlan(plan: AgentPlan): AgentRunRecorder = apply {
        this.plan = AgentRunPlanSnapshot(
            planId = plan.id,
            source = plan.source,
            sourceId = bound(plan.sourceId),
            routeLabel = bound(plan.routeLabel),
            stepToolNames = plan.toolNames.map { bound(it, MAX_TOOL_NAME) }
        )
        observations += AgentRunObservation(
            kind = "plan",
            value = plan.routeLabel,
            preview = "${plan.source}:${plan.toolNames.joinToString(" -> ")}"
        )
    }

    fun recordToolCall(
        toolName: String,
        args: Map<String, Any?>,
        durationMs: Long,
        output: String? = null,
        error: String? = null,
        fallbackFrom: String? = null
    ): AgentRunRecorder = apply {
        toolCalls += AgentRunToolCall(
            toolName = bound(toolName, MAX_TOOL_NAME),
            argKeys = args.keys.map { bound(it, MAX_TOOL_NAME) }.sorted(),
            status = if (error == null) "ok" else "error",
            durationMs = durationMs.coerceAtLeast(0),
            outputPreview = output?.let(::bound),
            errorPreview = error?.let(::bound),
            fallbackFrom = fallbackFrom?.let(::bound)
        )
    }

    fun linkDecisionTrace(trace: DecisionTraceEntity): AgentRunRecorder = apply {
        decisionTraceId = trace.id.takeIf { it > 0 }
        decisionTraceSessionId = trace.sessionId
        observations += AgentRunObservation("decision_trace", trace.sessionId, trace.toHumanSummary().take(MAX_PREVIEW))
    }

    fun finish(response: AgentResponse, completedAt: Long = System.currentTimeMillis()): AgentRun =
        AgentRun(
            id = id,
            inputMode = inputMode,
            requestPreview = requestPreview,
            contextSnapshot = contextSnapshot,
            routeLabel = routeLabel,
            plan = plan,
            toolCalls = toolCalls.toList(),
            decisions = decisions.toList(),
            observations = observations.toList(),
            finalResponse = AgentRunFinalResponse(
                replyPreview = bound(response.replyText),
                uiAction = response.uiAction,
                toolsUsed = response.toolsUsed
            ),
            decisionTraceId = decisionTraceId,
            decisionTraceSessionId = decisionTraceSessionId,
            startedAt = startedAt,
            completedAt = completedAt
        )

    companion object {
        private const val MAX_PREVIEW = 200
        private const val MAX_KIND = 48
        private const val MAX_TOOL_NAME = 80

        fun start(
            userText: String,
            context: AgentContext,
            inputMode: AgentRunInputMode = AgentRunInputMode.TEXT,
            startedAt: Long = System.currentTimeMillis()
        ): AgentRunRecorder =
            AgentRunRecorder(
                id = UUID.randomUUID().toString(),
                inputMode = inputMode,
                requestPreview = bound(userText),
                contextSnapshot = AgentRunContextSnapshot.from(context),
                startedAt = startedAt
            )

        private fun bound(value: String, max: Int = MAX_PREVIEW): String =
            value.replace(Regex("\\s+"), " ").trim().take(max)
    }
}
