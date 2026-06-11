package com.example.ez_capstone.agent

import android.util.Log
import com.example.ez_capstone.agent.models.AgentContext
import com.example.ez_capstone.analytics.AgentAnalytics
import com.example.ez_capstone.safety.ToolRiskTier
import com.example.ez_capstone.trace.DecisionTraceBuilder
import com.example.ez_capstone.trace.TraceStep
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import org.json.JSONArray
import org.json.JSONObject

class AgentToolExecutionHarness(
    private val toolExecutor: ToolExecutor,
    private val fallbackStrategy: FallbackStrategy,
    private val agentAnalytics: AgentAnalytics,
    // 권한 단일 게이트(Wave 5) — 위치 권한 부여 여부를 반환. null이면 미강제(테스트/내부 경로).
    // 호출자(Gemini/Live)가 PermissionManager로 람다를 만들어 주입 → 하니스는 Android-light 유지.
    private val locationPermissionGranted: (() -> Boolean)? = null,
    // 외부(MCP) 도구의 분류된 위험등급 조회 — 보통 McpToolGateway.riskTierOf. 안전 게이트가
    // 이름 휴리스틱 대신 실제 메타데이터로 effectful 외부 도구를 판단하게 한다. MCP는 Gemini
    // 경로에서만 호출되므로 보통 그 경로에서만 주입된다.
    private val externalRiskTier: ((String) -> ToolRiskTier?)? = null
) {
    suspend fun executePlan(
        plan: AgentPlan,
        context: AgentContext,
        traceBuilder: DecisionTraceBuilder,
        runRecorder: AgentRunRecorder,
        toolResultStore: MutableMap<String, Any>,
        toolsUsed: MutableList<String>,
        succeededTools: MutableSet<String>
    ): List<JSONObject> {
        val responses = mutableListOf<JSONObject>()
        plan.toFunctionCalls().forEachIndexed { index, call ->
            responses += executeAll(
                functionCalls = listOf(call),
                iteration = index,
                context = context,
                traceBuilder = traceBuilder,
                runRecorder = runRecorder,
                toolResultStore = toolResultStore,
                toolsUsed = toolsUsed,
                succeededTools = succeededTools
            )
        }
        return responses
    }

    suspend fun executeAll(
        functionCalls: List<JSONObject>,
        iteration: Int,
        context: AgentContext,
        traceBuilder: DecisionTraceBuilder,
        runRecorder: AgentRunRecorder,
        toolResultStore: MutableMap<String, Any>,
        toolsUsed: MutableList<String>,
        succeededTools: MutableSet<String>
    ): List<JSONObject> {
        val startedAt = System.currentTimeMillis()
        val executionResults = supervisorScope {
            functionCalls.map { part ->
                async {
                    executeOne(
                        part = part,
                        context = context
                    )
                }
            }.awaitAll()
        }
        val responses = executionResults.map { result ->
            mergeExecutionResult(
                result = result,
                iteration = iteration,
                traceBuilder = traceBuilder,
                runRecorder = runRecorder,
                toolResultStore = toolResultStore,
                toolsUsed = toolsUsed,
                succeededTools = succeededTools
            )
        }
        Log.d(TAG, "[Perf] Tool execution #${iteration + 1}: ${System.currentTimeMillis() - startedAt}ms")
        return responses
    }

    private suspend fun executeOne(
        part: JSONObject,
        context: AgentContext
    ): ToolExecutionResult {
        val fc = part.getJSONObject("functionCall")
        val toolName = fc.optString("name")
        val argsJson = fc.optJSONObject("args") ?: JSONObject()
        val args = AgentJson.toMap(argsJson)

        val toolStartedAt = System.currentTimeMillis()

        // 단일 안전 게이트 (Wave 5 / gap #4·#5) — 출처(Gemini/Live/Skill) 무관, 실행 직전 필수 통과.
        // 빌트인 ToolSpec 없는 외부(MCP) effectful 도구가 확인 없이 부작용을 내는 것을 차단한다.
        val verdict = ToolSafetyGate.evaluate(toolName, externalRiskTier = externalRiskTier)
        if (!verdict.allowed) {
            Log.w(TAG, "Tool [$toolName] blocked by safety gate: ${verdict.reason}")
            val blocked = JSONObject()
                .put("error", "blocked_by_safety_gate")
                .put("reason", verdict.reason)
                .put("tool", toolName)
            return ToolExecutionResult(
                toolName = toolName,
                args = args,
                durationMs = 0,
                result = blocked,
                strippedResult = blocked,
                error = "blocked_by_safety_gate",
                errorKind = "SAFETY_BLOCKED",
                safetyReason = verdict.reason
            )
        }

        // 권한 단일 게이트 — 위치 의존 도구는 위치 권한이 필요(출처 무관: Gemini/Live).
        // doChatLoop 시작점 외에 Live 경로에서도 강제. 컨텍스트에 위치가 있는데 권한이 막혀 있으면 차단.
        if (locationPermissionGranted != null &&
            toolName in LOCATION_DEPENDENT_TOOLS &&
            contextHasLocation(context) &&
            !locationPermissionGranted.invoke()
        ) {
            Log.w(TAG, "Tool [$toolName] blocked — location permission denied")
            val denied = JSONObject()
                .put("error", "location_permission_denied")
                .put("tool", toolName)
            return ToolExecutionResult(
                toolName = toolName,
                args = args,
                durationMs = 0,
                result = denied,
                strippedResult = denied,
                error = "location_permission_denied",
                errorKind = "PERMISSION_DENIED",
                safetyReason = "permission:location_denied"
            )
        }

        return try {
            val result = fallbackStrategy.executeWithRecovery(toolName, args, context, toolExecutor::execute)
            val toolDuration = System.currentTimeMillis() - toolStartedAt
            val fallbackFrom = result.optString("_fallback_from").takeIf { result.has("_fallback_from") }
            val error = result.optString("error").takeIf { result.has("error") }
            val strippedResult = toolExecutor.stripForGemini(toolName, result)

            ToolExecutionResult(
                toolName = toolName,
                args = args,
                durationMs = toolDuration,
                result = result,
                strippedResult = strippedResult,
                fallbackFrom = fallbackFrom,
                error = error,
                errorKind = result.optString("error_kind", "UNCLASSIFIED").takeIf { error != null },
                safetyReason = verdict.reason
            )
        } catch (e: Exception) {
            ToolExecutionResult(
                toolName = toolName,
                args = args,
                durationMs = System.currentTimeMillis() - toolStartedAt,
                exceptionMessage = e.message ?: "Tool 오류",
                safetyReason = verdict.reason
            )
        }
    }

    private fun mergeExecutionResult(
        result: ToolExecutionResult,
        iteration: Int,
        traceBuilder: DecisionTraceBuilder,
        runRecorder: AgentRunRecorder,
        toolResultStore: MutableMap<String, Any>,
        toolsUsed: MutableList<String>,
        succeededTools: MutableSet<String>
    ): JSONObject {
        toolsUsed.add(result.toolName)
        agentAnalytics.recordSkillCall(result.toolName)
        // 단일 안전 게이트 판정을 AgentRun에 기록 → 모든 도구 호출이 통과한 '관측 가능한 필수 단계'.
        result.safetyReason?.let { runRecorder.recordSafetyDecision(it, result.toolName) }

        val exceptionMessage = result.exceptionMessage
        if (exceptionMessage != null) {
            Log.e(TAG, "Tool error (${result.toolName}): $exceptionMessage")
            runRecorder.recordToolCall(
                toolName = result.toolName,
                args = result.args,
                durationMs = result.durationMs,
                error = exceptionMessage
            )
            return JSONObject().put(
                "functionResponse",
                JSONObject().apply {
                    put("name", result.toolName)
                    put("response", JSONObject().put("error", exceptionMessage))
                }
            )
        }

        val rawResult = result.result ?: JSONObject()
        val strippedResult = result.strippedResult ?: JSONObject()
        traceBuilder.addStep(
            TraceStep(
                iteration = iteration,
                type = "tool_call",
                toolName = result.toolName,
                durationMs = result.durationMs,
                output = strippedResult.toString().take(200),
                error = result.error,
                fallbackUsed = result.fallbackFrom != null,
                fallbackFrom = result.fallbackFrom
            )
        )
        runRecorder.recordToolCall(
            toolName = result.toolName,
            args = result.args,
            durationMs = result.durationMs,
            output = strippedResult.toString(),
            error = result.error,
            fallbackFrom = result.fallbackFrom
        )

        if (result.error == null) {
            succeededTools.add(result.toolName)
        } else {
            val kind = result.errorKind ?: "UNCLASSIFIED"
            agentAnalytics.recordToolFailure(result.toolName, kind)
            Log.w(TAG, "Tool [${result.toolName}] failed kind=$kind: ${result.error.take(80)}")
        }

        storeUiResult(result.toolName, rawResult, toolResultStore)

        return JSONObject().put(
            "functionResponse",
            JSONObject().apply {
                put("name", result.toolName)
                put("response", strippedResult)
            }
        )
    }

    private fun storeUiResult(
        toolName: String,
        result: JSONObject,
        toolResultStore: MutableMap<String, Any>
    ) {
        when (toolName) {
            "get_directions", "get_directions_naver" -> mergeDirectionsResult(result, toolResultStore)
            "search_places", "search_pharmacies", "search_hospitals",
            "get_gas_stations", "get_ev_chargers", "get_parking",
            "get_realtime_parking" -> {
                val placesArr = result.optJSONArray("places")
                    ?: result.optJSONArray("stations")
                    ?: result.optJSONArray("chargers")
                    ?: result.optJSONArray("parking_lots")
                    ?: result.optJSONArray("pharmacys")
                    ?: result.optJSONArray("hospitals")
                    ?: JSONArray()
                toolResultStore["places"] = JSONObject().put("places", placesArr).toString()
            }
            "get_schedule" -> toolResultStore["schedule"] = result.toString()
            "get_weather", "get_weather_kma" -> toolResultStore["weather"] = result.toString()
            "send_message" -> toolResultStore["message"] = result.toString()
            "make_call" -> toolResultStore["call"] = result.toString()
            "geocode" -> {
                val existing = toolResultStore.getOrDefault("geocode", "[]") as String
                val arr = JSONArray(existing)
                arr.put(result)
                toolResultStore["geocode"] = arr.toString()
            }
        }
    }

    private fun mergeDirectionsResult(
        result: JSONObject,
        toolResultStore: MutableMap<String, Any>
    ) {
        val existing = toolResultStore["directions"]
        if (existing == null) {
            toolResultStore["directions"] = result.toString()
            return
        }

        try {
            val existingJson = JSONObject(existing as String)
            val existingRoutes = existingJson.optJSONArray("routes") ?: JSONArray()
            val newRoutes = result.optJSONArray("routes") ?: JSONArray()
            for (i in 0 until newRoutes.length()) {
                existingRoutes.put(newRoutes.getJSONObject(i))
            }
            existingJson.put("routes", existingRoutes)
            toolResultStore["directions"] = existingJson.toString()
        } catch (_: Exception) {
            toolResultStore["directions"] = result.toString()
        }
    }

    companion object {
        private const val TAG = "AgentToolExecutionHarness"

        // 사용자 현재 위치(GPS)에 의존하는 도구 — 위치 권한 게이트 대상.
        // 명시 좌표로도 호출 가능하지만, 컨텍스트에 위치가 있고 권한이 막혔으면 일관되게 차단한다.
        private val LOCATION_DEPENDENT_TOOLS = setOf(
            "search_places", "get_directions", "get_future_eta", "reverse_geocode",
            "get_gas_stations", "get_ev_chargers", "get_parking", "get_realtime_parking",
            "get_weather_kma", "get_air_quality", "search_pharmacies", "search_hospitals",
            "get_traffic_speed", "get_traffic_incidents", "get_traffic_cctv",
            "get_road_risk", "get_speed_cameras", "get_road_incidents", "suggest_parking"
        )

        private fun contextHasLocation(context: AgentContext): Boolean =
            context.locationX != null && context.locationY != null &&
                !(context.locationX == 0.0 && context.locationY == 0.0)
    }

    private data class ToolExecutionResult(
        val toolName: String,
        val args: Map<String, Any?>,
        val durationMs: Long,
        val result: JSONObject? = null,
        val strippedResult: JSONObject? = null,
        val fallbackFrom: String? = null,
        val error: String? = null,
        val errorKind: String? = null,
        val exceptionMessage: String? = null,
        // 단일 안전 게이트(ToolSafetyGate)의 판정 사유 — AgentRun에 기록되어 관측 가능.
        val safetyReason: String? = null
    )
}
