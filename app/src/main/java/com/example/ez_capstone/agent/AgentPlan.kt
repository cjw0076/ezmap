package com.example.ez_capstone.agent

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class AgentPlanSource {
    GEMINI,
    LEARNED_SKILL,
    TEMPLATE,
    LIVE_API,
    MCP_SERVER,
    MCP_CLIENT,
    A2A
}

data class AgentPlanStep(
    val toolName: String,
    val args: Map<String, Any?>
)

data class AgentPlan(
    val id: String = UUID.randomUUID().toString(),
    val source: AgentPlanSource,
    val sourceId: String,
    val routeLabel: String,
    val steps: List<AgentPlanStep>
) {
    val toolNames: List<String> get() = steps.map { it.toolName }

    fun toFunctionCalls(): List<JSONObject> =
        steps.map { step ->
            JSONObject().put(
                "functionCall",
                JSONObject()
                    .put("name", step.toolName)
                    .put("args", mapToJson(step.args))
            )
        }

    companion object {
        private fun mapToJson(values: Map<String, Any?>): JSONObject =
            JSONObject().apply {
                values.forEach { (key, value) -> put(key, toJsonValue(value)) }
            }

        private fun listToJson(values: List<*>): JSONArray =
            JSONArray().apply {
                values.forEach { put(toJsonValue(it)) }
            }

        private fun toJsonValue(value: Any?): Any =
            when (value) {
                null -> JSONObject.NULL
                is Map<*, *> -> {
                    val stringKeyed = value.entries.associate { (k, v) -> k.toString() to v }
                    mapToJson(stringKeyed)
                }
                is List<*> -> listToJson(value)
                else -> value
            }
    }
}
