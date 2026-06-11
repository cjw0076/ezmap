package com.example.ez_capstone.skill

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillTemplateValidatorTest {

    @Test
    fun `rejects missing skill id`() {
        val result = SkillTemplateValidator.validate(validTemplate().copy(id = ""))

        assertFalse(result.isValid)
        assertTrue(result.hasError(SkillTemplateValidator.ErrorCode.MissingSkillId))
    }

    @Test
    fun `rejects empty tool chain`() {
        val result = SkillTemplateValidator.validate(validTemplate().copy(toolChain = emptyList()))

        assertFalse(result.isValid)
        assertTrue(result.hasError(SkillTemplateValidator.ErrorCode.EmptyToolChain))
    }

    @Test
    fun `rejects missing driving policy`() {
        val result = SkillTemplateValidator.validate(validTemplate().copy(policy = SkillPolicy()))

        assertFalse(result.isValid)
        assertTrue(result.hasError(SkillTemplateValidator.ErrorCode.MissingDrivingPolicy))
    }

    @Test
    fun `rejects action skills without confirmation policy`() {
        val result = SkillTemplateValidator.validate(
            validTemplate().copy(
                toolChain = listOf(ToolCall("save_note", """{"content":"memo"}""")),
                policy = SkillPolicy(
                    driving = DrivingPolicy(allowedWhileDriving = false),
                    confirmation = null
                )
            )
        )

        assertFalse(result.isValid)
        assertTrue(result.hasError(SkillTemplateValidator.ErrorCode.MissingConfirmationPolicy))
    }

    @Test
    fun `rejects unknown tools in tool chain`() {
        val result = SkillTemplateValidator.validate(
            validTemplate().copy(
                toolChain = listOf(ToolCall("nonexistent_tool", "{}"))
            )
        )

        assertFalse(result.isValid)
        assertTrue(result.hasError(SkillTemplateValidator.ErrorCode.UnknownTool))
    }

    @Test
    fun `rejects stale template risk tier that disagrees with registry`() {
        val result = SkillTemplateValidator.validate(
            validTemplate().copy(maxToolRiskTier = "SAFE")
        )

        assertFalse(result.isValid)
        assertTrue(result.hasError(SkillTemplateValidator.ErrorCode.MismatchedToolRiskTier))
    }

    @Test
    fun `accepts a valid route with weather template`() {
        val result = SkillTemplateValidator.validate(validTemplate())

        assertTrue(result.errors.toString(), result.isValid)
    }

    private fun validTemplate(): SkillTemplate = SkillTemplate(
        id = "route_with_weather",
        version = 1,
        intent = "ROUTE_WITH_WEATHER",
        fingerprint = "{DEST} 날씨 경로",
        canonicalUtterance = "서울역까지 날씨 보고 길 알려줘",
        slotSchema = listOf(SlotDef("DEST", "PLACE_REF")),
        toolChain = listOf(
            ToolCall("get_weather_kma", """{"x":"${'$'}GPS.current.x","y":"${'$'}GPS.current.y"}"""),
            ToolCall("get_directions", """{"origin":"${'$'}GPS.current","destination":"${'$'}DEST"}""")
        ),
        replyTemplate = "{DEST}까지 날씨를 반영해 경로를 안내할게요.",
        ttsTemplate = "{DEST}까지 경로를 안내합니다.",
        maxToolRiskTier = "STATEFUL",
        sensitivity = "PERSONAL",
        initialConfidence = 0.82f,
        policy = SkillPolicy(
            driving = DrivingPolicy(allowedWhileDriving = true),
            confirmation = null,
            fallback = FallbackPolicy("경로와 날씨를 함께 확인하지 못했어요.")
        )
    )
}
