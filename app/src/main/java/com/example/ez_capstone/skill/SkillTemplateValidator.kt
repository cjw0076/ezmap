package com.example.ez_capstone.skill

import com.example.ez_capstone.agent.ToolKind
import com.example.ez_capstone.agent.ToolRegistry
import com.example.ez_capstone.safety.ToolRiskTier

object SkillTemplateValidator {
    enum class ErrorCode {
        MissingSkillId,
        EmptyToolChain,
        MissingDrivingPolicy,
        MissingConfirmationPolicy,
        UnknownTool,
        MismatchedToolRiskTier
    }

    data class ValidationError(
        val code: ErrorCode,
        val message: String
    )

    data class Result(
        val errors: List<ValidationError>
    ) {
        val isValid: Boolean get() = errors.isEmpty()
        fun hasError(code: ErrorCode): Boolean = errors.any { it.code == code }
    }

    fun validate(template: SkillTemplate): Result {
        val errors = mutableListOf<ValidationError>()

        if (template.id.isBlank()) {
            errors += ValidationError(ErrorCode.MissingSkillId, "skill id is required")
        }

        if (template.toolChain.isEmpty()) {
            errors += ValidationError(ErrorCode.EmptyToolChain, "tool chain must not be empty")
        }

        val registry = ToolRegistry.builtIn
        val knownSpecs = template.toolChain.mapNotNull { call ->
            val toolName = call.tool.trim()
            val spec = registry.specOrNull(toolName)
            if (spec == null) {
                errors += ValidationError(ErrorCode.UnknownTool, "unknown tool in chain: $toolName")
            }
            spec
        }

        if (knownSpecs.isNotEmpty()) {
            val expectedRisk = knownSpecs
                .map { it.riskTier }
                .fold(ToolRiskTier.SAFE) { acc, risk -> ToolRiskTier.max(acc, risk) }
            if (template.maxToolRiskTier.trim().uppercase() != expectedRisk.code) {
                errors += ValidationError(
                    ErrorCode.MismatchedToolRiskTier,
                    "maxToolRiskTier must match registry-computed risk: ${expectedRisk.code}"
                )
            }
        }

        if (template.policy?.driving == null) {
            errors += ValidationError(ErrorCode.MissingDrivingPolicy, "driving policy is required")
        }

        if (knownSpecs.requiresConfirmation() && template.policy?.confirmation?.required != true) {
            errors += ValidationError(
                ErrorCode.MissingConfirmationPolicy,
                "stateful or effectful skills require confirmation policy"
            )
        }

        return Result(errors)
    }

    private fun List<com.example.ez_capstone.agent.ToolSpec>.requiresConfirmation(): Boolean =
        any { it.kind != ToolKind.READ }
}
