package com.example.ez_capstone.mcp.client

/**
 * 외부 MCP 서버 연결 설정. 사용자가 등록(URL+토큰)하면 EZmap 에이전트가
 * 해당 서버의 도구를 동적으로 가져와 Gemini Function Calling에 병합한다.
 *
 * @param id 짧은 식별자 — 도구 이름 prefix("mcp_<id>_<tool>")에 사용. 영숫자/_만.
 * @param url Streamable HTTP(JSON-RPC) 엔드포인트.
 * @param authToken Bearer 토큰(선택). 비어있으면 헤더 미첨부.
 */
data class McpServerConfig(
    val id: String,
    val name: String,
    val url: String,
    val authToken: String = "",
    val enabled: Boolean = false
)
