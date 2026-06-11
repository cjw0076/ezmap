package com.example.ez_capstone

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.ez_capstone.BuildConfig
import com.example.ez_capstone.monitoring.SentryInitializer
import com.example.ez_capstone.server.TokenManager
import com.example.ez_capstone.offline.VectorStore
import com.example.ez_capstone.voice.VoskWakeWordEngine
import com.example.ez_capstone.voice.VoiceStateCoordinator
import com.example.ez_capstone.worker.BackgroundWorkScheduler
import com.kakao.sdk.common.KakaoSdk
import com.kakao.vectormap.KakaoMapSdk
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class EZApplication : Application(), Configuration.Provider {

    @Inject lateinit var voskEngine: VoskWakeWordEngine
    @Inject lateinit var voiceCoordinator: VoiceStateCoordinator
    @Inject lateinit var vectorStore: VectorStore
    @Inject lateinit var workerFactory: HiltWorkerFactory

    // HiltWorker(@AssistedInject) 인스턴스화에 필수 — 없으면 워커가 런타임 크래시.
    // 기본 WorkManagerInitializer는 매니페스트에서 제거 → 이 설정으로 on-demand 초기화.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // 전역 크래시 핸들러 (Phase 5.1)
        setupCrashHandler()
        SentryInitializer.init(this)

        // Kakao SDK 초기화 (로그인용)
        KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)

        // Kakao Map SDK 초기화
        KakaoMapSdk.init(this, BuildConfig.KAKAO_MAP_KEY)

        // JWT 토큰 매니저 초기화
        TokenManager.init(this)

        // Vosk 모델 비동기 로드
        initVoskModel()

        // VectorStore DB → 메모리 인덱스 로딩 (Phase 10)
        CoroutineScope(Dispatchers.IO).launch {
            try { vectorStore.loadFromDb() } catch (e: Exception) {
                Log.e("EZApplication", "VectorStore load error: ${e.message}")
            }
        }

        // 백그라운드 주기 워커 등록 — Proactive(선제 알림)/Predictive/Skill학습/메모리정리.
        // (이전엔 dead-wire로 한 번도 실행 안 됐음 — Pillar 3·6 부활.)
        try { BackgroundWorkScheduler.schedule(this) } catch (e: Exception) {
            Log.e("EZApplication", "Worker schedule error: ${e.message}")
        }
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            try {
                Log.e("CRASH", "Uncaught exception in ${thread.name}: ${ex.message}", ex)
                val prefs = getSharedPreferences("ezmap_crash", MODE_PRIVATE)
                prefs.edit()
                    .putString("last_crash", "${ex::class.simpleName}: ${ex.message}")
                    .putLong("last_crash_time", System.currentTimeMillis())
                    .apply()
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, ex)
        }
    }

    private fun initVoskModel() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val modelDir = File(filesDir, "vosk-model-small-ko")
                if (!modelDir.exists()) {
                    copyAssetFolder("vosk-model-small-ko", modelDir)
                }
                voskEngine.initialize(modelDir.absolutePath)
                voiceCoordinator.onModelReady()
                Log.d("EZApplication", "Vosk model ready: ${modelDir.absolutePath}")
            } catch (e: Exception) {
                Log.e("EZApplication", "Vosk init error: ${e.message}")
            }
        }
    }

    private fun copyAssetFolder(assetPath: String, targetDir: File) {
        val assetManager = assets
        val files = assetManager.list(assetPath) ?: return
        targetDir.mkdirs()
        for (file in files) {
            val subAssetPath = "$assetPath/$file"
            val subFiles = assetManager.list(subAssetPath)
            if (subFiles != null && subFiles.isNotEmpty()) {
                copyAssetFolder(subAssetPath, File(targetDir, file))
            } else {
                assetManager.open(subAssetPath).use { input ->
                    File(targetDir, file).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}
