package com.example.ez_capstone.mcp

// JSON-RPC 2.0 request
data class McpRequest(
    val jsonrpc: String = "2.0",
    val id: Int,
    val method: String,
    val params: Map<String, Any>? = null
)

// JSON-RPC 2.0 response
data class McpResponse(
    val jsonrpc: String = "2.0",
    val id: Int,
    val result: Any? = null,
    val error: McpError? = null
)

data class McpError(
    val code: Int,
    val message: String
)
