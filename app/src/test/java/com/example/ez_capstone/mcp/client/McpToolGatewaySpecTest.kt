package com.example.ez_capstone.mcp.client

import com.example.ez_capstone.agent.ToolExposure
import com.example.ez_capstone.agent.ToolExternalExposure
import com.example.ez_capstone.agent.ToolKind
import com.example.ez_capstone.safety.ToolRiskTier
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpToolGatewaySpecTest {

    @Test
    fun `new mcp server configs default disabled pending review`() {
        val config = McpServerConfig(id = "docs", name = "Docs", url = "https://example.com/mcp")

        assertFalse(config.enabled)
    }

    @Test
    fun `imported mcp read tool is classified SAFE and never re exposed`() {
        val declaration = JSONObject()
            .put("name", "mcp_docs_search")
            .put("description", "Search remote docs")
            .put(
                "parameters",
                JSONObject()
                    .put("type", "object")
                    .put("properties", JSONObject().put("query", JSONObject().put("type", "string")))
            )

        val spec = McpToolGateway.importedToolSpec("mcp_docs_search", declaration)

        assertEquals("mcp_docs_search", spec.name)
        // 읽기성 도구 → 정확 분류(과거엔 일괄 STATEFUL/WRITE였음).
        assertEquals(ToolKind.READ, spec.kind)
        assertEquals(ToolRiskTier.SAFE, spec.riskTier)
        assertTrue(ToolExposure.GEMINI_FUNCTION in spec.exposures)
        assertTrue(ToolExposure.EXECUTOR in spec.exposures)
        assertFalse(ToolExposure.LIVE_API in spec.exposures)
        assertEquals(ToolExternalExposure.NONE, spec.externalExposure)
    }

    @Test
    fun `imported mcp effectful tool is classified EFFECTFUL`() {
        val declaration = JSONObject()
            .put("name", "mcp_mail_send_email")
            .put("description", "Send an email to a recipient")
            .put("parameters", JSONObject().put("type", "object").put("properties", JSONObject()))

        val spec = McpToolGateway.importedToolSpec("mcp_mail_send_email", declaration)

        assertEquals(ToolKind.SENSITIVE, spec.kind)
        assertEquals(ToolRiskTier.EFFECTFUL, spec.riskTier)
        // 위험해도 외부 재노출은 여전히 기본 차단.
        assertEquals(ToolExternalExposure.NONE, spec.externalExposure)
        assertEquals(ToolRiskTier.EFFECTFUL, McpToolGateway.classifyExternalRisk("mcp_mail_send_email", "Send an email"))
        assertEquals(ToolRiskTier.SAFE, McpToolGateway.classifyExternalRisk("mcp_docs_search", "Search docs"))
    }

    @Test
    fun `prefixed mcp names stay bounded and avoid truncated collisions`() {
        val serverId = "docs"
        val sharedLongPrefix = "tool_" + "a".repeat(80)
        val first = McpToolGateway.prefixedToolName(serverId, "${sharedLongPrefix}_alpha")
        val second = McpToolGateway.prefixedToolName(serverId, "${sharedLongPrefix}_bravo", setOf(first))

        assertEquals(McpToolGateway.MAX_IMPORTED_TOOL_NAME_LENGTH, first.length)
        assertTrue(second.length <= McpToolGateway.MAX_IMPORTED_TOOL_NAME_LENGTH)
        assertTrue(first.matches(Regex("[a-zA-Z0-9_]+")))
        assertTrue(second.matches(Regex("[a-zA-Z0-9_]+")))
        assertNotEquals(first, second)
        assertEquals(second, McpToolGateway.prefixedToolName(serverId, "${sharedLongPrefix}_bravo", setOf(first)))
    }
}
