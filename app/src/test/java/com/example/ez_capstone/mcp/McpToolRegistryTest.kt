package com.example.ez_capstone.mcp

import com.example.ez_capstone.agent.ToolExternalExposure
import com.example.ez_capstone.agent.ToolRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class McpToolRegistryTest {

    private lateinit var registry: McpToolRegistry

    @Before
    fun setUp() {
        registry = McpToolRegistry()
    }

    @Test
    fun `listTools returns at least 10 tools`() {
        val tools = registry.listTools()
        assertTrue("Expected ≥10 tools, got ${tools.size}", tools.size >= 10)
    }

    @Test
    fun `listTools contains search_places`() {
        assertTrue(registry.listTools().any { it.name == "search_places" })
    }

    @Test
    fun `listTools contains get_directions`() {
        assertTrue(registry.listTools().any { it.name == "get_directions" })
    }

    @Test
    fun `listTools contains get_weather`() {
        assertTrue(registry.listTools().any { it.name == "get_weather" })
    }

    @Test
    fun `each tool has non-empty description`() {
        registry.listTools().forEach { tool ->
            assertTrue("${tool.name} has empty description", tool.description.isNotBlank())
        }
    }

    @Test
    fun `published tools are derived from ToolSpec external exposure metadata`() {
        val expected = ToolRegistry.builtIn
            .specsForExternalExposure(ToolExternalExposure.MCP_SERVER)
            .map { it.name }
            .toSet()

        assertEquals(expected, registry.listTools().map { it.name }.toSet())
    }

    @Test
    fun `effectful tools are not externally exposed by default`() {
        val names = registry.listTools().map { it.name }.toSet()

        assertFalse("send_message must remain default-off for MCP server exposure", "send_message" in names)
        assertFalse("make_call must remain default-off for MCP server exposure", "make_call" in names)
    }
}
