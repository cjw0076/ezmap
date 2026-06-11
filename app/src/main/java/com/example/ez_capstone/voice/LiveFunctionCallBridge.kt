package com.example.ez_capstone.voice

import android.util.Log
import com.example.ez_capstone.agent.AgentPlan
import com.example.ez_capstone.agent.AgentPlanSource
import com.example.ez_capstone.agent.AgentPlanStep
import com.example.ez_capstone.agent.AgentRun
import com.example.ez_capstone.agent.AgentRunInputMode
import com.example.ez_capstone.agent.AgentRunRecorder
import com.example.ez_capstone.agent.AgentToolExecutionHarness
import com.example.ez_capstone.agent.FallbackStrategy
import com.example.ez_capstone.agent.ToolExposure
import com.example.ez_capstone.agent.ToolExecutor
import com.example.ez_capstone.agent.ToolRegistry
import com.example.ez_capstone.agent.models.AgentContext
import com.example.ez_capstone.agent.models.AgentDrivingState
import com.example.ez_capstone.agent.models.AgentResponse
import com.example.ez_capstone.analytics.AgentAnalytics
import com.example.ez_capstone.governance.AgentCapability
import com.example.ez_capstone.governance.PermissionManager
import com.example.ez_capstone.governance.PermissionResult
import com.example.ez_capstone.trace.DecisionTraceBuilder
import kotlinx.coroutines.*
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 9 — Live API Function Call 브릿지.
 *
 * LiveVoiceSession에서 ToolCall 이벤트를 수신 →
 * 기존 ToolExecutor.execute()로 실행 →
 * 결과를 LiveVoiceSession.sendToolResponse()로 주입.
 *
 * 기존 25개 Tool 선언(ToolDeclarations.kt)을 변경 없이 재사용.
 */
@Singleton
class LiveFunctionCallBridge @Inject constructor(
    private val toolExecutor: ToolExecutor,
    private val liveVoiceSession: LiveVoiceSession,
    private val fallbackStrategy: FallbackStrategy,
    private val agentAnalytics: AgentAnalytics,
    private val permissionManager: PermissionManager
) {
    companion object {
        private const val TAG = "LiveFunctionCallBridge"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // 권한 단일 게이트 — Live 음성 경로의 위치 의존 도구도 위치 권한을 강제(출처 무관 일관).
    private val harness = AgentToolExecutionHarness(
        toolExecutor, fallbackStrategy, agentAnalytics,
        locationPermissionGranted = {
            permissionManager.check(AgentCapability.LOCATION_ACCESS) is PermissionResult.Granted
        }
    )

    // ConversationViewModel이 GPS 업데이트 시 이 값을 갱신해야 한다.
    // 갱신 없으면 location-dependent tool(search_places, get_directions 등)이 null 좌표로 호출됨.
    @Volatile private var locationX: Double? = null  // longitude
    @Volatile private var locationY: Double? = null  // latitude
    @Volatile private var currentDrivingState: String = AgentDrivingState.IDLE
    @Volatile private var lastAgentRun: AgentRun? = null

    fun lastAgentRunSnapshot(): AgentRun? = lastAgentRun

    /** ConversationViewModel 또는 NavigationViewModel에서 GPS/주행 상태 변경 시 호출. */
    fun updateLocationContext(lat: Double?, lng: Double?, drivingState: String = AgentDrivingState.IDLE) {
        locationX = lng
        locationY = lat
        currentDrivingState = drivingState
    }

    /**
     * LiveVoiceSession의 events Flow를 구독해서 ToolCall 이벤트를 처리.
     * VoiceStateCoordinator 또는 ConversationViewModel에서 한 번 호출.
     */
    fun attach(): Job = scope.launch {
        liveVoiceSession.events.collect { event ->
            if (event is LiveVoiceSession.SessionEvent.ToolCall) {
                handleToolCall(event)
            }
        }
    }

    private suspend fun handleToolCall(call: LiveVoiceSession.SessionEvent.ToolCall) {
        Log.d(TAG, "Tool 실행: ${call.name} (id=${call.callId})")
        try {
            val result = executeToolCall(call)
            liveVoiceSession.sendToolResponse(call.callId, call.name, result.toString())
            Log.d(TAG, "Tool 완료: ${call.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Tool 실패: ${call.name} — ${e.javaClass.simpleName}")
            val errorResult = """{"error": "tool_execution_failed"}"""
            liveVoiceSession.sendToolResponse(call.callId, call.name, errorResult)
        }
    }

    internal suspend fun executeToolCall(call: LiveVoiceSession.SessionEvent.ToolCall): JSONObject {
        val context = currentContext()
        val recorder = AgentRunRecorder.start(
            userText = "live:${call.name}",
            context = context,
            inputMode = AgentRunInputMode.VOICE
        )

        return try {
            val spec = ToolRegistry.builtIn.specOrNull(call.name)
            if (spec == null || ToolExposure.LIVE_API !in spec.exposures) {
                recorder
                    .recordRoute("live_api_rejected")
                    .recordSafetyDecision("live_tool_rejected", call.name)
                val rejected = JSONObject()
                    .put("error", "live_tool_not_allowed")
                    .put("tool", call.name)
                lastAgentRun = recorder.finish(
                    AgentResponse(replyText = "Live tool rejected", toolsUsed = emptyList())
                )
                return rejected
            }

            val argsMap = parseArgs(call.argsJson)
            val plan = AgentPlan(
                source = AgentPlanSource.LIVE_API,
                sourceId = call.callId.ifBlank { call.name },
                routeLabel = "live_api_tool_call",
                steps = listOf(AgentPlanStep(call.name, argsMap))
            )
            recorder
                .recordRoute(plan.routeLabel)
                .recordPlan(plan)
                .recordSafetyDecision("live_tool_allowed", call.name)

            val toolResultStore = mutableMapOf<String, Any>()
            val toolsUsed = mutableListOf<String>()
            val succeededTools = mutableSetOf<String>()
            val responses = harness.executePlan(
                plan = plan,
                context = context,
                traceBuilder = DecisionTraceBuilder(plan.id, "live:${call.name}"),
                runRecorder = recorder,
                toolResultStore = toolResultStore,
                toolsUsed = toolsUsed,
                succeededTools = succeededTools
            )
            val result = extractResponse(responses)
            lastAgentRun = recorder.finish(
                AgentResponse(
                    replyText = result.optString("message", result.toString()),
                    toolsUsed = toolsUsed
                )
            )
            result
        } catch (e: Exception) {
            recorder
                .recordRoute("live_api_error")
                .recordFallback("live_api_tool_error", e.javaClass.simpleName)
            val failed = JSONObject()
                .put("error", "tool_execution_failed")
                .put("tool", call.name)
            lastAgentRun = recorder.finish(
                AgentResponse(replyText = "Live tool failed", toolsUsed = emptyList())
            )
            failed
        }
    }

    private fun currentContext(): AgentContext =
        AgentContext(
            locationX = locationX,
            locationY = locationY,
            drivingState = currentDrivingState
        )

    private fun parseArgs(argsJson: String): Map<String, Any?> {
        val argsMap = mutableMapOf<String, Any?>()
        val argsObj = JSONObject(argsJson.ifBlank { "{}" })
        argsObj.keys().forEach { key ->
            val value = argsObj.get(key)
            argsMap[key] = if (value == JSONObject.NULL) null else value
        }
        return argsMap
    }

    private fun extractResponse(responses: List<JSONObject>): JSONObject =
        responses
            .firstOrNull()
            ?.optJSONObject("functionResponse")
            ?.optJSONObject("response")
            ?: JSONObject().put("error", "missing_tool_response")

    fun detach() {
        scope.coroutineContext.cancelChildren()
    }
}
