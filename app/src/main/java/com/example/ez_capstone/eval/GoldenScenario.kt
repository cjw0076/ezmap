package com.example.ez_capstone.eval

data class GoldenScenario(
    val id: String,
    val input: String,
    val expectedTools: List<String> = emptyList(),
    val requiredKeywords: List<String> = emptyList(),
    val forbiddenKeywords: List<String> = emptyList(),
    val description: String = ""
)

data class EvalResult(
    val scenarioId: String,
    val input: String,
    val actualResponse: String,
    val toolsUsed: List<String>,
    val score: Float,          // 0.0 ~ 1.0
    val passed: Boolean,
    val failureReasons: List<String>
)

data class EvalReport(
    val totalScenarios: Int,
    val passed: Int,
    val failed: Int,
    val overallScore: Float,
    val results: List<EvalResult>
)
