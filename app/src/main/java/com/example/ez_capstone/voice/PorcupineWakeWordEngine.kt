package com.example.ez_capstone.voice

import android.content.Context
import android.util.Log
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import ai.picovoice.porcupine.PorcupineException
import com.example.ez_capstone.BuildConfig

/**
 * Porcupine 기반 웨이크워드 감지 엔진.
 * VoskWakeWordEngine과 동일한 Listener 인터페이스를 사용.
 *
 * 장점: 상용 수준 정확도, 낮은 오인식률, .ppn 커스텀 모델
 * 웨이크워드만 감지 — 명령 인식은 VoiceStateCoordinator(Google STT)가 처리.
 */
class PorcupineWakeWordEngine(
    private val context: Context
) {
    companion object {
        private const val TAG = "PorcupineWakeEngine"
        private const val KEYWORD_PATH = "eziya_wakeword.ppn"
        private const val MODEL_PATH = "porcupine_params_ko.pv"
        private const val SENSITIVITY = 0.9f
    }

    private var porcupineManager: PorcupineManager? = null
    private var listener: VoskWakeWordEngine.Listener? = null

    @Volatile
    private var isRunning = false
    @Volatile
    private var isPaused = false

    // VoskWakeWordEngine 호환 — 오프라인 명령 인식 (Porcupine에서는 미사용)
    var useVoskForCommands = false

    fun isInitialized(): Boolean = true  // Porcupine은 별도 모델 로딩 불필요

    fun isRunning(): Boolean = isRunning

    fun initialize(modelPath: String) {
        // Porcupine은 별도 초기화 불필요 (start 시 생성)
        Log.d(TAG, "PorcupineWakeWordEngine ready (model path ignored, using .ppn asset)")
    }

    fun start(listener: VoskWakeWordEngine.Listener) {
        if (isRunning) return
        this.listener = listener

        val accessKey = BuildConfig.PORCUPINE_ACCESS_KEY
        if (accessKey.isBlank()) {
            // 던진다 → VoiceStateCoordinator가 Vosk로 폴백(웨이크워드가 죽지 않게).
            Log.e(TAG, "PORCUPINE_ACCESS_KEY is empty — falling back to Vosk")
            throw IllegalStateException("Porcupine access key 미설정")
        }

        try {
            // stop() 먼저 호출해야 오디오 스레드가 정지된 뒤 delete() 가능
            porcupineManager?.stop()
            porcupineManager?.delete()
            porcupineManager = null

            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setModelPath(MODEL_PATH)
                .setKeywordPath(KEYWORD_PATH)
                .setSensitivity(SENSITIVITY)
                .build(context, PorcupineManagerCallback { keywordIndex ->
                    // isRunning 체크: delete() 이후 오디오 콜백이 지연 호출되는 경쟁 방지
                    if (keywordIndex >= 0 && isRunning && !isPaused) {
                        Log.d(TAG, "Wake word detected! (keyword index: $keywordIndex)")
                        listener.onWakeWordDetected()
                    }
                })

            porcupineManager?.start()
            isRunning = true
            isPaused = false
            Log.d(TAG, "Porcupine started (sensitivity=$SENSITIVITY)")
        } catch (e: PorcupineException) {
            // init 실패 → 삼키지 말고 던진다. VoiceStateCoordinator가 Vosk로 폴백.
            // (기존엔 onError만 호출+return → 폴백 catch가 안 걸려 웨이크워드가 통째로 죽음)
            // 정확한 사유를 알기 위해 full detail 로깅: 보통 PV 상태코드(ACTIVATION_LIMIT/
            // ACTIVATION_THROTTLED=재설치 과다, INVALID_ARGUMENT=.ppn 비호환, IO=모델 로드 실패).
            Log.e(TAG, "Porcupine init failed (→ Vosk fallback): " +
                "class=${e.javaClass.simpleName} msg='${e.message}' " +
                "messageStack=${e.messageStack?.joinToString(" | ")} cause=${e.cause}")
            isRunning = false
            throw e
        }
    }

    fun stop() {
        isRunning = false
        isPaused = false
        try {
            porcupineManager?.stop()
            porcupineManager?.delete()
        } catch (e: PorcupineException) {
            Log.w(TAG, "Porcupine stop error: ${e.message}")
        }
        porcupineManager = null
        listener = null
        Log.d(TAG, "Porcupine stopped")
    }

    fun pause() {
        if (!isRunning) return
        isPaused = true
        try {
            porcupineManager?.stop()
        } catch (_: PorcupineException) {}
        Log.d(TAG, "Porcupine paused")
    }

    fun resume() {
        if (!isRunning) return
        isPaused = false
        try {
            porcupineManager?.start()
        } catch (e: PorcupineException) {
            Log.e(TAG, "Porcupine resume failed: ${e.message}")
        }
        Log.d(TAG, "Porcupine resumed")
    }

    fun release() {
        stop()
    }
}
