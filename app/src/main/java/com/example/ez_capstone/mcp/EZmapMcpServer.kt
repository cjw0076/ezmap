package com.example.ez_capstone.mcp

import com.example.ez_capstone.agent.AgentPlan
import com.example.ez_capstone.agent.AgentPlanSource
import com.example.ez_capstone.agent.AgentPlanStep
import com.example.ez_capstone.agent.AgentRun
import com.example.ez_capstone.agent.AgentRunInputMode
import com.example.ez_capstone.agent.AgentRunRecorder
import com.example.ez_capstone.agent.AgentToolExecutionHarness
import com.example.ez_capstone.agent.FallbackStrategy
import com.example.ez_capstone.agent.ToolExecutor
import com.example.ez_capstone.agent.models.AgentContext
import com.example.ez_capstone.agent.models.AgentResponse
import com.example.ez_capstone.analytics.AgentAnalytics
import com.example.ez_capstone.trace.DecisionTraceBuilder
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EZmapMcpServer @Inject constructor(
    private val registry: McpToolRegistry,
    private val toolExecutor: ToolExecutor?,
    private val fallbackStrategy: FallbackStrategy? = null,
    private val agentAnalytics: AgentAnalytics? = null
) {
    private val gson = Gson()
    @Volatile private var lastAgentRun: AgentRun? = null

    fun lastAgentRunSnapshot(): AgentRun? = lastAgentRun

    fun handleMessage(rawJson: String): String? {
        val request = try {
            gson.fromJson(rawJson, McpRequest::class.java)
        } catch (e: JsonSyntaxException) {
            return gson.toJson(McpResponse(id = -1, error = McpError(-32700, "Parse error")))
        } catch (e: Exception) {
            return gson.toJson(McpResponse(id = -1, error = McpError(-32700, "Parse error")))
        }

        return when (request.method) {
            "initialize" -> gson.toJson(
                McpResponse(
                    id = request.id,
                    result = mapOf(
                        "protocolVersion" to "2024-11-05",
                        "capabilities" to mapOf("tools" to emptyMap<String, Any>()),
                        "serverInfo" to mapOf("name" to "EZmap", "version" to "1.0.0")
                    )
                )
            )
            "tools/list" -> {
                val tools = registry.listTools().map { t ->
                    mapOf("name" to t.name, "description" to t.description, "inputSchema" to t.inputSchema)
                }
                gson.toJson(McpResponse(id = request.id, result = mapOf("tools" to tools)))
            }
            "tools/call" -> {
                gson.toJson(
                    McpResponse(
                        id = request.id,
                        error = McpError(-32000, "Tool execution requires async MCP handler")
                    )
                )
            }
            else -> gson.toJson(
                McpResponse(id = request.id, error = McpError(-32601, "Method not found: ${request.method}"))
            )
        }
    }

    suspend fun handleMessageAsync(rawJson: String, context: AgentContext = AgentContext()): String? {
        val request = try {
            gson.fromJson(rawJson, McpRequest::class.java)
        } catch (e: JsonSyntaxException) {
            return gson.toJson(McpResponse(id = -1, error = McpError(-32700, "Parse error")))
        } catch (e: Exception) {
            return gson.toJson(McpResponse(id = -1, error = McpError(-32700, "Parse error")))
        }

        return when (request.method) {
            "tools/call" -> handleToolCall(request, context)
            else -> handleMessage(rawJson)
        }
    }

    private suspend fun handleToolCall(request: McpRequest, context: AgentContext): String {
        val toolName = request.params?.get("name")?.toString()?.takeIf { it.isNotBlank() }
            ?: return gson.toJson(McpResponse(id = request.id, error = McpError(-32602, "Missing tool name")))

        val exposedTool = registry.getTool(toolName)
        if (exposedTool == null) {
            val recorder = AgentRunRecorder.start("mcp:$toolName", context, AgentRunInputMode.MCP)
                .recordRoute("mcp_server_rejected")
                .recordSafetyDecision("mcp_server_exposure_denied", toolName)
            lastAgentRun = recorder.finish(
                AgentResponse(replyText = "MCP tool rejected", toolsUsed = emptyList())
            )
            return gson.toJson(
                McpResponse(id = request.id, error = McpError(-32602, "Tool not exposed: $toolName"))
            )
        }

        val executor = toolExecutor
        val fallback = fallbackStrategy
        val analytics = agentAnalytics
        if (executor == null || fallback == null || analytics == null) {
            return gson.toJson(
                McpResponse(
                    id = request.id,
                    result = mapOf(
                        "content" to listOf(
                            mapOf("type" to "text", "text" to "Tool execution requires async context. Use HTTP endpoint.")
                        )
                    )
                )
            )
        }

        val args = extractArguments(request.params)
        val plan = AgentPlan(
            source = AgentPlanSource.MCP_SERVER,
            sourceId = request.id.toString(),
            routeLabel = "mcp_server_tool_call",
            steps = listOf(AgentPlanStep(toolName, args))
        )
        val recorder = AgentRunRecorder.start("mcp:$toolName", context, AgentRunInputMode.MCP)
            .recordRoute(plan.routeLabel)
            .recordPlan(plan)
            .recordSafetyDecision("mcp_server_exposure_allowed", toolName)

        val toolResultStore = mutableMapOf<String, Any>()
        val toolsUsed = mutableListOf<String>()
        val succeededTools = mutableSetOf<String>()
        val responses = AgentToolExecutionHarness(executor, fallback, analytics).executePlan(
            plan = plan,
            context = context,
            traceBuilder = DecisionTraceBuilder(plan.id, "mcp:$toolName"),
            runRecorder = recorder,
            toolResultStore = toolResultStore,
            toolsUsed = toolsUsed,
            succeededTools = succeededTools
        )
        val response = responses
            .firstOrNull()
            ?.optJSONObject("functionResponse")
            ?.optJSONObject("response")
            ?: JSONObject().put("error", "missing_tool_response")

        lastAgentRun = recorder.finish(
            AgentResponse(
                replyText = response.optString("message", response.toString()),
                toolsUsed = toolsUsed
            )
        )

        if (response.has("error")) {
            return gson.toJson(
                McpResponse(id = request.id, error = McpError(-32000, response.optString("error")))
            )
        }

        return gson.toJson(
            McpResponse(
                id = request.id,
                result = mapOf(
                    "tool" to exposedTool.name,
                    "source" to "ezmap_local",
                    "content" to listOf(
                        mapOf("type" to "text", "text" to response.toString())
                    )
                )
            )
        )
    }

    private fun extractArguments(params: Map<String, Any>?): Map<String, Any?> {
        val rawArgs = params?.get("arguments") ?: params?.get("args") ?: emptyMap<String, Any?>()
        return mapValue(rawArgs)
    }

    private fun mapValue(value: Any?): Map<String, Any?> =
        when (value) {
            is Map<*, *> -> value.entries.associate { (key, entryValue) ->
                key.toString() to plainValue(entryValue)
            }
            else -> emptyMap()
        }

    private fun plainValue(value: Any?): Any? =
        when (value) {
            is Map<*, *> -> value.entries.associate { (key, entryValue) ->
                key.toString() to plainValue(entryValue)
            }
            is List<*> -> value.map(::plainValue)
            else -> value
        }
}
