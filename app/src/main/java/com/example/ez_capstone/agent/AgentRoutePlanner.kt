package com.example.ez_capstone.agent

import android.util.Log
import com.example.ez_capstone.agent.models.AgentContext
import com.example.ez_capstone.config.ApiKeyProvider
import com.example.ez_capstone.mcp.client.McpToolGateway
import org.json.JSONArray

data class AgentTurnPlan(
    val routeLabel: String,
    val intent: IntentCategory,
    val prompt: String,
    val tools: JSONArray
)

class AgentRoutePlanner(
    private val systemPrompt: SystemPrompt,
    private val apiKeyProvider: ApiKeyProvider,
    private val mcpGateway: McpToolGateway
) {
    suspend fun planGeminiFunctionLoop(
        userText: String,
        context: AgentContext,
        sessionId: String
    ): AgentTurnPlan {
        val drivingState = context.drivingState
        val intent = IntentDetector.detect(userText, drivingState)
        val prompt = systemPrompt.build(context, sessionId, intent)
        val tools = ToolDeclarations.toJsonArray(apiKeyProvider, intent, drivingState)

        if (mcpGateway.hasServers()) {
            val mcpDecls = mcpGateway.cachedToolDeclarations()
            mcpDecls.forEach { tools.put(it) }
            if (mcpDecls.isNotEmpty()) {
                Log.d(TAG, "MCP 도구 ${mcpDecls.size}개 병합 -> 총 ${tools.length()}")
            }
        }

        return AgentTurnPlan(
            routeLabel = GEMINI_FUNCTION_LOOP,
            intent = intent,
            prompt = prompt,
            tools = tools
        )
    }

    fun forceModeForIteration(iteration: Int, intent: IntentCategory): String? =
        if (iteration == 0 && intent != IntentCategory.AUTO) "ANY" else null

    companion object {
        const val GEMINI_FUNCTION_LOOP = "gemini_function_loop"
        private const val TAG = "AgentRoutePlanner"
    }
}
