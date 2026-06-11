package com.example.ez_capstone.mcp

import com.example.ez_capstone.agent.AgentPlanSource
import com.example.ez_capstone.agent.AgentRunInputMode
import com.example.ez_capstone.agent.FallbackStrategy
import com.example.ez_capstone.agent.ToolExecutor
import com.example.ez_capstone.agent.models.AgentContext
import com.example.ez_capstone.analytics.AgentAnalytics
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class McpAgentBoundaryTest {

    private lateinit var toolExecutor: ToolExecutor
    private lateinit var fallbackStrategy: FallbackStrategy
    private lateinit var agentAnalytics: AgentAnalytics
    private lateinit var server: EZmapMcpServer

    @Before
    fun setUp() {
        toolExecutor = mockk()
        fallbackStrategy = mockk()
        agentAnalytics = mockk(relaxed = true)
        server = EZmapMcpServer(
            registry = McpToolRegistry(),
            toolExecutor = toolExecutor,
            fallbackStrategy = fallbackStrategy,
            agentAnalytics = agentAnalytics
        )

        every { toolExecutor.stripForGemini(any(), any()) } answers { secondArg() }
        coEvery { fallbackStrategy.executeWithRecovery(any(), any(), any(), any()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val executor = arg<suspend (String, Map<String, Any?>, AgentContext) -> JSONObject>(3)
            executor(arg(0), arg(1), arg(2))
        }
    }

    @Test
    fun `local mcp tools_call creates agent plan and executes through harness`() = runTest {
        coEvery { toolExecutor.execute("get_weather", any(), any()) } returns JSONObject()
            .put("weather", "rain")
            .put("message", "비")

        val response = server.handleMessageAsync(
            """{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"get_weather","arguments":{"location":"Seoul"}}}"""
        )

        assertNotNull(response)
        assertTrue(response!!.contains("ezmap_local"))
        assertTrue(response.contains("get_weather"))
        val run = server.lastAgentRunSnapshot()
        assertNotNull(run)
        assertEquals(AgentRunInputMode.MCP, run!!.inputMode)
        assertEquals("mcp_server_tool_call", run.routeLabel)
        assertEquals(AgentPlanSource.MCP_SERVER, run.plan!!.source)
        assertEquals(listOf("get_weather"), run.plan!!.stepToolNames)
        coVerify(exactly = 1) {
            toolExecutor.execute("get_weather", match { it["location"] == "Seoul" }, any())
        }
    }

    @Test
    fun `local mcp rejects effectful tool exposure before tool executor`() = runTest {
        val response = server.handleMessageAsync(
            """{"jsonrpc":"2.0","id":8,"method":"tools/call","params":{"name":"send_message","arguments":{"recipient":"010","message":"x"}}}"""
        )

        assertNotNull(response)
        assertTrue(response!!.contains("-32602"))
        assertTrue(response.contains("Tool not exposed"))
        val run = server.lastAgentRunSnapshot()
        assertNotNull(run)
        assertEquals("mcp_server_rejected", run!!.routeLabel)
        assertTrue(run.decisions.any { it.kind == "safety" && it.value == "mcp_server_exposure_denied" })
        coVerify(exactly = 0) { toolExecutor.execute(any(), any(), any()) }
    }
}
