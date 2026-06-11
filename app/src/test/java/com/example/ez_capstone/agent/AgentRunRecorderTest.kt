package com.example.ez_capstone.agent

import com.example.ez_capstone.agent.models.AgentContext
import com.example.ez_capstone.agent.models.AgentResponse
import com.example.ez_capstone.agent.models.AgentDrivingState
import com.example.ez_capstone.trace.DecisionTraceBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRunRecorderTest {

    @Test
    fun `records normal turn envelope with bounded context and trace linkage`() {
        val context = AgentContext(
            locationX = 127.123456,
            locationY = 37.123456,
            drivingState = "driving",
            destX = 128.0,
            destY = 38.0
        )
        val trace = DecisionTraceBuilder("session-1", "강남역까지 길 알려줘")
            .apply {
                addStep(com.example.ez_capstone.trace.TraceStep(0, "tool_call", "get_directions", 12, "{}"))
            }
            .build(iterationCount = 1, safetyDecision = "Allow")

        val run = AgentRunRecorder
            .start("강남역까지 길 알려줘", context, AgentRunInputMode.VOICE)
            .recordRoute("gemini_function_loop")
            .recordToolCall("get_directions", mapOf("origin_x" to 1, "dest_x" to 2), 12, "{}")
            .recordSafetyDecision("Allow")
            .linkDecisionTrace(trace)
            .finish(AgentResponse(replyText = "경로 안내할게요.", uiAction = "show_route", toolsUsed = listOf("get_directions")))

        assertTrue(run.isComplete)
        assertEquals(AgentRunInputMode.VOICE, run.inputMode)
        assertEquals("gemini_function_loop", run.routeLabel)
        assertEquals(AgentDrivingState.NAVIGATING, run.contextSnapshot.drivingState)
        assertTrue(run.contextSnapshot.hasLocation)
        assertTrue(run.contextSnapshot.hasDestination)
        assertEquals("session-1", run.decisionTraceSessionId)
        assertEquals("show_route", run.finalResponse?.uiAction)
        assertEquals(listOf("dest_x", "origin_x"), run.toolCalls.single().argKeys)
    }

    @Test
    fun `records malformed tool and fallback without leaking full payloads`() {
        val longOutput = "x".repeat(500)

        val run = AgentRunRecorder
            .start(" ".repeat(10) + "민감한 요청".repeat(80), AgentContext(), AgentRunInputMode.TEXT)
            .recordRoute("gemini_function_loop")
            .recordToolCall(
                toolName = "unknown_tool",
                args = mapOf("message" to "full text should not be stored"),
                durationMs = -5,
                output = longOutput,
                error = "bad args ".repeat(80),
                fallbackFrom = "primary_tool"
            )
            .recordFallback("tool_error", "bad args ".repeat(80))
            .recordSafetyDecision("Warn", "GPS 없음 ".repeat(80))
            .finish(AgentResponse(replyText = "문제가 발생했어요.", uiAction = "none"))

        val call = run.toolCalls.single()
        assertEquals("error", call.status)
        assertEquals(0, call.durationMs)
        assertTrue((call.outputPreview?.length ?: 0) <= 200)
        assertTrue((call.errorPreview?.length ?: 0) <= 200)
        assertEquals(listOf("message"), call.argKeys)
        assertFalse(run.requestPreview.contains("\n"))
        assertTrue(run.decisions.single().reasonPreview!!.length <= 200)
    }

    @Test
    fun `incomplete trace id remains nullable before Room insert`() {
        val trace = DecisionTraceBuilder("session-2", "요청").build(iterationCount = 1)

        val run = AgentRunRecorder
            .start("요청", AgentContext())
            .recordRoute("test")
            .linkDecisionTrace(trace)
            .finish(AgentResponse(replyText = "응답"))

        assertNull(run.decisionTraceId)
        assertEquals("session-2", run.decisionTraceSessionId)
    }
}
