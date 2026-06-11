package com.example.ez_capstone.voice

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
class LiveFunctionCallBridgeHarnessTest {

    private lateinit var toolExecutor: ToolExecutor
    private lateinit var fallbackStrategy: FallbackStrategy
    private lateinit var agentAnalytics: AgentAnalytics
    private lateinit var bridge: LiveFunctionCallBridge

    @Before
    fun setUp() {
        toolExecutor = mockk()
        fallbackStrategy = mockk()
        agentAnalytics = mockk(relaxed = true)
        bridge = LiveFunctionCallBridge(
            toolExecutor = toolExecutor,
            liveVoiceSession = LiveVoiceSession(),
            fallbackStrategy = fallbackStrategy,
            agentAnalytics = agentAnalytics,
            permissionManager = mockk(relaxed = true)
        )

        every { toolExecutor.stripForGemini(any(), any()) } answers { secondArg() }
        coEvery { fallbackStrategy.executeWithRecovery(any(), any(), any(), any()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val executor = arg<suspend (String, Map<String, Any?>, AgentContext) -> JSONObject>(3)
            executor(arg(0), arg(1), arg(2))
        }
    }

    @Test
    fun `live tool call creates agent plan and executes through harness`() = runTest {
        coEvery { toolExecutor.execute("get_weather", any(), any()) } returns JSONObject()
            .put("weather", "sunny")
            .put("message", "맑음")

        val result = bridge.executeToolCall(
            LiveVoiceSession.SessionEvent.ToolCall(
                name = "get_weather",
                argsJson = """{"location":"Seoul"}""",
                callId = "live-call-1"
            )
        )

        assertEquals("sunny", result.getString("weather"))
        val run = bridge.lastAgentRunSnapshot()
        assertNotNull(run)
        assertEquals(AgentRunInputMode.VOICE, run!!.inputMode)
        assertEquals("live_api_tool_call", run.routeLabel)
        assertEquals(AgentPlanSource.LIVE_API, run.plan!!.source)
        assertEquals(listOf("get_weather"), run.plan!!.stepToolNames)
        assertTrue(run.toolCalls.any { it.toolName == "get_weather" && it.status == "ok" })
        coVerify(exactly = 1) {
            toolExecutor.execute("get_weather", match { it["location"] == "Seoul" }, any())
        }
    }

    @Test
    fun `unknown live tool is rejected before tool executor`() = runTest {
        val result = bridge.executeToolCall(
            LiveVoiceSession.SessionEvent.ToolCall(
                name = "unknown_live_tool",
                argsJson = "{}",
                callId = "live-call-2"
            )
        )

        assertEquals("live_tool_not_allowed", result.getString("error"))
        val run = bridge.lastAgentRunSnapshot()
        assertNotNull(run)
        assertEquals("live_api_rejected", run!!.routeLabel)
        assertTrue(run.decisions.any { it.kind == "safety" && it.value == "live_tool_rejected" })
        coVerify(exactly = 0) { toolExecutor.execute(any(), any(), any()) }
    }

    @Test
    fun `effectful and write live tools are rejected before tool executor`() = runTest {
        for (toolName in listOf("send_message", "make_call", "update_user_profile", "create_schedule")) {
            val result = bridge.executeToolCall(
                LiveVoiceSession.SessionEvent.ToolCall(
                    name = toolName,
                    argsJson = "{}",
                    callId = "live-call-$toolName"
                )
            )

            assertEquals("live_tool_not_allowed", result.getString("error"))
            val run = bridge.lastAgentRunSnapshot()
            assertNotNull(run)
            assertEquals("live_api_rejected", run!!.routeLabel)
            assertTrue(run.decisions.any { it.kind == "safety" && it.value == "live_tool_rejected" })
        }
        coVerify(exactly = 0) { toolExecutor.execute(any(), any(), any()) }
    }

    @Test
    fun `live tool response payload includes function name`() {
        val payload = LiveVoiceSession().buildToolResponsePayload(
            callId = "call-9",
            name = "get_weather",
            resultJson = """{"ok":true}"""
        )

        val response = payload
            .getJSONObject("toolResponse")
            .getJSONArray("functionResponses")
            .getJSONObject(0)

        assertEquals("call-9", response.getString("id"))
        assertEquals("get_weather", response.getString("name"))
        assertTrue(response.getJSONObject("response").getBoolean("ok"))
    }
}
