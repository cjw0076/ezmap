package com.example.ez_capstone.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePipelinePolicyTest {

    @Test
    fun `live pipeline is selected only when enabled and ready`() {
        assertTrue(
            VoicePipelinePolicy.shouldUseLiveApi(
                enabled = true,
                networkAvailable = true,
                apiKey = "gemini-key",
                liveSessionAvailable = true
            )
        )
    }

    @Test
    fun `legacy pipeline is selected when live mode is disabled`() {
        assertFalse(
            VoicePipelinePolicy.shouldUseLiveApi(
                enabled = false,
                networkAvailable = true,
                apiKey = "gemini-key",
                liveSessionAvailable = true
            )
        )
    }

    @Test
    fun `legacy pipeline is selected when live prerequisites are missing`() {
        assertFalse(VoicePipelinePolicy.shouldUseLiveApi(true, false, "gemini-key", true))
        assertFalse(VoicePipelinePolicy.shouldUseLiveApi(true, true, "", true))
        assertFalse(VoicePipelinePolicy.shouldUseLiveApi(true, true, "gemini-key", false))
    }
}
