package com.example.ez_capstone.voice

object SttRoutingPolicy {
    const val DEFAULT_MAX_RETRIES = 2
    const val DEFAULT_ECHO_GUARD_MS = 1_000L

    enum class CommandRoute {
        GoogleStt,
        VoskCommand
    }

    enum class GoogleSttError {
        NoMatch,
        SpeechTimeout,
        Audio,
        Client,
        Network,
        NetworkTimeout,
        Other
    }

    sealed class ErrorDecision {
        data class RetryGoogle(
            val delayMs: Long,
            val requestAudioFocus: Boolean = false
        ) : ErrorDecision()

        data object FallbackToVosk : ErrorDecision()
        data object ReturnToIdle : ErrorDecision()
    }

    fun selectCommandRoute(
        networkAvailable: Boolean,
        speechRecognizerAvailable: Boolean
    ): CommandRoute =
        if (networkAvailable && speechRecognizerAvailable) {
            CommandRoute.GoogleStt
        } else {
            CommandRoute.VoskCommand
        }

    fun handleGoogleError(
        error: GoogleSttError,
        retryCount: Int,
        maxRetries: Int = DEFAULT_MAX_RETRIES
    ): ErrorDecision = when (error) {
        GoogleSttError.NoMatch,
        GoogleSttError.SpeechTimeout ->
            if (retryCount < maxRetries) ErrorDecision.RetryGoogle(delayMs = 200L)
            else ErrorDecision.ReturnToIdle

        GoogleSttError.Audio ->
            if (retryCount < maxRetries) {
                ErrorDecision.RetryGoogle(delayMs = 800L, requestAudioFocus = true)
            } else {
                ErrorDecision.ReturnToIdle
            }

        GoogleSttError.Client ->
            if (retryCount < maxRetries) ErrorDecision.RetryGoogle(delayMs = 500L)
            else ErrorDecision.ReturnToIdle

        GoogleSttError.Network,
        GoogleSttError.NetworkTimeout,
        GoogleSttError.Other -> ErrorDecision.FallbackToVosk
    }

    fun shouldIgnoreWakeWord(
        nowMs: Long,
        lastTtsEndMs: Long,
        echoGuardMs: Long = DEFAULT_ECHO_GUARD_MS
    ): Boolean = nowMs - lastTtsEndMs < echoGuardMs
}
