package com.example.ez_capstone.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import org.vosk.Model
import org.vosk.Recognizer
import org.json.JSONObject

/**
 * Vosk 기반 웨이크워드 감지 엔진.
 *
 * 역할 분리:
 * - 웨이크워드("이지야") 감지: Vosk (상시 대기, 온디바이스)
 * - 명령 인식: VoiceStateCoordinator가 Google SpeechRecognizer로 처리
 * - 오프라인 폴백: useVoskForCommands=true 시 Vosk가 명령까지 인식
 */
class VoskWakeWordEngine(
    private val context: Context
) {
    interface Listener {
        fun onWakeWordDetected()
        fun onSpeechResult(text: String)
        fun onPartialResult(text: String)
        fun onError(message: String)
        fun onWakeWordTimeout()
    }

    companion object {
        private const val TAG = "VoskWakeWordEngine"
        private const val SAMPLE_RATE = 16000
        private const val WAKE_TIMEOUT_MS = 7000L
        private const val SILENCE_TIMEOUT_MS = 2500L
    }

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var aec: AcousticEchoCanceler? = null
    private var listener: Listener? = null

    @Volatile
    private var isRunning = false
    @Volatile
    private var isPaused = false
    private var wakeDetected = false
    private var hasCommandSpeech = false
    private var lastCommandText: String? = null

    /**
     * true = Vosk가 명령까지 인식 (오프라인 폴백)
     * false = 웨이크워드만 감지 후 Coordinator에게 위임 (기본)
     */
    var useVoskForCommands = false

    private var audioThread: HandlerThread? = null
    private var audioHandler: Handler? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val silenceTimeoutRunnable: Runnable = Runnable {
        if (wakeDetected && hasCommandSpeech && lastCommandText != null) {
            mainHandler.removeCallbacks(wakeTimeoutRunnable)
            val command = lastCommandText!!
            wakeDetected = false
            hasCommandSpeech = false
            lastCommandText = null
            Log.d(TAG, "Silence detected (${SILENCE_TIMEOUT_MS}ms) → firing result: $command")
            listener?.onSpeechResult(command)
        }
    }

    private val wakeTimeoutRunnable: Runnable = Runnable {
        if (wakeDetected) {
            mainHandler.removeCallbacks(silenceTimeoutRunnable)
            wakeDetected = false
            hasCommandSpeech = false
            lastCommandText = null
            listener?.onWakeWordTimeout()
        }
    }

    fun initialize(modelPath: String) {
        try {
            model = Model(modelPath)
            Log.d(TAG, "Vosk model loaded from: $modelPath")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Vosk model: ${e.message}")
        }
    }

    fun isInitialized(): Boolean = model != null

    fun start(listener: Listener) {
        if (isRunning) return
        if (model == null) {
            listener.onError("Vosk model not initialized")
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            listener.onError("RECORD_AUDIO permission not granted")
            return
        }

        this.listener = listener
        isRunning = true
        wakeDetected = false

        try {
            recognizer = Recognizer(model, SAMPLE_RATE.toFloat())

            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ) * 2

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(audioRecord!!.audioSessionId)
                aec?.enabled = true
                Log.d(TAG, "AcousticEchoCanceler enabled")
            }

            audioRecord?.startRecording()

            audioThread = HandlerThread("VoskAudioThread").apply { start() }
            audioHandler = Handler(audioThread!!.looper)
            audioHandler?.post { audioLoop() }

            Log.d(TAG, "Vosk engine started (commandMode=${useVoskForCommands})")
        } catch (e: Exception) {
            // 정확한 사유 진단(추측 금지): 예외 클래스·cause·model null 여부.
            Log.e(TAG, "Vosk start failed: class=${e.javaClass.name} msg='${e.message}' " +
                "cause=${e.cause?.javaClass?.simpleName}:${e.cause?.message} modelNull=${model == null}")
            isRunning = false
            mainHandler.post { listener.onError("Failed to start: ${e.message}") }
        }
    }

    fun stop() {
        isRunning = false
        mainHandler.removeCallbacks(wakeTimeoutRunnable)
        mainHandler.removeCallbacks(silenceTimeoutRunnable)

        audioThread?.quitSafely()
        try { audioThread?.join(500) } catch (_: Exception) {}
        audioThread = null
        audioHandler = null

        try { audioRecord?.stop() } catch (_: Exception) {}
        audioRecord?.release()
        audioRecord = null

        aec?.release()
        aec = null

        recognizer?.close()
        recognizer = null

        isPaused = false
        wakeDetected = false
        hasCommandSpeech = false
        lastCommandText = null
        listener = null

        Log.d(TAG, "Vosk engine stopped")
    }

    fun release() {
        stop()
        model?.close()
        model = null
    }

    fun isRunning(): Boolean = isRunning

    fun pause() {
        isPaused = true
        Log.d(TAG, "Vosk paused")
    }

    fun resume() {
        isPaused = false
        wakeDetected = false
        hasCommandSpeech = false
        lastCommandText = null
        Log.d(TAG, "Vosk resumed")
    }

    private fun audioLoop() {
        val buffer = ShortArray(4096)

        while (isRunning) {
            val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
            if (read <= 0 || isPaused) continue

            val bytes = ByteArray(read * 2)
            for (i in 0 until read) {
                bytes[i * 2] = (buffer[i].toInt() and 0xFF).toByte()
                bytes[i * 2 + 1] = (buffer[i].toInt() shr 8 and 0xFF).toByte()
            }

            val rec = recognizer ?: continue

            if (rec.acceptWaveForm(bytes, bytes.size)) {
                val result = rec.result
                mainHandler.post { processFinalResult(result) }
            } else {
                val partial = rec.partialResult
                mainHandler.post { processPartialResult(partial) }
            }
        }
    }

    private fun processPartialResult(json: String) {
        val text = parseText(json, "partial")
        if (text.isBlank()) return

        if (!wakeDetected) {
            // 웨이크워드 감지
            if (WakeWordMatcher.containsWakeWord(text)) {
                wakeDetected = true
                listener?.onWakeWordDetected()

                if (useVoskForCommands) {
                    // 오프라인 모드: Vosk가 계속 명령 인식
                    mainHandler.postDelayed(wakeTimeoutRunnable, WAKE_TIMEOUT_MS)
                }
                // 온라인 모드: Coordinator가 SpeechRecognizer 시작 (Vosk는 pause됨)
            }
        } else if (useVoskForCommands) {
            // 오프라인 폴백: Vosk로 명령 인식
            val command = WakeWordMatcher.extractCommand(text)
            if (command != null) {
                if (!hasCommandSpeech) {
                    hasCommandSpeech = true
                    mainHandler.removeCallbacks(wakeTimeoutRunnable)
                    Log.d(TAG, "Command speech started (Vosk fallback)")
                }
                lastCommandText = command
                listener?.onPartialResult(command)
                mainHandler.removeCallbacks(silenceTimeoutRunnable)
                mainHandler.postDelayed(silenceTimeoutRunnable, SILENCE_TIMEOUT_MS)
            }
        }
    }

    private fun processFinalResult(json: String) {
        val text = parseText(json, "text")
        if (text.isBlank()) return

        if (wakeDetected && useVoskForCommands) {
            mainHandler.removeCallbacks(wakeTimeoutRunnable)
            mainHandler.removeCallbacks(silenceTimeoutRunnable)
            wakeDetected = false
            hasCommandSpeech = false
            lastCommandText = null

            val command = WakeWordMatcher.extractCommand(text)
            if (command != null) {
                listener?.onSpeechResult(command)
            } else {
                listener?.onWakeWordTimeout()
            }
        } else if (!wakeDetected) {
            // 웨이크워드 + 명령이 한 문장에 들어온 경우
            if (WakeWordMatcher.containsWakeWord(text)) {
                val command = WakeWordMatcher.extractCommand(text)
                if (command != null && useVoskForCommands) {
                    listener?.onWakeWordDetected()
                    listener?.onSpeechResult(command)
                } else {
                    wakeDetected = true
                    listener?.onWakeWordDetected()
                    if (useVoskForCommands) {
                        mainHandler.postDelayed(wakeTimeoutRunnable, WAKE_TIMEOUT_MS)
                    }
                }
            }
        }
    }

    private fun parseText(json: String, key: String): String {
        return try {
            JSONObject(json).optString(key, "").trim()
        } catch (_: Exception) {
            ""
        }
    }
}
