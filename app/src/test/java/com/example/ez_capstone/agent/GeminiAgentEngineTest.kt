package com.example.ez_capstone.agent

import com.example.ez_capstone.agent.models.AgentContext
import com.example.ez_capstone.analytics.AgentAnalytics
import com.example.ez_capstone.config.ApiKeyProvider
import com.example.ez_capstone.db.dao.ConversationDao
import com.example.ez_capstone.governance.PermissionManager
import com.example.ez_capstone.governance.PermissionResult
import com.example.ez_capstone.governance.AgentCapability
import com.example.ez_capstone.resilience.NetworkMonitor
import com.example.ez_capstone.safety.SafetyContext
import com.example.ez_capstone.safety.SafetyDecision
import com.example.ez_capstone.safety.SafetyPolicyEngine
import com.example.ez_capstone.offline.OnDeviceLlm
import com.example.ez_capstone.offline.OfflineIntent
import com.example.ez_capstone.predictive.PredictiveCache
import com.example.ez_capstone.security.SensitiveDataMasker
import com.example.ez_capstone.skill.SkillExecutor
import com.example.ez_capstone.skill.SkillLearner
import com.example.ez_capstone.skill.SkillMatchResult
import com.example.ez_capstone.skill.SkillMatcher
import com.example.ez_capstone.skill.SkillTemplate
import com.example.ez_capstone.skill.SlotDef
import com.example.ez_capstone.skill.ToolCall
import com.example.ez_capstone.trace.DecisionTraceDao
import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * GeminiAgentEngine 오프라인 가드 + 기본 응답 파이프라인 테스트.
 *
 * 13개 의존성 전체를 mockk으로 주입.
 * networkMonitor.isOnline을 false로 설정하면 즉시 오프라인 메시지 반환 검증.
 */
class GeminiAgentEngineTest {

    // ── 모든 의존성 mock ──
    private val apiClient: GeminiApiClient = mockk(relaxed = true)
    private val apiKeyProvider: ApiKeyProvider = mockk(relaxed = true)
    private val systemPrompt: SystemPrompt = mockk(relaxed = true)
    private val toolExecutor: ToolExecutor = mockk(relaxed = true)
    private val fallbackStrategy: FallbackStrategy = mockk(relaxed = true)
    private val safetyPolicyEngine: SafetyPolicyEngine = mockk(relaxed = true)
    private val permissionManager: PermissionManager = mockk(relaxed = true)
    private val networkMonitor: NetworkMonitor = mockk(relaxed = true)
    private val conversationDao: ConversationDao = mockk(relaxed = true)
    private val decisionTraceDao: DecisionTraceDao = mockk(relaxed = true)
    private val gson: Gson = Gson()
    private val sensitiveDataMasker: SensitiveDataMasker = mockk(relaxed = true)
    private val agentAnalytics: AgentAnalytics = mockk(relaxed = true)
    private val onDeviceLlm: OnDeviceLlm = mockk(relaxed = true)
    private val predictiveCache: PredictiveCache = mockk(relaxed = true)
    private val skillMatcher: SkillMatcher = mockk(relaxed = true)
    private val skillExecutor: SkillExecutor = mockk(relaxed = true)
    private val skillLearner: SkillLearner = mockk(relaxed = true)
    private val mcpGateway: com.example.ez_capstone.mcp.client.McpToolGateway = mockk(relaxed = true)

    private val onlineFlow = MutableStateFlow(true)
    private val offlineFlow = MutableStateFlow(false)

    private lateinit var engine: GeminiAgentEngine

    @Before
    fun setUp() {
        // 민감 데이터 마스커 — 입력을 그대로 반환
        every { sensitiveDataMasker.mask(any()) } answers { firstArg() }
        // 위치 권한은 기본 허용
        every { permissionManager.check(any()) } returns PermissionResult.Granted
        // SystemPrompt.build() — suspend 함수, 빈 문자열 반환
        coEvery { systemPrompt.build(any(), any()) } returns ""
        // ConversationDao — suspend insert, Long 반환
        coEvery { conversationDao.insertMessage(any()) } returns 1L
        // DecisionTraceDao — suspend insert, Long 반환
        coEvery { decisionTraceDao.insert(any()) } returns 1L
        // AgentAnalytics — 호출 기록 (no-op)
        every { agentAnalytics.recordConversation() } returns Unit
        every { agentAnalytics.recordSkillCall(any()) } returns Unit
        every { agentAnalytics.recordChainDepth(any()) } returns Unit
        // PredictiveCache — 테스트에서 캐시 히트 없음
        every { predictiveCache.getCachedPrediction(any()) } returns null
        coEvery { skillMatcher.tryMatch(any(), any()) } returns null
    }

    // ── Test 1: 오프라인 시 즉각 오프라인 메시지 반환 ──

    @Test
    fun `chat returns offline message when network is unavailable`() = runTest {
        every { networkMonitor.isOnline } returns offlineFlow

        // onDeviceLlm — 오프라인 시 연결 안내 메시지 반환
        every { onDeviceLlm.handleOffline(any(), any()) } returns
            com.example.ez_capstone.agent.models.AgentResponse(
                replyText = "현재 인터넷에 연결되어 있지 않습니다. 인터넷 연결 후 다시 말씀해주세요.",
                ttsText = "인터넷 연결이 필요합니다.",
                uiAction = "none"
            )

        engine = GeminiAgentEngine(
            apiClient = apiClient,
            apiKeyProvider = apiKeyProvider,
            systemPrompt = systemPrompt,
            toolExecutor = toolExecutor,
            fallbackStrategy = fallbackStrategy,
            safetyPolicyEngine = safetyPolicyEngine,
            permissionManager = permissionManager,
            networkMonitor = networkMonitor,
            conversationDao = conversationDao,
            decisionTraceDao = decisionTraceDao,
            gson = gson,
            sensitiveDataMasker = sensitiveDataMasker,
            agentAnalytics = agentAnalytics,
            onDeviceLlm = onDeviceLlm,
            predictiveCache = predictiveCache,
            skillMatcher = skillMatcher,
            skillExecutor = skillExecutor,
            skillLearner = skillLearner,
            mcpGateway = mcpGateway
        )

        val context = AgentContext()
        val response = engine.chat("강남역 가는 길", context)

        // 오프라인 응답 검증
        assertTrue(
            "오프라인 메시지에 '인터넷' 또는 '연결' 포함 기대",
            response.replyText.contains("인터넷") || response.replyText.contains("연결")
        )
        assertEquals("none", response.uiAction)

        val run = engine.lastAgentRunSnapshot()
        assertEquals(AgentRunInputMode.TEXT, run?.inputMode)
        assertEquals("offline_on_device_llm", run?.routeLabel)
        assertEquals(response.replyText, run?.finalResponse?.replyPreview)
        assertTrue(run?.isComplete == true)
    }

    // ── Test 2: 온라인 + API가 텍스트만 반환 → 비어있지 않은 응답 ──

    @Test
    fun `chat returns non-blank response when API returns plain text`() = runTest {
        every { networkMonitor.isOnline } returns onlineFlow

        // Gemini API — functionCall 없이 텍스트만 반환
        val textPart = JSONObject().put("text", """{"reply_text":"안녕하세요!","ui_action":"none"}""")
        val partsList = listOf(textPart)
        coEvery { apiClient.generateContent(any(), any(), any()) } returns Pair(partsList, "model")

        // SafetyPolicyEngine — 입력 응답을 그대로 통과
        every { safetyPolicyEngine.evaluate(any(), any()) } answers {
            val resp = firstArg<com.example.ez_capstone.agent.models.AgentResponse>()
            Pair(SafetyDecision.Allow, resp)
        }

        engine = GeminiAgentEngine(
            apiClient = apiClient,
            apiKeyProvider = apiKeyProvider,
            systemPrompt = systemPrompt,
            toolExecutor = toolExecutor,
            fallbackStrategy = fallbackStrategy,
            safetyPolicyEngine = safetyPolicyEngine,
            permissionManager = permissionManager,
            networkMonitor = networkMonitor,
            conversationDao = conversationDao,
            decisionTraceDao = decisionTraceDao,
            gson = gson,
            sensitiveDataMasker = sensitiveDataMasker,
            agentAnalytics = agentAnalytics,
            onDeviceLlm = onDeviceLlm,
            predictiveCache = predictiveCache,
            skillMatcher = skillMatcher,
            skillExecutor = skillExecutor,
            skillLearner = skillLearner,
            mcpGateway = mcpGateway
        )

        val context = AgentContext()
        val response = engine.chat("안녕", context)

        assertFalse("응답 replyText가 비어있지 않아야 함", response.replyText.isBlank())
        val run = engine.lastAgentRunSnapshot()
        assertEquals("gemini_function_loop", run?.routeLabel)
        assertEquals("none", run?.finalResponse?.uiAction)
        assertTrue(run?.decisionTraceSessionId?.isNotBlank() == true)
    }

    @Test
    fun `chat records tool turn through planner executor and response adapter seams`() = runTest {
        every { networkMonitor.isOnline } returns onlineFlow

        val functionCall = JSONObject().put(
            "functionCall",
            JSONObject()
                .put("name", "search_places")
                .put("args", JSONObject().put("query", "카페"))
        )
        val finalText = JSONObject().put("text", "근처 카페를 찾았어요.")
        coEvery { apiClient.generateContent(any(), any(), any(), any()) } returnsMany listOf(
            Pair(listOf(functionCall), "model"),
            Pair(listOf(finalText), "model")
        )
        coEvery { fallbackStrategy.executeWithRecovery(any(), any(), any(), any()) } returns
            JSONObject().put("places", JSONArray().put(JSONObject().put("name", "테스트 카페")))
        every { toolExecutor.stripForGemini(any(), any()) } answers { secondArg() }
        every { safetyPolicyEngine.evaluate(any(), any()) } answers {
            val resp = firstArg<com.example.ez_capstone.agent.models.AgentResponse>()
            Pair(SafetyDecision.Allow, resp)
        }

        engine = GeminiAgentEngine(
            apiClient = apiClient,
            apiKeyProvider = apiKeyProvider,
            systemPrompt = systemPrompt,
            toolExecutor = toolExecutor,
            fallbackStrategy = fallbackStrategy,
            safetyPolicyEngine = safetyPolicyEngine,
            permissionManager = permissionManager,
            networkMonitor = networkMonitor,
            conversationDao = conversationDao,
            decisionTraceDao = decisionTraceDao,
            gson = gson,
            sensitiveDataMasker = sensitiveDataMasker,
            agentAnalytics = agentAnalytics,
            onDeviceLlm = onDeviceLlm,
            predictiveCache = predictiveCache,
            skillMatcher = skillMatcher,
            skillExecutor = skillExecutor,
            skillLearner = skillLearner,
            mcpGateway = mcpGateway
        )

        val response = engine.chat(
            "근처 카페 찾아줘",
            AgentContext(locationX = 127.0, locationY = 37.0)
        )
        val run = engine.lastAgentRunSnapshot()

        assertEquals("show_places", response.uiAction)
        assertEquals("gemini_function_loop", run?.routeLabel)
        assertEquals("search_places", run?.toolCalls?.single()?.toolName)
        assertTrue(run?.observations?.any { it.kind == "planner" } == true)
    }

    @Test
    fun `chatWithMode records voice input mode without changing AgentResponse`() = runTest {
        every { networkMonitor.isOnline } returns offlineFlow
        every { onDeviceLlm.handleOffline(any(), any()) } returns
            com.example.ez_capstone.agent.models.AgentResponse(
                replyText = "인터넷 연결이 필요합니다.",
                uiAction = "none"
            )

        engine = GeminiAgentEngine(
            apiClient = apiClient,
            apiKeyProvider = apiKeyProvider,
            systemPrompt = systemPrompt,
            toolExecutor = toolExecutor,
            fallbackStrategy = fallbackStrategy,
            safetyPolicyEngine = safetyPolicyEngine,
            permissionManager = permissionManager,
            networkMonitor = networkMonitor,
            conversationDao = conversationDao,
            decisionTraceDao = decisionTraceDao,
            gson = gson,
            sensitiveDataMasker = sensitiveDataMasker,
            agentAnalytics = agentAnalytics,
            onDeviceLlm = onDeviceLlm,
            predictiveCache = predictiveCache,
            skillMatcher = skillMatcher,
            skillExecutor = skillExecutor,
            skillLearner = skillLearner,
            mcpGateway = mcpGateway
        )

        val response = engine.chatWithMode("길 안내", AgentContext(), AgentRunInputMode.VOICE)

        assertEquals("none", response.uiAction)
        assertEquals(AgentRunInputMode.VOICE, engine.lastAgentRunSnapshot()?.inputMode)
    }

    @Test
    fun `template l3 hit preserves promotion and avoids Gemini api`() = runTest {
        every { networkMonitor.isOnline } returns onlineFlow
        val template = SkillTemplate(
            id = "template-places",
            version = 1,
            intent = "PLACES",
            fingerprint = "{QUERY} 찾자",
            canonicalUtterance = "근처 카페 찾아줘",
            slotSchema = listOf(SlotDef("QUERY", "TEXT")),
            toolChain = listOf(ToolCall("search_places", """{"query":"${'$'}QUERY"}""")),
            replyTemplate = "{QUERY}를 찾았어요.",
            ttsTemplate = "{QUERY}를 찾았어요.",
            maxToolRiskTier = "SAFE",
            sensitivity = "PUBLIC",
            initialConfidence = 0.95f
        )
        val match = SkillMatchResult(
            source = SkillMatchResult.Source.TEMPLATE,
            template = template,
            slotValues = mapOf("QUERY" to "카페"),
            score = 0.95f,
            fingerprint = template.fingerprint
        )
        coEvery { skillMatcher.tryMatch(any(), any()) } returns match
        coEvery { skillExecutor.execute(match, any(), any()) } returns
            com.example.ez_capstone.agent.models.AgentResponse(
                replyText = "카페를 찾았어요.",
                uiAction = "show_places",
                toolsUsed = listOf("search_places")
            )
        coEvery { skillLearner.promoteFromTemplate(any(), any(), any()) } returns "promoted-1"

        engine = GeminiAgentEngine(
            apiClient = apiClient,
            apiKeyProvider = apiKeyProvider,
            systemPrompt = systemPrompt,
            toolExecutor = toolExecutor,
            fallbackStrategy = fallbackStrategy,
            safetyPolicyEngine = safetyPolicyEngine,
            permissionManager = permissionManager,
            networkMonitor = networkMonitor,
            conversationDao = conversationDao,
            decisionTraceDao = decisionTraceDao,
            gson = gson,
            sensitiveDataMasker = sensitiveDataMasker,
            agentAnalytics = agentAnalytics,
            onDeviceLlm = onDeviceLlm,
            predictiveCache = predictiveCache,
            skillMatcher = skillMatcher,
            skillExecutor = skillExecutor,
            skillLearner = skillLearner,
            mcpGateway = mcpGateway
        )

        val response = engine.chat("근처 카페 찾아줘", AgentContext())

        assertEquals("show_places", response.uiAction)
        assertEquals("l3_template", engine.lastAgentRunSnapshot()?.routeLabel)
        coVerify(exactly = 0) { apiClient.generateContent(any(), any(), any(), any()) }
        coVerify(timeout = 1000) { skillLearner.promoteFromTemplate("근처 카페 찾아줘", match, decisionTraceId = null) }
    }
}
