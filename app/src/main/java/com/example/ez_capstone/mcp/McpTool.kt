package com.example.ez_capstone.mcp

data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any>
)

data class McpToolResult(
    val content: List<Map<String, Any>>,
    val isError: Boolean = false
)
