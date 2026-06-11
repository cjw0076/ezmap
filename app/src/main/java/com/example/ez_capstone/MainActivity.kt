package com.example.ez_capstone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.example.ez_capstone.analytics.AgentAnalytics
import com.example.ez_capstone.eval.AgentFailureAnalyzer
import com.example.ez_capstone.navigation.EZMapNavHost
import com.example.ez_capstone.trace.DecisionTraceDao
import com.example.ez_capstone.ui.theme.EZMapTheme
import com.example.ez_capstone.viewmodel.SettingsViewModel
import com.example.ez_capstone.voice.VoiceStateCoordinator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var coordinator: VoiceStateCoordinator
    @Inject lateinit var agentAnalytics: AgentAnalytics
    // Splash 라우팅(온보딩/키 분기)·DevScreen에 필요. 안 넘기면 NavHost에서 null로 떨어져
    // onboardingCompleted/hasApiKeys가 항상 false → 매 실행 온보딩 무한 반복 버그.
    @Inject lateinit var apiKeyProvider: com.example.ez_capstone.config.ApiKeyProvider
    @Inject lateinit var decisionTraceDao: DecisionTraceDao  // Phase 6 자가개선 분석(디버그 트리거)
    @Inject lateinit var skillLifecycleManager: com.example.ez_capstone.skill.SkillLifecycleManager
    @Inject lateinit var mcpGateway: com.example.ez_capstone.mcp.client.McpToolGateway

    // DEBUG 전용 — ADB로 음성 명령을 주입해 flow 테스트 (STT/UI 우회)
    private var debugCmdReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        coordinator.initMediaSession()
        registerDebugCommandReceiver()
        // 등록된 MCP 서버 도구를 시작 시 백그라운드로 새로고침(캐시는 메모리라 재시작마다 복원).
        if (mcpGateway.hasServers()) {
            lifecycleScope.launch { runCatching { mcpGateway.refreshAll() } }
        }
        enableEdgeToEdge()
        val prefs = getSharedPreferences("ezmap_settings", MODE_PRIVATE)
        val highContrast = prefs.getBoolean(SettingsViewModel.KEY_HIGH_CONTRAST, false)
        val voiceOnly = prefs.getBoolean(SettingsViewModel.KEY_VOICE_ONLY, false)
        setContent {
            EZMapTheme(highContrast = highContrast, voiceOnlyMode = voiceOnly) {
                EZMapNavHost(
                    rememberNavController(),
                    coordinator,
                    apiKeyProvider = apiKeyProvider,
                    agentAnalytics = agentAnalytics
                )
            }
        }
    }

    private fun registerDebugCommandReceiver() {
        if (!BuildConfig.DEBUG) return
        // 중복 등록 방지: 이전 리시버가 살아있으면 broadcast가 두 번 전달돼 명령이 2회 실행됨
        // (재설치/재생성 등으로 본 함수가 다시 불릴 때의 이중 디스패치 차단).
        debugCmdReceiver?.let { runCatching { unregisterReceiver(it) } }
        debugCmdReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val text = intent?.getStringExtra("text")?.trim().orEmpty()
                when {
                    text.isBlank() -> {}
                    // Phase 6: 최근 DecisionTrace를 분석해 반복 실패 리포트를 logcat에 출력
                    text == "__ANALYZE__" -> this@MainActivity.lifecycleScope.launch { runFailureAnalysis() }
                    text == "__RESET_HEALTH__" -> {
                        skillLifecycleManager.resetAll()
                        Log.d("SelfImprove", "도구 건강 초기화 완료(자동비활성화 해제)")
                    }
                    // [DEBUG] MCP 서버 등록: __MCP_ADD__:id|name|url|token(선택)
                    text.startsWith("__MCP_ADD__:") -> this@MainActivity.lifecycleScope.launch {
                        val p = text.removePrefix("__MCP_ADD__:").split("|")
                        if (p.size < 3) { Log.e("MCP", "형식: __MCP_ADD__:id|name|url|token"); return@launch }
                        val cfg = com.example.ez_capstone.mcp.client.McpServerConfig(
                            id = p[0].trim(), name = p[1].trim(), url = p[2].trim(),
                            authToken = p.getOrNull(3)?.trim().orEmpty()
                        )
                        val r = mcpGateway.addServer(cfg)
                        Log.d("MCP", "add '${cfg.id}' → ${r.getOrNull()?.let { "$it 도구" } ?: "실패: ${r.exceptionOrNull()?.message}"}")
                    }
                    text == "__MCP_REFRESH__" -> this@MainActivity.lifecycleScope.launch {
                        mcpGateway.refreshAll()
                        Log.d("MCP", "refresh 완료 → 캐시 도구 ${mcpGateway.cachedToolDeclarations().size}개")
                    }
                    text == "__MCP_LIST__" -> Log.d("MCP",
                        "서버=${mcpGateway.listServers().map { it.id }} 캐시도구=${mcpGateway.cachedToolDeclarations().size}")
                    // [DEBUG A/B] 런타임 모델 스위치: __MODEL__:gemini-2.5-pro / __MODEL__:default
                    text.startsWith("__MODEL__:") -> {
                        val m = text.removePrefix("__MODEL__:").trim()
                        com.example.ez_capstone.agent.GeminiApiClient.modelOverride =
                            if (m.isBlank() || m == "default") null else m
                        Log.d("ModelAB", "modelOverride=${com.example.ez_capstone.agent.GeminiApiClient.modelOverride ?: "default(gemini-2.5-flash)"}")
                    }
                    else -> coordinator.injectDebugCommand(text)
                }
            }
        }
        val filter = IntentFilter("com.example.ez_capstone.DEBUG_CMD")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(debugCmdReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(debugCmdReceiver, filter)
        }
    }

    /** [DEBUG] 최근 결정 기록 분석 → 반복 실패 패턴을 logcat("SelfImprove")에 출력(자가개선 입력). */
    private suspend fun runFailureAnalysis() {
        val traces = decisionTraceDao.getRecent(100)
        val report = AgentFailureAnalyzer.analyze(traces)
        Log.d("SelfImprove", "=== FailureReport: traces=${report.totalTraces}, findings=${report.findings.size} ===")
        if (report.findings.isEmpty()) Log.d("SelfImprove", "  반복 실패 패턴 없음")
        report.findings.forEach {
            Log.d("SelfImprove", "  [${it.kind}] ${it.subject} x${it.count} → ${it.suggestion}")
            it.sample?.let { e -> Log.d("SelfImprove", "      ↳ sample: ${e.take(200)}") }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coordinator.releaseMediaSession()
        debugCmdReceiver?.let { runCatching { unregisterReceiver(it) } }
        debugCmdReceiver = null
    }
}
