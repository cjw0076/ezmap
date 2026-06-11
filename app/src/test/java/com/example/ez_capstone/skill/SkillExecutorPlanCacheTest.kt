package com.example.ez_capstone.skill

import com.example.ez_capstone.agent.AgentRunInputMode
import com.example.ez_capstone.agent.AgentRunRecorder
import com.example.ez_capstone.agent.FallbackStrategy
import com.example.ez_capstone.agent.ToolExecutor
import com.example.ez_capstone.agent.models.AgentContext
import com.example.ez_capstone.agent.models.AgentResponse
import com.example.ez_capstone.analytics.AgentAnalytics
import com.example.ez_capstone.db.dao.ProfileDao
import com.example.ez_capstone.governance.PermissionManager
import com.example.ez_capstone.governance.PermissionResult
import com.example.ez_capstone.safety.SafetyPolicyEngine
import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SkillExecutorPlanCacheTest {
    private val toolExecutor: ToolExecutor = mockk(relaxed = true)
    private val learnedSkillDao: LearnedSkillDao = mockk(relaxed = true)
    private val profileDao: ProfileDao = mockk(relaxed = true)
    private val fallbackStrategy: FallbackStrategy = mockk(relaxed = true)
    private val safetyPolicyEngine = SafetyPolicyEngine()
    private val agentAnalytics: AgentAnalytics = mockk(relaxed = true)
    private val permissionManager: PermissionManager = mockk {
        every { check(any()) } returns PermissionResult.Granted
    }
    private lateinit var executor: SkillExecutor

    @Before
    fun setUp() {
        coEvery { profileDao.getProfile() } returns null
        every { agentAnalytics.recordSkillCall(any()) } returns Unit
        every { agentAnalytics.recordToolFailure(any(), any()) } returns Unit
        every { toolExecutor.stripForGemini(any(), any()) } answers { secondArg() }
        executor = SkillExecutor(
            toolExecutor = toolExecutor,
            learnedSkillDao = learnedSkillDao,
            profileDao = profileDao,
            fallbackStrategy = fallbackStrategy,
            safetyPolicyEngine = safetyPolicyEngine,
            agentAnalytics = agentAnalytics,
            permissionManager = permissionManager
        )
    }

    @Test
    fun `learned skill hit executes explicit agent plan through harness`() = runTest {
        val skill = learnedSkill("learned-1")
        val match = SkillMatchResult(
            source = SkillMatchResult.Source.LEARNED_SKILL,
            learnedSkill = skill,
            slotValues = mapOf("QUERY" to "카페"),
            score = 1.0f,
            fingerprint = skill.fingerprint
        )
        coEvery { fallbackStrategy.executeWithRecovery(any(), any(), any(), any()) } returns
            JSONObject().put("places", JSONArray().put(JSONObject().put("name", "테스트 카페")))
        coEvery { learnedSkillDao.recordUsage(any(), any(), any(), any()) } returns Unit
        coEvery { learnedSkillDao.getById(skill.id) } returns skill.copy(usageCount = 1, successCount = 1)
        coEvery { learnedSkillDao.updateConfidence(any(), any(), any()) } returns Unit

        val recorder = AgentRunRecorder.start("근처 카페", AgentContext(), AgentRunInputMode.TEXT)
        val response = executor.execute(match, AgentContext(locationX = 127.0, locationY = 37.0), recorder)
        val run = recorder.finish(response)

        assertEquals("show_places", response.uiAction)
        assertEquals(listOf("search_places"), response.toolsUsed)
        assertNotNull(run.plan)
        assertEquals("learned-1", run.plan?.sourceId)
        assertEquals(listOf("search_places"), run.plan?.stepToolNames)
        assertEquals("ok", run.toolCalls.single().status)
    }

    @Test
    fun `template hit executes explicit agent plan through harness`() = runTest {
        val template = template("template-1")
        val match = SkillMatchResult(
            source = SkillMatchResult.Source.TEMPLATE,
            template = template,
            slotValues = mapOf("QUERY" to "카페"),
            score = 0.95f,
            fingerprint = template.fingerprint
        )
        coEvery { fallbackStrategy.executeWithRecovery(any(), any(), any(), any()) } returns
            JSONObject().put("places", JSONArray().put(JSONObject().put("name", "템플릿 카페")))

        val recorder = AgentRunRecorder.start("근처 카페", AgentContext())
        val response = executor.execute(match, AgentContext(), recorder)
        val run = recorder.finish(response)

        assertEquals("show_places", response.uiAction)
        assertEquals("template-1", run.plan?.sourceId)
        assertEquals(listOf("search_places"), run.plan?.stepToolNames)
        assertTrue(run.observations.any { it.kind == "plan" })
    }

    @Test
    fun `effectful learned plan is rejected before tool execution and throws`() = runTest {
        val skill = learnedSkill(
            id = "unsafe-1",
            toolChain = JSONArray().put(
                JSONObject()
                    .put("tool", "send_message")
                    .put("params", JSONObject().put("recipient", "A").put("message", "B"))
            ).toString(),
            maxToolRiskTier = "EFFECTFUL"
        )
        val match = SkillMatchResult(
            source = SkillMatchResult.Source.LEARNED_SKILL,
            learnedSkill = skill,
            slotValues = emptyMap(),
            score = 1.0f,
            fingerprint = skill.fingerprint
        )

        val recorder = AgentRunRecorder.start("문자 보내", AgentContext())
        val exception = assertPlanCacheFallback {
            executor.execute(match, AgentContext(), recorder)
        }
        val run = recorder.finish(AgentResponse(replyText = "gemini fallback", uiAction = "none"))

        assertTrue(exception.message?.contains("plan_cache_rejected") == true)
        assertTrue(run.toolCalls.isEmpty())
        assertTrue(run.observations.any { it.kind == "fallback" && it.value == "plan_cache_rejected" })
    }

    @Test
    fun `stale risk tier is rejected before plan cache execution and throws`() = runTest {
        val statefulChain = JSONArray().put(
            JSONObject()
                .put("tool", "get_directions")
                .put(
                    "params",
                    JSONObject()
                        .put("origin_x", 127.0)
                        .put("origin_y", 37.0)
                        .put("dest_x", 128.0)
                        .put("dest_y", 38.0)
                )
        ).toString()
        val skill = learnedSkill(
            id = "stale-1",
            toolChain = statefulChain,
            maxToolRiskTier = "SAFE"
        )
        val match = SkillMatchResult(
            source = SkillMatchResult.Source.LEARNED_SKILL,
            learnedSkill = skill,
            slotValues = mapOf("QUERY" to "카페"),
            score = 1.0f,
            fingerprint = skill.fingerprint
        )

        val recorder = AgentRunRecorder.start("근처 카페", AgentContext())
        val exception = assertPlanCacheFallback {
            executor.execute(match, AgentContext(), recorder)
        }
        val run = recorder.finish(AgentResponse(replyText = "gemini fallback", uiAction = "none"))

        assertTrue(exception.message?.contains("plan_cache_rejected") == true)
        assertTrue(run.toolCalls.isEmpty())
        assertTrue(run.observations.any { it.preview?.contains("stale_maxToolRiskTier") == true })
    }

    @Test
    fun `failed learned plan throws instead of returning success reply template`() = runTest {
        val skill = learnedSkill("failed-1")
        val match = SkillMatchResult(
            source = SkillMatchResult.Source.LEARNED_SKILL,
            learnedSkill = skill,
            slotValues = mapOf("QUERY" to "카페"),
            score = 1.0f,
            fingerprint = skill.fingerprint
        )
        coEvery { fallbackStrategy.executeWithRecovery(any(), any(), any(), any()) } returns
            JSONObject().put("error", "network_unavailable")

        val recorder = AgentRunRecorder.start("근처 카페", AgentContext())
        val exception = assertPlanCacheFallback {
            executor.execute(match, AgentContext(), recorder)
        }
        val run = recorder.finish(AgentResponse(replyText = "gemini fallback", uiAction = "none"))

        assertTrue(exception.message?.contains("plan_cache_tool_error") == true)
        assertTrue(exception.message?.contains("network_unavailable") == true)
        assertTrue(run.observations.any { it.kind == "fallback" && it.value == "plan_cache_tool_error" })
    }

    private fun learnedSkill(
        id: String,
        toolChain: String = searchPlacesToolChain(),
        maxToolRiskTier: String = "SAFE"
    ): LearnedSkillEntity =
        LearnedSkillEntity(
            id = id,
            fingerprint = "{QUERY} 찾자",
            canonicalUtterance = "근처 카페 찾아줘",
            coreTokenSet = JSONArray().put("찾자").toString(),
            tokenCount = 2,
            slotSchema = JSONArray().put(JSONObject().put("name", "QUERY").put("type", "TEXT")).toString(),
            toolChain = toolChain,
            replyTemplate = "{QUERY}를 찾았어요.",
            ttsTemplate = "{QUERY}를 찾았어요.",
            confidence = 0.95f,
            maxToolRiskTier = maxToolRiskTier,
            confidenceThreshold = 0.75f
        )

    private fun template(id: String): SkillTemplate =
        SkillTemplate(
            id = id,
            version = 1,
            intent = "PLACES",
            fingerprint = "{QUERY} 찾자",
            canonicalUtterance = "근처 카페 찾아줘",
            slotSchema = listOf(SlotDef("QUERY", "TEXT")),
            toolChain = listOf(ToolCall("search_places", JSONObject().put("query", "\$QUERY").toString())),
            replyTemplate = "{QUERY}를 찾았어요.",
            ttsTemplate = "{QUERY}를 찾았어요.",
            maxToolRiskTier = "SAFE",
            sensitivity = "PUBLIC",
            initialConfidence = 0.95f
        )

    private fun searchPlacesToolChain(): String =
        JSONArray().put(
            JSONObject()
                .put("tool", "search_places")
                .put("params", JSONObject().put("query", "\$QUERY"))
        ).toString()

    private suspend fun assertPlanCacheFallback(
        block: suspend () -> Unit
    ): PlanCacheFallbackException {
        try {
            block()
        } catch (e: PlanCacheFallbackException) {
            return e
        }
        throw AssertionError("Expected PlanCacheFallbackException")
    }
}
