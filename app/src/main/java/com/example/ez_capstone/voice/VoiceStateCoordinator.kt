package com.example.ez_capstone.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.KeyEvent
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.content.ContextCompat
import androidx.media.session.MediaButtonReceiver
import com.example.ez_capstone.config.ApiKeyProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 하이브리드 음성 상태 코디네이터.
 *
 * 파이프라인:
 * 1. Vosk — 웨이크워드 "이지야" 상시 감지 (온디바이스)
 * 2. Google SpeechRecognizer — 명령 인식 (온라인, 한국어 최적)
 * 3. Vosk 폴백 — 오프라인 시 Vosk로 명령까지 인식
 *
 * 외부 인터페이스(CommandCallback)는 변경 없음.
 */
class VoiceStateCoordinator(
    private val voskEngine: VoskWakeWordEngine,
    private val porcupineEngine: PorcupineWakeWordEngine?,
    private val context: Context,
    private val apiKeyProvider: ApiKeyProvider? = null,
    private val liveVoiceSession: LiveVoiceSession? = null,
    private val liveFunctionCallBridge: LiveFunctionCallBridge? = null
) {
    enum class State { IDLE, WAKE_DETECTED, PROCESSING, SPEAKING }

    interface CommandCallback {
        // alternatives: STT N-best 대안 후보(1순위 제외). 에이전트가 1순위 어색 시 재해석에 사용.
        fun onCommand(text: String, alternatives: List<String> = emptyList())
        fun onWakeWordOnly()
        fun onPartialSpeech(text: String)
    }

    companion object {
        private const val TAG = "VoiceStateCoordinator"
        private const val STT_TIMEOUT_MS = 12000L
        private const val TTS_COOLDOWN_MS = 180L      // Guard 1: TTS 끝 → 마이크 복귀 쿨다운 (체감 매끄러움 위해 단축)
        private const val WAKE_ACK_DELAY_MS = 140L    // 비프 ack 후 STT 시작까지 — "네" TTS 대기 제거(즉각 깨우기)
        private const val MAX_STT_RETRIES = SttRoutingPolicy.DEFAULT_MAX_RETRIES
        private const val ECHO_GUARD_MS = SttRoutingPolicy.DEFAULT_ECHO_GUARD_MS
        private const val LIVE_INPUT_SAMPLE_RATE = 16_000
        private const val LIVE_OUTPUT_SAMPLE_RATE = 24_000
        private const val LIVE_CHUNK_MS = 100
    }

    // Porcupine 사용 가능 여부
    // Porcupine을 기본 웨이크 엔진으로 — 작고(.ppn 4KB+params 1MB) 정확, 새 사용자에게 동작.
    // Vosk 폴백: 전체 모델(graph/HCLr.fst 173MB 포함)을 번들에 넣어 Recognizer 생성 가능
    // → Porcupine 활성화 한도 초과 시에도 오프라인 웨이크워드가 동작한다(2026-06 HCLr.fst 추가).
    private val usePorcupine: Boolean get() = porcupineEngine != null

    // Phase 9 Live API mode. Default remains off until PCM streaming is validated on device.
    var useLiveApi: Boolean = false
        set(value) {
            field = value
            if (!value) stopLiveSession()
        }

    // Guard 변수
    private val sttRetryCount = java.util.concurrent.atomic.AtomicInteger(0)
    private var lastTtsEndTime = 0L
    private var expectFollowUp = false
    private var ttsWatchdogJob: Job? = null  // speak()마다 교체 — 복수 워치독 누적 방지

    // 웨이크 ack용 짧은 비프(earcon) — "네" TTS 합성 대기 없이 즉각 깨우기.
    private val toneGenerator: ToneGenerator? by lazy {
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull()
    }
    private fun playWakeBeep() {
        runCatching { toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120) }
    }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private var callback: CommandCallback? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var engineActive = false

    // Google SpeechRecognizer
    private var speechRecognizer: SpeechRecognizer? = null
    private var isGoogleSttActive = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val liveScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var liveEventJob: Job? = null
    private var liveBridgeJob: Job? = null
    private var liveCaptureJob: Job? = null
    private var liveEndTurnJob: Job? = null
    private var liveAudioTrack: AudioTrack? = null
    private var liveFallbackStarted = false

    // AudioFocus — Bixby 등 다른 앱의 마이크 점유 해제 요청
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    // TTS 설정
    var ttsSpeed: Float = 1.0f
        set(value) {
            field = value
            tts?.setSpeechRate(value)
        }

    var ttsEnabled: Boolean = true

    // ── TTS 초기화 ──

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val langResult = tts?.setLanguage(Locale.KOREAN)
                Log.d(TAG, "[TTS] init SUCCESS, setLanguage(KOREAN)=$langResult (0=OK, -1/-2=missing)")
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d(TAG, "[TTS] onStart id=$utteranceId state=${_state.value}")
                    }
                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "[TTS] onDone id=$utteranceId")
                        lastTtsEndTime = System.currentTimeMillis()
                        if (utteranceId == "wake_ack") {
                            Log.d(TAG, "Wake ack done → starting STT")
                            mainHandler.post { startCommandListening() }
                            return
                        }
                        if (_state.value == State.SPEAKING) {
                            // Guard 1: TTS 끝 → 쿨다운 후 마이크 복귀
                            mainHandler.postDelayed({
                                if (expectFollowUp) {
                                    expectFollowUp = false
                                    Log.d(TAG, "TTS done → follow-up expected, starting STT directly")
                                    // follow-up은 churn 최소화: 재시도 1회만 — 무응답/오인식 시 빠르게 웨이크 대기로 복귀
                                    // (응답 후 마이크가 에러·재시도로 뚝뚝 끊기던 문제 완화).
                                    sttRetryCount.set((MAX_STT_RETRIES - 1).coerceAtLeast(0))
                                    _state.value = State.WAKE_DETECTED
                                    pauseWakeEngine()
                                    startCommandListening()
                                } else {
                                    resumeWakeEngine()
                                    _state.value = State.IDLE
                                    Log.d(TAG, "TTS done → ${TTS_COOLDOWN_MS}ms cooldown → IDLE, wake engine resumed")
                                }
                            }, TTS_COOLDOWN_MS)
                        }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        Log.w(TAG, "[TTS] onError id=$utteranceId state=${_state.value}")
                        lastTtsEndTime = System.currentTimeMillis()
                        if (utteranceId == "wake_ack") {
                            mainHandler.post { startCommandListening() }
                            return
                        }
                        if (_state.value == State.SPEAKING) {
                            mainHandler.postDelayed({
                                resumeWakeEngine()
                                _state.value = State.IDLE
                            }, TTS_COOLDOWN_MS)
                        }
                    }
                })
                ttsReady = true
                Log.d(TAG, "TTS initialized")
            } else {
                Log.e(TAG, "[TTS] init FAILED status=$status — TTS 사용 불가")
            }
        }
    }

    // ── Vosk 엔진 리스너 (웨이크워드 전용) ──

    private val engineListener = object : VoskWakeWordEngine.Listener {
        override fun onWakeWordDetected() {
            Log.d(TAG, "Wake word detected! Current state: ${_state.value}")

            // Guard 3: TTS 끝난 직후 에코로 웨이크워드가 감지되면 무시
            val now = System.currentTimeMillis()
            val timeSinceTts = now - lastTtsEndTime
            if (SttRoutingPolicy.shouldIgnoreWakeWord(now, lastTtsEndTime, ECHO_GUARD_MS)) {
                Log.d(TAG, "Echo guard: ignoring wake word (${timeSinceTts}ms since TTS)")
                return
            }

            if (_state.value == State.SPEAKING) {
                tts?.stop()
                stopGoogleStt()
                resumeWakeEngine()
                Log.d(TAG, "Barge-in: TTS stopped")
            }
            _state.value = State.WAKE_DETECTED
            sttRetryCount.set(0)  // Guard 2: 재시도 카운터 리셋
            pauseWakeEngine()  // 마이크 양보
            callback?.onPartialSpeech("")

            // 빠른 비프 ack + STT 즉시 시작 — "네" TTS 합성 대기(~0.8s)를 없애 깨우기를 즉각화(매끄러움).
            if (ttsEnabled) playWakeBeep()
            mainHandler.postDelayed({ startCommandListening() }, WAKE_ACK_DELAY_MS)
        }

        // Vosk 오프라인 폴백용 콜백
        override fun onSpeechResult(text: String) {
            Log.d(TAG, "Vosk fallback result: $text")
            _state.value = State.PROCESSING
            callback?.onCommand(text)
        }

        override fun onPartialResult(text: String) {
            callback?.onPartialSpeech(text)
        }

        override fun onError(message: String) {
            Log.e(TAG, "Engine error: $message")
            _state.value = State.IDLE
        }

        override fun onWakeWordTimeout() {
            Log.d(TAG, "Vosk wake word timeout")
            _state.value = State.IDLE
            callback?.onWakeWordOnly()
        }
    }

    // ── 명령 인식 시작 (온라인/오프라인 분기) ──

    private fun startCommandListening() {
        if (_state.value != State.WAKE_DETECTED) return

        val apiKey = apiKeyProvider?.activeGeminiKey.orEmpty()
        if (VoicePipelinePolicy.shouldUseLiveApi(
                enabled = useLiveApi,
                networkAvailable = isNetworkAvailable(),
                apiKey = apiKey,
                liveSessionAvailable = liveVoiceSession != null
            )
        ) {
            startLiveCommandListening(apiKey)
            return
        }

        startLegacyCommandListening()
    }

    private fun startLegacyCommandListening() {
        if (_state.value != State.WAKE_DETECTED) return

        // AudioFocus 획득 (Bixby 등 마이크 점유 해제)
        requestAudioFocus()

        when (
            SttRoutingPolicy.selectCommandRoute(
                networkAvailable = isNetworkAvailable(),
                speechRecognizerAvailable = SpeechRecognizer.isRecognitionAvailable(context)
            )
        ) {
            SttRoutingPolicy.CommandRoute.GoogleStt -> {
                // Vosk가 마이크를 완전히 놓을 때까지 150ms 대기 후 Google STT 시작
                // (즉시 시작 시 ERROR_AUDIO 발생 가능)
                mainHandler.postDelayed({ startGoogleStt() }, 150L)
            }
            SttRoutingPolicy.CommandRoute.VoskCommand -> {
                Log.d(TAG, "Offline → Vosk fallback for command recognition")
                voskEngine.useVoskForCommands = true
                voskEngine.resume()
            }
        }
    }

    private fun startLiveCommandListening(apiKey: String) {
        val session = liveVoiceSession ?: run {
            startLegacyCommandListening()
            return
        }

        requestAudioFocus()
        pauseWakeEngine()
        liveFallbackStarted = false
        ensureLiveBridgeAttached()
        ensureLiveEventsObserved()
        val toolDecl = apiKeyProvider?.let {
            com.example.ez_capstone.agent.ToolDeclarations.toLiveApiFormat(it)
        }
        session.startStream(apiKey, toolDeclarations = toolDecl)
        if (session.connected) {
            startLiveAudioCapture(session)
            scheduleLiveTurnEnd(session)
        }
        Log.d(TAG, "Live API command listening started")
    }

    private fun startLiveAudioCapture(session: LiveVoiceSession) {
        if (liveCaptureJob?.isActive == true) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            fallbackFromLive("record audio permission missing")
            return
        }

        liveCaptureJob = liveScope.launch(Dispatchers.IO) {
            val minBuffer = AudioRecord.getMinBufferSize(
                LIVE_INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuffer <= 0) {
                fallbackFromLive("invalid AudioRecord buffer")
                return@launch
            }

            val chunkBytes = LIVE_INPUT_SAMPLE_RATE * 2 * LIVE_CHUNK_MS / 1000
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                LIVE_INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, chunkBytes * 2)
            )

            try {
                record.startRecording()
                while (isActive && _state.value == State.WAKE_DETECTED) {
                    val buffer = ByteArray(chunkBytes)
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        session.sendAudioChunk(if (read == buffer.size) buffer else buffer.copyOf(read))
                    }
                }
            } catch (e: Exception) {
                fallbackFromLive("audio capture failed: ${e.javaClass.simpleName}")
            } finally {
                runCatching { record.stop() }
                record.release()
            }
        }
    }

    private fun scheduleLiveTurnEnd(session: LiveVoiceSession) {
        liveEndTurnJob?.cancel()
        liveEndTurnJob = liveScope.launch {
            delay(STT_TIMEOUT_MS)
            stopLiveAudioCapture()
            session.endStream()
            _state.value = State.PROCESSING
        }
    }

    private fun ensureLiveBridgeAttached() {
        if (liveBridgeJob?.isActive == true) return
        liveBridgeJob = liveFunctionCallBridge?.attach()
    }

    private fun ensureLiveEventsObserved() {
        if (liveEventJob?.isActive == true) return
        val session = liveVoiceSession ?: return
        liveEventJob = liveScope.launch {
            session.events.collect { event ->
                when (event) {
                    is LiveVoiceSession.SessionEvent.PartialText -> callback?.onPartialSpeech(event.text)
                    is LiveVoiceSession.SessionEvent.AudioChunk -> {
                        _state.value = State.SPEAKING
                        playLiveAudio(event.pcm)
                    }
                    is LiveVoiceSession.SessionEvent.ToolCall -> Unit
                    is LiveVoiceSession.SessionEvent.TurnComplete -> {
                        stopLiveAudioCapture()
                        releaseAudioFocus()
                        resumeWakeEngine()
                        _state.value = State.IDLE
                    }
                    is LiveVoiceSession.SessionEvent.ConnectionChange -> {
                        if (event.connected) {
                            startLiveAudioCapture(session)
                            scheduleLiveTurnEnd(session)
                        } else if (_state.value == State.WAKE_DETECTED) {
                            fallbackFromLive("connection closed")
                        }
                    }
                    is LiveVoiceSession.SessionEvent.Error -> fallbackFromLive(event.message)
                }
            }
        }
    }

    private fun fallbackFromLive(reason: String) {
        if (liveFallbackStarted) return
        liveFallbackStarted = true
        Log.w(TAG, "Live API fallback to legacy STT: $reason")
        stopLiveAudioCapture()
        releaseLiveAudioTrack()
        liveVoiceSession?.close()
        releaseAudioFocus()
        mainHandler.post {
            _state.value = State.WAKE_DETECTED
            startLegacyCommandListening()
        }
    }

    private fun stopLiveSession() {
        stopLiveAudioCapture()
        releaseLiveAudioTrack()
        liveEventJob?.cancel()
        liveEventJob = null
        liveBridgeJob?.cancel()
        liveBridgeJob = null
        liveFunctionCallBridge?.detach()
        liveVoiceSession?.close()
        liveFallbackStarted = false
    }

    private fun stopLiveAudioCapture() {
        liveEndTurnJob?.cancel()
        liveEndTurnJob = null
        liveCaptureJob?.cancel()
        liveCaptureJob = null
    }

    private fun playLiveAudio(pcm: ByteArray) {
        val track = liveAudioTrack ?: createLiveAudioTrack().also { liveAudioTrack = it }
        if (track.state != AudioTrack.STATE_INITIALIZED) return
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
            track.play()
        }
        track.write(pcm, 0, pcm.size)
    }

    private fun createLiveAudioTrack(): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(
            LIVE_OUTPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(LIVE_OUTPUT_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuffer, LIVE_OUTPUT_SAMPLE_RATE))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun releaseLiveAudioTrack() {
        liveAudioTrack?.let {
            runCatching { it.stop() }
            it.release()
        }
        liveAudioTrack = null
    }

    // ── Google SpeechRecognizer ──

    private fun startGoogleStt() {
        Log.d(TAG, "Starting Google SpeechRecognizer")
        isGoogleSttActive = true

        try {
            try { speechRecognizer?.setRecognitionListener(null) } catch (_: Exception) {}
            try { speechRecognizer?.cancel() } catch (_: Exception) {}
            try { speechRecognizer?.destroy() } catch (_: Exception) {}
            speechRecognizer = null
            // createAttributionContext는 API 30(Android 11) 이상 전용
            val attrContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.createAttributionContext("voice_command")
            } else {
                context
            }
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(attrContext)

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Google STT ready")
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "Google STT speech started")
                    // 발화 시작 → 타임아웃 타이머 취소
                    mainHandler.removeCallbacks(sttTimeoutRunnable)
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d(TAG, "Google STT speech ended")
                }

                override fun onError(error: Int) {
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "네트워크 타임아웃"
                        SpeechRecognizer.ERROR_NETWORK -> "네트워크 오류"
                        SpeechRecognizer.ERROR_AUDIO -> "오디오 오류"
                        SpeechRecognizer.ERROR_NO_MATCH -> "인식 실패"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "발화 타임아웃"
                        else -> "STT 오류 ($error)"
                    }
                    Log.w(TAG, "Google STT error: $errorMsg (code $error)")
                    isGoogleSttActive = false
                    mainHandler.removeCallbacks(sttTimeoutRunnable)

                    when (
                        val decision = SttRoutingPolicy.handleGoogleError(
                            googleSttErrorFromCode(error),
                            sttRetryCount.get(),
                            MAX_STT_RETRIES
                        )
                    ) {
                        is SttRoutingPolicy.ErrorDecision.RetryGoogle -> {
                            releaseAudioFocus()
                            val retryNum = sttRetryCount.incrementAndGet()
                            Log.d(TAG, "Google STT retry ($retryNum/$MAX_STT_RETRIES)")
                            _state.value = State.WAKE_DETECTED
                            mainHandler.postDelayed({
                                if (decision.requestAudioFocus) requestAudioFocus()
                                startGoogleStt()
                            }, decision.delayMs)
                        }
                        SttRoutingPolicy.ErrorDecision.ReturnToIdle -> {
                            releaseAudioFocus()
                            Log.d(TAG, "Google STT max retries reached, silent IDLE")
                            _state.value = State.IDLE
                            voskEngine.useVoskForCommands = false
                            resumeWakeEngine()
                        }
                        SttRoutingPolicy.ErrorDecision.FallbackToVosk -> {
                            Log.d(TAG, "STT error → Vosk fallback")
                            voskEngine.useVoskForCommands = true
                            voskEngine.resume()
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    isGoogleSttActive = false
                    mainHandler.removeCallbacks(sttTimeoutRunnable)
                    releaseAudioFocus()

                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim()

                    if (!text.isNullOrBlank()) {
                        // N-best 대안 후보(1순위 제외, 중복·빈·동일 제거, 최대 4개) — 오인식 재해석용.
                        val alternatives = (matches ?: arrayListOf()).drop(1)
                            .mapNotNull { it?.trim()?.takeIf { a -> a.isNotBlank() } }
                            .distinct()
                            .filter { it != text }
                            .take(4)
                        Log.d(TAG, "Google STT result: $text${if (alternatives.isNotEmpty()) " | alts=$alternatives" else ""}")
                        _state.value = State.PROCESSING
                        voskEngine.useVoskForCommands = false
                        // PROCESSING 동안 웨이크 엔진 재개: 에이전트가 응답 중에도
                        // 새로운 "이지야" 호출을 받아 barge-in 가능
                        resumeWakeEngine()
                        callback?.onCommand(text, alternatives)
                    } else {
                        _state.value = State.IDLE
                        voskEngine.useVoskForCommands = false
                        voskEngine.resume()
                        callback?.onWakeWordOnly()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim()
                    if (!text.isNullOrBlank()) {
                        callback?.onPartialSpeech(text)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)  // N-best: 오인식 재해석용 대안 후보 확보
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            }

            speechRecognizer?.startListening(intent)

            // 안전 타임아웃 (SpeechRecognizer 자체 타임아웃이 안 걸릴 경우 대비)
            mainHandler.postDelayed(sttTimeoutRunnable, STT_TIMEOUT_MS)

        } catch (e: Exception) {
            Log.e(TAG, "Google STT start failed: ${e.message} → Vosk fallback")
            isGoogleSttActive = false
            voskEngine.useVoskForCommands = true
            voskEngine.resume()
        }
    }

    private val sttTimeoutRunnable = Runnable {
        if (isGoogleSttActive) {
            Log.d(TAG, "Google STT timeout → stopping")
            stopGoogleStt()
            _state.value = State.IDLE
            voskEngine.useVoskForCommands = false
            voskEngine.resume()
            callback?.onWakeWordOnly()
        }
    }

    private fun googleSttErrorFromCode(error: Int): SttRoutingPolicy.GoogleSttError = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> SttRoutingPolicy.GoogleSttError.NoMatch
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SttRoutingPolicy.GoogleSttError.SpeechTimeout
        SpeechRecognizer.ERROR_AUDIO -> SttRoutingPolicy.GoogleSttError.Audio
        SpeechRecognizer.ERROR_CLIENT, 11 -> SttRoutingPolicy.GoogleSttError.Client
        SpeechRecognizer.ERROR_NETWORK -> SttRoutingPolicy.GoogleSttError.Network
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> SttRoutingPolicy.GoogleSttError.NetworkTimeout
        else -> SttRoutingPolicy.GoogleSttError.Other
    }

    private fun stopGoogleStt() {
        isGoogleSttActive = false
        mainHandler.removeCallbacks(sttTimeoutRunnable)
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (_: Exception) {}
        try { speechRecognizer?.destroy() } catch (_: Exception) {}
        speechRecognizer = null
    }

    // ── AudioFocus (삼성 Bixby 등 마이크 경쟁 해결) ──

    private fun requestAudioFocus(): Boolean {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { focus ->
                if (focus == AudioManager.AUDIOFOCUS_LOSS) {
                    Log.d(TAG, "AudioFocus lost — another app took over")
                }
            }
            .build()
        audioFocusRequest = request
        val result = audioManager.requestAudioFocus(request)
        Log.d(TAG, "AudioFocus request: ${if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) "GRANTED" else "DENIED"}")
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun releaseAudioFocus() {
        audioFocusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
            Log.d(TAG, "AudioFocus released")
        }
        audioFocusRequest = null
    }

    // ── 네트워크 확인 ──

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ── 화면별 활성화 ──

    // 현재 코디네이터를 소유한 화면 토큰. 화면 전환 시 떠나는 화면의 onDispose가
    // 새 화면이 막 등록한 callback을 지워버리는 race를 막는다.
    private var activeOwner: Any? = null

    /**
     * @param owner 호출 화면의 고유 토큰 (remember { Any() }). deactivate 소유권 검증용.
     * @param useWakeWord true(기본) = Porcupine/Vosk 상시 대기.
     *                    false = 버튼 트리거 전용 (NavigationScreen처럼 FAB만 사용).
     */
    fun activate(owner: Any, callback: CommandCallback, useWakeWord: Boolean = true) {
        activeOwner = owner
        this.callback = callback
        engineActive = true
        voskEngine.useVoskForCommands = false
        if (useWakeWord) {
            startWakeEngine()
        }
        _state.value = State.IDLE
        Log.d(TAG, "Activated (${if (useWakeWord) if (usePorcupine) "Porcupine" else "Vosk" else "button-only"} + Google STT)")
    }

    /**
     * 화면 onDispose에서 호출. 현재 소유자가 아니면 무시한다 — 화면 전환 중
     * 떠나는 화면의 늦은 onDispose가 새 화면의 callback을 지우지 않도록.
     */
    fun deactivate(owner: Any) {
        if (activeOwner !== owner) {
            Log.d(TAG, "Deactivate ignored — caller is not current owner")
            return
        }
        doDeactivate()
    }

    private fun doDeactivate() {
        activeOwner = null
        engineActive = false
        stopGoogleStt()
        stopLiveSession()
        stopAllEngines()
        tts?.stop()
        _state.value = State.IDLE
        callback = null
        Log.d(TAG, "Deactivated")
    }

    fun onModelReady() {
        if (engineActive) {
            startWakeEngine()
            Log.d(TAG, "Engine started after model ready")
        }
    }

    // ── 엔진 헬퍼 (Porcupine/Vosk 분기) ──

    private fun startWakeEngine() {
        if (!engineActive) return
        val pe = porcupineEngine
        if (usePorcupine && pe != null) {
            try {
                pe.start(engineListener)
            } catch (e: Exception) {
                Log.e(TAG, "Porcupine start failed, falling back to Vosk: ${e.message}")
                if (voskEngine.isInitialized()) voskEngine.start(engineListener)
            }
        } else if (voskEngine.isInitialized()) {
            voskEngine.start(engineListener)
        }
    }

    private fun pauseWakeEngine() {
        if (usePorcupine) porcupineEngine?.pause()
        voskEngine.pause()
    }

    private fun resumeWakeEngine() {
        if (usePorcupine) porcupineEngine?.resume()
        else voskEngine.resume()
    }

    private fun stopAllEngines() {
        porcupineEngine?.stop()
        voskEngine.stop()
    }

    // ── TTS ──

    fun stopSpeaking() {
        if (_state.value == State.SPEAKING) {
            tts?.stop()
            stopGoogleStt()
            voskEngine.useVoskForCommands = false
            voskEngine.resume()
            _state.value = State.IDLE
            Log.d(TAG, "TTS stopped by user, Vosk resumed")
        }
    }

    /**
     * TTS 발화.
     * @param priority HIGH=내비 안내(현재 TTS 즉시 중단), NORMAL=에이전트 응답(큐 추가)
     * @param expectFollowUpAfter true면 발화 후 자동으로 STT 청취(대화 후속). 내비 안내/경보는
     *   false로 둬야 매 안내마다 마이크가 켜지지 않음(주행 중 상시 청취/STT 실패 스팸 방지).
     */
    fun speak(text: String, priority: TtsPriority = TtsPriority.NORMAL, expectFollowUpAfter: Boolean = false) {
        if (!ttsReady || !ttsEnabled || text.isBlank()) {
            Log.w(TAG, "[TTS] speak SKIPPED — ttsReady=$ttsReady ttsEnabled=$ttsEnabled blank=${text.isBlank()}")
            return
        }
        tts?.setSpeechRate(ttsSpeed)
        _state.value = State.SPEAKING
        pauseWakeEngine()
        expectFollowUp = expectFollowUpAfter
        val queueMode = if (priority == TtsPriority.HIGH) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val uttId = "coordinator_tts_${System.currentTimeMillis()}"
        val r = tts?.speak(text, queueMode, null, uttId)
        Log.d(TAG, "[TTS] speak(${priority.name}) result=$r (0=OK,-1=ERROR) spkVol check, text='${text.take(30)}'")

        // TTS 워치독: 이전 워치독 취소 후 새 워치독 시작 — 누적 방지
        ttsWatchdogJob?.cancel()
        ttsWatchdogJob = liveScope.launch {
            delay(30_000)
            if (_state.value == State.SPEAKING) {
                Log.w(TAG, "TTS watchdog triggered — forcing IDLE after 30s stuck in SPEAKING")
                mainHandler.post {
                    resumeWakeEngine()
                    _state.value = State.IDLE
                }
            }
        }
    }

    enum class TtsPriority { HIGH, NORMAL }

    fun notifyProcessingDone(ttsText: String? = null) {
        if (!ttsText.isNullOrBlank()) {
            // 대화 연속성 + 매끄러움: 응답이 '질문'일 때만 follow-up 마이크를 자동으로 연다.
            // 사용자가 답할 턴(질문)에서만 이어듣고, terminal statement("안내를 시작합니다")엔 마이크를
            // 열지 않아 응답 후 churn(마이크 열림→무응답→에러→재시도→침묵)이 사라진다 → 깔끔히 웨이크 대기 복귀.
            // 주행 안내 TTS는 coordinator.speak 직접 호출이라 이 경로를 타지 않는다(follow-up=false).
            speak(ttsText, expectFollowUpAfter = isQuestion(ttsText))
        } else {
            voskEngine.useVoskForCommands = false
            voskEngine.resume()
            _state.value = State.IDLE
        }
    }

    // ── MediaSession — Bluetooth 이어버드 버튼 지원 ──

    private var mediaSession: MediaSessionCompat? = null

    /**
     * 블루투스 이어버드 버튼(HEADSETHOOK / MEDIA_PLAY) 또는 다른 외부 트리거가
     * 누를 때 호출되는 공개 진입점.
     * IDLE 상태에서만 웨이크워드 감지와 동일한 경로로 명령 수신을 시작한다.
     */
    fun triggerVoiceInput() {
        mainHandler.post {
            when (_state.value) {
                State.IDLE -> {
                    Log.d(TAG, "triggerVoiceInput: button → simulating wake word")
                    engineListener.onWakeWordDetected()
                }
                State.SPEAKING -> {
                    // 마이크 탭으로 바지인 — TTS 즉시 중단하고 바로 청취(에코가드/ack 없이 스냅하게).
                    Log.d(TAG, "triggerVoiceInput: barge-in during SPEAKING → stop TTS + listen")
                    tts?.stop()
                    stopGoogleStt()
                    expectFollowUp = false
                    sttRetryCount.set(0)
                    _state.value = State.WAKE_DETECTED
                    pauseWakeEngine()
                    callback?.onPartialSpeech("")
                    startCommandListening()
                }
                else -> Log.d(TAG, "triggerVoiceInput: ignored — state=${_state.value}")
            }
        }
    }

    /**
     * DEBUG 전용: STT를 거치지 않고 텍스트 명령을 현재 화면의 CommandCallback으로 직접 주입한다.
     * 실제 음성 경로(onResults → onCommand)와 동일한 진입점이라 flow 테스트에 신뢰성 있게 쓸 수 있다.
     * ADB: am broadcast -a com.example.ez_capstone.DEBUG_CMD --es text "강남역 가자"
     */
    fun injectDebugCommand(raw: String) {
        mainHandler.post {
            val cb = callback
            if (cb == null) {
                Log.w(TAG, "injectDebugCommand: no active callback (screen not activated)")
                return@post
            }
            // 디버그 N-best 주입: "발화||대안1||대안2" 형식이면 대안을 STT 후보로 전달.
            val parts = raw.split("||")
            val text = parts[0]
            val alternatives = parts.drop(1).map { it.trim() }.filter { it.isNotBlank() }
            Log.d(TAG, "injectDebugCommand: '$text'${if (alternatives.isNotEmpty()) " alts=$alternatives" else ""}")
            _state.value = State.PROCESSING
            cb.onCommand(text, alternatives)
        }
    }

    /**
     * MediaSession 초기화.
     * Activity.onCreate() 에서 한 번 호출한다.
     * 블루투스 이어버드(HEADSETHOOK)와 미디어 버튼(MEDIA_PLAY/PAUSE)을 수신한다.
     */
    fun initMediaSession() {
        if (mediaSession != null) return   // 중복 초기화 방지
        mediaSession = MediaSessionCompat(context, "EZmapVoice").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                    val keyEvent: KeyEvent? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            mediaButtonEvent.getParcelableExtra(
                                Intent.EXTRA_KEY_EVENT, KeyEvent::class.java
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                        }
                    if (keyEvent?.action == KeyEvent.ACTION_DOWN) {
                        when (keyEvent.keyCode) {
                            KeyEvent.KEYCODE_MEDIA_PLAY,
                            KeyEvent.KEYCODE_HEADSETHOOK -> {
                                triggerVoiceInput()
                                return true
                            }
                        }
                    }
                    return super.onMediaButtonEvent(mediaButtonEvent)
                }
            })
            val playbackState = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE
                )
                .setState(PlaybackStateCompat.STATE_PAUSED, 0L, 1.0f)
                .build()
            setPlaybackState(playbackState)
            isActive = true
        }
        Log.d(TAG, "MediaSession initialized — BT headset button active")
    }

    /**
     * MediaSession 해제.
     * Activity.onDestroy() 에서 호출한다.
     */
    fun releaseMediaSession() {
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        Log.d(TAG, "MediaSession released")
    }

    /**
     * 에이전트 응답이 사용자 답을 요구하는 '질문'인지 — follow-up 마이크 자동 오픈 판단용.
     * '안전 운전하세요'·'조심하세요' 같은 정중한 명령/조언(=statement)은 질문이 아니므로 제외해야
     * 한다('세요' 매칭 금지). '까요?'·'알려주시면'·'하시겠어요'처럼 답을 요구하는 형태만 true.
     */
    private fun isQuestion(text: String): Boolean {
        val t = text.trim()
        if (t.endsWith("?")) return true
        val patterns = listOf("까요", "하시겠", "할까", "보낼까", "드릴까", "줄까", "알려주시면", "무엇을 도와", "어느 것")
        return patterns.any { t.contains(it) }
    }

    fun release() {
        doDeactivate()
        liveScope.cancel()
        releaseMediaSession()
        speechRecognizer?.destroy()
        speechRecognizer = null
        tts?.shutdown()
        tts = null
        ttsReady = false
        runCatching { toneGenerator?.release() }
    }
}
