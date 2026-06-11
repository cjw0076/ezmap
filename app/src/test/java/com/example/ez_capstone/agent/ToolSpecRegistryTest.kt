package com.example.ez_capstone.agent

import com.example.ez_capstone.safety.SafetyPolicyEngine
import com.example.ez_capstone.safety.ToolRiskTier
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ToolSpecRegistryTest {

    @Test
    fun `built in registry generates Gemini and Live declaration views with stable public names`() {
        val registry = ToolRegistry.builtIn
        val declarationNames = registry.functionDeclarations().names()
        val liveNames = registry.liveApiFormat().getJSONArray("functionDeclarations").names()

        val geminiCritical = setOf("search_places", "get_directions", "send_message", "make_call")
        val liveCritical = setOf("search_places", "geocode", "get_weather", "get_directions", "get_transit_route")
        val liveBlocked = setOf("send_message", "make_call", "update_user_profile", "create_schedule")

        assertTrue(declarationNames.containsAll(geminiCritical + liveCritical + liveBlocked))
        assertTrue(liveNames.containsAll(liveCritical))
        assertTrue(liveBlocked.none { it in liveNames })
        assertEquals(ToolCatalog.KIND.keys, registry.names.toSet())
        assertFalse("registry should expose at least one generated declaration", declarationNames.isEmpty())
    }

    @Test
    fun `registry rejects duplicate or incomplete tool specs`() {
        val valid = validSpec("valid_tool")

        assertRejected("duplicate tool name") {
            ToolRegistry.from(listOf(valid, valid.copy(description = "second declaration")))
        }
        assertRejected("blank tool name") {
            ToolRegistry.from(listOf(valid.copy(name = " ")))
        }
        assertRejected("blank description") {
            ToolRegistry.from(listOf(valid.copy(description = " ")))
        }
        assertRejected("schema without object type") {
            ToolRegistry.from(listOf(valid.copy(parameters = JSONObject().put("properties", JSONObject()))))
        }
        assertRejected("schema without properties") {
            ToolRegistry.from(listOf(valid.copy(parameters = JSONObject().put("type", "object"))))
        }
        assertRejected("missing exposure metadata") {
            ToolRegistry.from(listOf(valid.copy(exposures = emptySet())))
        }
    }

    @Test
    fun `registry generated views honor exposure metadata and protect schema from mutation`() {
        val params = objectSchema()
        val geminiOnly = validSpec("gemini_only").copy(
            parameters = params,
            exposures = setOf(ToolExposure.GEMINI_FUNCTION)
        )
        val registry = ToolRegistry.from(listOf(geminiOnly))

        params.put("properties", JSONObject().put("mutated", JSONObject().put("type", "string")))
        geminiOnly.parameters.put("properties", JSONObject().put("also_mutated", JSONObject()))

        assertEquals(setOf("gemini_only"), registry.functionDeclarations().names())
        assertTrue(registry.liveApiFormat().getJSONArray("functionDeclarations").names().isEmpty())

        val generatedProperties = registry
            .functionDeclarations()
            .getJSONObject(0)
            .getJSONObject("parameters")
            .getJSONObject("properties")

        assertTrue(generatedProperties.has("query"))
        assertFalse(generatedProperties.has("mutated"))
        assertFalse(generatedProperties.has("also_mutated"))
    }

    @Test
    fun `live api exposure is an explicit read oriented allowlist`() {
        val registry = ToolRegistry.builtIn
        val liveSpecs = registry.allSpecs().filter { ToolExposure.LIVE_API in it.exposures }

        assertTrue(liveSpecs.map { it.name }.containsAll(setOf("search_places", "geocode", "get_weather")))
        for (spec in liveSpecs) {
            assertEquals("Live API must not expose write or sensitive tools: ${spec.name}", ToolKind.READ, spec.kind)
            assertFalse("Live API must not expose effectful tools: ${spec.name}", spec.riskTier == ToolRiskTier.EFFECTFUL)
        }

        for (blocked in setOf("send_message", "make_call", "update_user_profile", "create_schedule")) {
            assertFalse("$blocked must stay out of Live API", ToolExposure.LIVE_API in registry.requireSpec(blocked).exposures)
        }
    }

    @Test
    fun `registry preserves catalog kind and safety risk contracts for executable tools`() {
        val registry = ToolRegistry.builtIn

        for ((name, kind) in ToolCatalog.KIND) {
            val spec = registry.requireSpec(name)

            assertEquals("ToolCatalog kind mismatch for $name", kind, spec.kind)
            assertEquals(
                "Safety risk tier mismatch for $name",
                SafetyPolicyEngine.toolRiskTierOf(name),
                spec.riskTier
            )
        }

        assertEquals(ToolKind.SENSITIVE, registry.requireSpec("send_message").kind)
        assertEquals(ToolRiskTier.EFFECTFUL, registry.requireSpec("send_message").riskTier)
        assertEquals(ToolKind.SENSITIVE, registry.requireSpec("make_call").kind)
        assertEquals(ToolRiskTier.EFFECTFUL, registry.requireSpec("make_call").riskTier)
    }

    @Test
    fun `external exposure is explicit and default off for effectful tools`() {
        val registry = ToolRegistry.builtIn

        assertEquals(ToolExternalExposure.MCP_SERVER, registry.requireSpec("search_places").externalExposure)
        assertEquals(ToolExternalExposure.NONE, registry.requireSpec("send_message").externalExposure)
        assertEquals(ToolExternalExposure.NONE, registry.requireSpec("make_call").externalExposure)
        assertFalse(
            registry
                .specsForExternalExposure(ToolExternalExposure.MCP_SERVER)
                .any { it.riskTier == ToolRiskTier.EFFECTFUL }
        )
        assertTrue(
            "A2A exposure must stay default-off until an explicit allowlist is added",
            registry.specsForExternalExposure(ToolExternalExposure.A2A_AGENT).isEmpty()
        )
    }

    private fun validSpec(name: String): ToolSpec =
        ToolSpec(
            name = name,
            description = "Valid generated test tool",
            parameters = objectSchema(),
            keyGroup = "free",
            kind = ToolKind.READ,
            riskTier = ToolRiskTier.SAFE,
            exposures = setOf(ToolExposure.GEMINI_FUNCTION, ToolExposure.LIVE_API)
        )

    private fun objectSchema(): JSONObject =
        JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject().put(
                    "query",
                    JSONObject().put("type", "string").put("description", "검색어")
                )
            )

    private fun assertRejected(reason: String, block: () -> Unit) {
        try {
            block()
            fail("Expected ToolRegistry rejection: $reason")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    private fun org.json.JSONArray.names(): Set<String> =
        (0 until length()).map { getJSONObject(it).getString("name") }.toSet()
}
