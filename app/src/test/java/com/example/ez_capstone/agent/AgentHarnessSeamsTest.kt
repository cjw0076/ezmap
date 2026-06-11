package com.example.ez_capstone.agent

import com.example.ez_capstone.agent.models.AgentContext
import com.example.ez_capstone.agent.models.AgentResponse
import com.example.ez_capstone.analytics.AgentAnalytics
import com.example.ez_capstone.config.ApiKeyProvider
import com.example.ez_capstone.mcp.client.McpToolGateway
import com.example.ez_capstone.trace.DecisionTraceBuilder
import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentHarnessSeamsTest {
    @Test
    fun `response adapter recovers route UI action and departure intent`() {
        val routes = JSONArray().put(
            JSONObject()
                .put("distance_m", 1200)
                .put("duration_s", 600)
                .put("summary", JSONObject().put("fare", 0))
        )
        val response = AgentResponseAdapter().adapt(
            rawText = "경로를 찾았어요.",
            userText = "바로 출발하자",
            toolStore = mapOf("directions" to JSONObject().put("routes", routes).toString())
        )

        assertEquals("start_navigation", response.uiAction)
        assertTrue(response.uiData["routes"] is List<*>)
        assertTrue((response.uiData["routes"] as List<*>).isNotEmpty())
    }

    @Test
    fun `route planner creates explicit Gemini plan and force mode`() = runTest {
        val apiKeyProvider: ApiKeyProvider = mockk(relaxed = true)
        val systemPrompt: SystemPrompt = mockk(relaxed = true)
        val mcpGateway: McpToolGateway = mockk(relaxed = true)
        coEvery { systemPrompt.build(any(), any(), any()) } returns "prompt"
        every { mcpGateway.hasServers() } returns false

        val planner = AgentRoutePlanner(systemPrompt, apiKeyProvider, mcpGateway)
        val plan = planner.planGeminiFunctionLoop(
            userText = "강남역까지 길 알려줘",
            context = AgentContext(),
            sessionId = "session-1"
        )

        assertEquals(AgentRoutePlanner.GEMINI_FUNCTION_LOOP, plan.routeLabel)
        assertEquals("prompt", plan.prompt)
        assertEquals("ANY", planner.forceModeForIteration(0, plan.intent))
        assertEquals(null, planner.forceModeForIteration(1, plan.intent))
    }

    @Test
    fun `tool execution harness merges concurrent tool calls sequentially`() = runTest {
        val toolExecutor: ToolExecutor = mockk(relaxed = true)
        val fallbackStrategy: FallbackStrategy = mockk(relaxed = true)
        val agentAnalytics: AgentAnalytics = mockk(relaxed = true)
        val places = JSONArray().put(JSONObject().put("name", "테스트 카페"))
        val placesResult = JSONObject().put("places", places)
        val weatherResult = JSONObject().put("summary", "맑음")
        coEvery { fallbackStrategy.executeWithRecovery(any(), any(), any(), any()) } coAnswers {
            when (arg<String>(0)) {
                "search_places" -> placesResult
                "get_weather" -> weatherResult
                else -> JSONObject().put("error", "unexpected tool")
            }
        }
        every { toolExecutor.stripForGemini(any(), any()) } answers { secondArg() }
        every { agentAnalytics.recordSkillCall(any()) } returns Unit

        val harness = AgentToolExecutionHarness(toolExecutor, fallbackStrategy, agentAnalytics)
        val recorder = AgentRunRecorder.start("근처 카페", AgentContext())
        val traceBuilder = DecisionTraceBuilder("session-1", "근처 카페")
        val toolStore = mutableMapOf<String, Any>()
        val toolsUsed = mutableListOf<String>()
        val succeededTools = mutableSetOf<String>()
        val placeCall = JSONObject().put(
            "functionCall",
            JSONObject()
                .put("name", "search_places")
                .put("args", JSONObject().put("query", "카페"))
        )
        val weatherCall = JSONObject().put(
            "functionCall",
            JSONObject()
                .put("name", "get_weather")
                .put("args", JSONObject().put("location", "서울"))
        )

        val responses = harness.executeAll(
            functionCalls = listOf(placeCall, weatherCall),
            iteration = 0,
            context = AgentContext(),
            traceBuilder = traceBuilder,
            runRecorder = recorder,
            toolResultStore = toolStore,
            toolsUsed = toolsUsed,
            succeededTools = succeededTools
        )
        val run = recorder.finish(AgentResponse(replyText = "완료"))

        assertEquals(listOf("search_places", "get_weather"), toolsUsed)
        assertEquals(setOf("search_places", "get_weather"), succeededTools)
        assertTrue(toolStore.containsKey("places"))
        assertTrue(toolStore.containsKey("weather"))
        assertEquals(listOf("search_places", "get_weather"), run.toolCalls.map { it.toolName })
        assertEquals(listOf("ok", "ok"), run.toolCalls.map { it.status })
        assertEquals("search_places", responses[0].getJSONObject("functionResponse").getString("name"))
        assertEquals("get_weather", responses[1].getJSONObject("functionResponse").getString("name"))
    }

    @Test
    fun `location dependent tool is blocked when location permission denied`() = runTest {
        val toolExecutor: ToolExecutor = mockk(relaxed = true)
        val fallbackStrategy: FallbackStrategy = mockk(relaxed = true)
        val agentAnalytics: AgentAnalytics = mockk(relaxed = true)
        every { toolExecutor.stripForGemini(any(), any()) } answers { secondArg() }

        // 위치 권한 거부 람다 주입 + 컨텍스트에 위치 있음 → 위치 의존 도구는 실행 전 차단.
        val harness = AgentToolExecutionHarness(
            toolExecutor, fallbackStrategy, agentAnalytics,
            locationPermissionGranted = { false }
        )
        val located = AgentContext(locationX = 127.0, locationY = 37.5)
        val recorder = AgentRunRecorder.start("근처 카페", located)
        val call = JSONObject().put(
            "functionCall",
            JSONObject().put("name", "search_places").put("args", JSONObject().put("query", "카페"))
        )
        val responses = harness.executeAll(
            functionCalls = listOf(call),
            iteration = 0,
            context = located,
            traceBuilder = DecisionTraceBuilder("perm", "근처 카페"),
            runRecorder = recorder,
            toolResultStore = mutableMapOf(),
            toolsUsed = mutableListOf(),
            succeededTools = mutableSetOf()
        )
        val response = responses.single().getJSONObject("functionResponse").getJSONObject("response")
        assertEquals("location_permission_denied", response.getString("error"))
        // 실제 도구 실행은 일어나지 않아야 한다(게이트가 실행 전 차단).
        coVerify(exactly = 0) { fallbackStrategy.executeWithRecovery(any(), any(), any(), any()) }
    }

    @Test
    fun `tool execution harness captures unknown tool error without crashing`() = runTest {
        val toolExecutor: ToolExecutor = mockk(relaxed = true)
        val fallbackStrategy: FallbackStrategy = mockk(relaxed = true)
        val agentAnalytics: AgentAnalytics = mockk(relaxed = true)
        val error = JSONObject()
            .put("error", "알 수 없는 tool: unknown_tool")
            .put("error_kind", "UNCLASSIFIED")
        coEvery { fallbackStrategy.executeWithRecovery(any(), any(), any(), any()) } returns error
        every { toolExecutor.stripForGemini(any(), any()) } answers { secondArg() }
        every { agentAnalytics.recordSkillCall(any()) } returns Unit
        every { agentAnalytics.recordToolFailure(any(), any()) } returns Unit

        val recorder = AgentRunRecorder.start("이상한 도구", AgentContext())
        val responses = AgentToolExecutionHarness(toolExecutor, fallbackStrategy, agentAnalytics).executeAll(
            functionCalls = listOf(
                JSONObject().put(
                    "functionCall",
                    JSONObject().put("name", "unknown_tool").put("args", JSONObject())
                )
            ),
            iteration = 0,
            context = AgentContext(),
            traceBuilder = DecisionTraceBuilder("session-2", "이상한 도구"),
            runRecorder = recorder,
            toolResultStore = mutableMapOf(),
            toolsUsed = mutableListOf(),
            succeededTools = mutableSetOf()
        )
        val run = recorder.finish(AgentResponse(replyText = "문제가 발생했어요."))

        assertEquals("error", run.toolCalls.single().status)
        assertTrue(responses.single().getJSONObject("functionResponse").getJSONObject("response").has("error"))
    }
}
