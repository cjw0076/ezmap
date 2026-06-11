package com.example.ez_capstone.mcp

import com.example.ez_capstone.agent.ToolExternalExposure
import com.example.ez_capstone.agent.ToolRegistry
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class McpToolRegistry @Inject constructor() {

    fun listTools(): List<McpTool> =
        ToolRegistry.builtIn
            .specsForExternalExposure(ToolExternalExposure.MCP_SERVER)
            .map { spec ->
                McpTool(
                    name = spec.name,
                    description = spec.description,
                    inputSchema = spec.parameters.toMap()
                )
            }

    fun getTool(name: String): McpTool? = listTools().find { it.name == name }

    private fun JSONObject.toMap(): Map<String, Any> =
        keys().asSequence().associateWith { key -> plainValue(get(key)) }

    private fun JSONArray.toList(): List<Any> =
        (0 until length()).map { index -> plainValue(get(index)) }

    private fun plainValue(value: Any): Any = when (value) {
        is JSONObject -> value.toMap()
        is JSONArray -> value.toList()
        JSONObject.NULL -> ""
        else -> value
    }
}
