package com.example.ez_capstone.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SttRoutingPolicyTest {

    @Test
    fun `routes to google stt when network and recognizer are available`() {
        assertEquals(
            SttRoutingPolicy.CommandRoute.GoogleStt,
            SttRoutingPolicy.selectCommandRoute(
                networkAvailable = true,
                speechRecognizerAvailable = true
            )
        )
    }

    @Test
    fun `routes to vosk command fallback when offline or recognizer unavailable`() {
        assertEquals(
            SttRoutingPolicy.CommandRoute.VoskCommand,
            SttRoutingPolicy.selectCommandRoute(
                networkAvailable = false,
                speechRecognizerAvailable = true
            )
        )
        assertEquals(
            SttRoutingPolicy.CommandRoute.VoskCommand,
            SttRoutingPolicy.selectCommandRoute(
                networkAvailable = true,
                speechRecognizerAvailable = false
            )
        )
    }

    @Test
    fun `retries no match and speech timeout only up to max retries`() {
        assertEquals(
            SttRoutingPolicy.ErrorDecision.RetryGoogle(delayMs = 200L),
            SttRoutingPolicy.handleGoogleError(SttRoutingPolicy.GoogleSttError.NoMatch, retryCount = 0)
        )
        assertEquals(
            SttRoutingPolicy.ErrorDecision.RetryGoogle(delayMs = 200L),
            SttRoutingPolicy.handleGoogleError(SttRoutingPolicy.GoogleSttError.SpeechTimeout, retryCount = 1)
        )
        assertEquals(
            SttRoutingPolicy.ErrorDecision.ReturnToIdle,
            SttRoutingPolicy.handleGoogleError(SttRoutingPolicy.GoogleSttError.NoMatch, retryCount = 2)
        )
    }

    @Test
    fun `audio errors retry with focus delay and network errors fall back to vosk`() {
        assertEquals(
            SttRoutingPolicy.ErrorDecision.RetryGoogle(delayMs = 800L, requestAudioFocus = true),
            SttRoutingPolicy.handleGoogleError(SttRoutingPolicy.GoogleSttError.Audio, retryCount = 0)
        )
        assertEquals(
            SttRoutingPolicy.ErrorDecision.FallbackToVosk,
            SttRoutingPolicy.handleGoogleError(SttRoutingPolicy.GoogleSttError.Network, retryCount = 0)
        )
        assertEquals(
            SttRoutingPolicy.ErrorDecision.FallbackToVosk,
            SttRoutingPolicy.handleGoogleError(SttRoutingPolicy.GoogleSttError.NetworkTimeout, retryCount = 0)
        )
    }

    @Test
    fun `echo guard blocks wake handling only inside guard window`() {
        assertTrue(
            SttRoutingPolicy.shouldIgnoreWakeWord(
                nowMs = 1_500L,
                lastTtsEndMs = 800L,
                echoGuardMs = 1_000L
            )
        )
        assertFalse(
            SttRoutingPolicy.shouldIgnoreWakeWord(
                nowMs = 1_800L,
                lastTtsEndMs = 800L,
                echoGuardMs = 1_000L
            )
        )
    }
}
