package com.example.ez_capstone.eval

import android.content.Context
import android.util.Log
import com.example.ez_capstone.agent.GeminiAgentEngine
import com.example.ez_capstone.agent.models.AgentContext
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentEvaluator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val agentEngine: GeminiAgentEngine
) {
    companion object {
        private const val TAG = "AgentEvaluator"
    }

    fun loadScenarios(): List<GoldenScenario> {
        return try {
            val json = context.assets.open("golden_scenarios.json")
                .bufferedReader().readText()
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                GoldenScenario(
                    id = obj.getString("id"),
                    input = obj.getString("input"),
                    expectedTools = obj.optJSONArray("expectedTools")
                        ?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                    requiredKeywords = obj.optJSONArray("requiredKeywords")
                        ?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                    forbiddenKeywords = obj.optJSONArray("forbiddenKeywords")
                        ?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                    description = obj.optString("description", "")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load scenarios: ${e.message}")
            emptyList()
        }
    }

    suspend fun evaluate(scenario: GoldenScenario): EvalResult {
        val agentContext = AgentContext()
        val response = try {
            agentEngine.chat(scenario.input, agentContext)
        } catch (e: Exception) {
            return EvalResult(
                scenarioId = scenario.id,
                input = scenario.input,
                actualResponse = "ERROR: ${e.message}",
                toolsUsed = emptyList(),
                score = 0f,
                passed = false,
                failureReasons = listOf("Exception: ${e.message}")
            )
        }

        val failures = mutableListOf<String>()
        var score = 1.0f

        // Required keyword checks
        scenario.requiredKeywords.forEach { kw ->
            if (!response.replyText.contains(kw, ignoreCase = true)) {
                failures.add("Missing keyword: $kw")
                score -= 0.2f
            }
        }

        // Forbidden keyword checks
        scenario.forbiddenKeywords.forEach { kw ->
            if (response.replyText.contains(kw, ignoreCase = true)) {
                failures.add("Forbidden keyword present: $kw")
                score -= 0.3f
            }
        }

        val passed = failures.isEmpty() && score > 0.5f
        return EvalResult(
            scenarioId = scenario.id,
            input = scenario.input,
            actualResponse = response.replyText,
            toolsUsed = emptyList(),
            score = score.coerceIn(0f, 1f),
            passed = passed,
            failureReasons = failures
        )
    }

    suspend fun runAll(): EvalReport {
        val scenarios = loadScenarios()
        val results = scenarios.map { evaluate(it) }
        val passed = results.count { it.passed }
        return EvalReport(
            totalScenarios = results.size,
            passed = passed,
            failed = results.size - passed,
            overallScore = if (results.isEmpty()) 0f else results.map { it.score }.average().toFloat(),
            results = results
        )
    }
}
