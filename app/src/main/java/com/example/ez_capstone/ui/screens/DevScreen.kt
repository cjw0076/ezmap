package com.example.ez_capstone.ui.screens

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.example.ez_capstone.analytics.AgentAnalytics
import com.example.ez_capstone.config.ApiKeyProvider
import com.example.ez_capstone.eval.AgentEvaluator
import com.example.ez_capstone.eval.EvalReport
import com.example.ez_capstone.trace.DecisionTraceDao
import com.example.ez_capstone.trace.DecisionTraceEntity
import com.example.ez_capstone.ui.theme.*
import com.example.ez_capstone.viewmodel.DbTable
import com.example.ez_capstone.viewmodel.DbViewerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 개발자 디버그 페이지.
 * API 키 상태, 화면 이동, 시스템 정보 확인용.
 */
@Composable
fun DevScreen(
    apiKeyProvider: ApiKeyProvider,
    decisionTraceDao: DecisionTraceDao? = null,
    agentAnalytics: AgentAnalytics? = null,
    agentEvaluator: AgentEvaluator? = null,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    // Decision Trace 로드
    var traces by remember { mutableStateOf<List<DecisionTraceEntity>>(emptyList()) }
    LaunchedEffect(Unit) {
        decisionTraceDao?.let {
            traces = withContext(Dispatchers.IO) { it.getRecent(10) }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "DEV MODE",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Green,
                fontFamily = Orbitron,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onBack) {
                Text("닫기", color = TextSecondary)
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── API Key Status ──
        SectionHeader("API KEYS")
        ApiKeyRow("Gemini", apiKeyProvider.activeGeminiKey.isNotBlank())
        ApiKeyRow("Kakao REST", apiKeyProvider.activeKakaoKey.isNotBlank())
        ApiKeyRow("data.go.kr", apiKeyProvider.isPublicDataConfigured)
        ApiKeyRow("오피넷", apiKeyProvider.opinetKey.isNotBlank())
        ApiKeyRow("네이버", apiKeyProvider.naverClientId.isNotBlank())
        ApiKeyRow("ODsay", apiKeyProvider.odsayKey.isNotBlank())
        ApiKeyRow("Trial Mode", apiKeyProvider.isTrialMode, isMode = true)
        ApiKeyRow("Trial Count", false, extra = "${apiKeyProvider.trialUsageCount}/${ApiKeyProvider.TRIAL_DAILY_LIMIT}")

        Spacer(Modifier.height(20.dp))

        // ── Enabled Features ──
        SectionHeader("ENABLED FEATURES")
        val features = apiKeyProvider.getEnabledFeatures()
        if (features.isEmpty()) {
            Text("활성화된 기능 없음", color = Red, fontSize = 13.sp)
        } else {
            Text(
                features.joinToString(" · "),
                color = Cyan,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        }

        Spacer(Modifier.height(20.dp))

        // ── Screen Navigation ──
        SectionHeader("SCREENS")
        val screens = listOf(
            "conversation" to "ConversationScreen (메인)",
            "navigation" to "NavigationScreen (내비)",
            "profile" to "ProfileScreen (프로필)",
            "schedule" to "ScheduleScreen (일정)",
            "settings" to "SettingsScreen (설정)",
            "onboarding" to "OnboardingScreen (온보딩)",
            "landing" to "LandingScreen (랜딩)",
            "skill_store" to "SkillStore (스킬 마켓)",
        )
        screens.forEach { (route, label) ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clickable { onNavigate(route) },
                shape = RoundedCornerShape(8.dp),
                color = Surface
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(route, color = Cyan, fontSize = 13.sp, fontFamily = Rajdhani, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(label, color = TextSecondary, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── System Info ──
        SectionHeader("SYSTEM")
        InfoRow("Package", "com.example.ez_capstone")
        InfoRow("isConfigured", apiKeyProvider.isConfigured.toString())
        InfoRow("isReady", apiKeyProvider.isReady.toString())
        InfoRow("onboardingCompleted", apiKeyProvider.onboardingCompleted.toString())

        // ── Decision Trace (Phase 1.3.5) ──
        if (traces.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            SectionHeader("DECISION TRACES")
            traces.forEach { trace ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Surface
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            trace.requestText,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${trace.totalDurationMs}ms", color = Accent, fontSize = 11.sp, fontFamily = Rajdhani)
                            Text("${trace.iterationCount}iter", color = TextSecondary, fontSize = 11.sp)
                            if (trace.toolsUsed.isNotBlank()) {
                                Text(trace.toolsUsed, color = TextDim, fontSize = 10.sp, maxLines = 1)
                            }
                        }
                        trace.safetyDecision?.let {
                            if (it != "Allow") {
                                Text("Safety: $it", color = Warning, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }

        // ── Agent Analytics (Phase 5.9) ──
        agentAnalytics?.let { analytics ->
            val stats = remember { analytics.getTodayStats() }
            Spacer(Modifier.height(20.dp))
            SectionHeader("AGENT ANALYTICS")
            InfoRow("오늘 대화", "${stats.conversations}회")
            InfoRow("음성 사용", "${stats.voiceCount}회")
            InfoRow("평균 체이닝", "${"%.1f".format(stats.avgChainDepth)}단계")
            if (stats.skillCallsTop5.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "SKILL TOP 5",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontFamily = Rajdhani,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                stats.skillCallsTop5.forEachIndexed { i, (name, count) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            "${i + 1}. $name",
                            color = Cyan,
                            fontSize = 12.sp,
                            fontFamily = Rajdhani,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${count}회",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontFamily = Rajdhani
                        )
                    }
                }
            }
        }

        // ── Agent Evaluation (Phase 5.6) ──
        agentEvaluator?.let { evaluator ->
            Spacer(Modifier.height(20.dp))
            SectionHeader("EVALUATION")

            var evalReport by remember { mutableStateOf<EvalReport?>(null) }
            var evalRunning by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            Button(
                onClick = {
                    if (!evalRunning) {
                        evalRunning = true
                        scope.launch(Dispatchers.IO) {
                            val report = evaluator.runAll()
                            withContext(Dispatchers.Main) {
                                evalReport = report
                                evalRunning = false
                            }
                        }
                    }
                },
                enabled = !evalRunning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (evalRunning) SurfaceHigh else Accent.copy(alpha = 0.2f),
                    contentColor = if (evalRunning) TextSecondary else Accent
                )
            ) {
                Text(
                    if (evalRunning) "평가 중..." else "전체 시나리오 평가",
                    fontFamily = Rajdhani,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            evalReport?.let { report ->
                Spacer(Modifier.height(10.dp))
                // Summary row
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceHigh
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        InfoRow("총 시나리오", "${report.totalScenarios}개")
                        InfoRow("통과", "${report.passed}개")
                        InfoRow("실패", "${report.failed}개")
                        InfoRow("점수", "${"%.0f".format(report.overallScore * 100)}%")
                    }
                }
                // Individual results (failed only to keep it concise)
                val failedResults = report.results.filter { !it.passed }
                if (failedResults.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "실패 항목",
                        fontSize = 11.sp,
                        color = Red,
                        fontFamily = Rajdhani,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    failedResults.take(5).forEach { result ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = SurfaceHigh
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    result.scenarioId,
                                    color = Red,
                                    fontSize = 11.sp,
                                    fontFamily = Rajdhani,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    result.failureReasons.joinToString(" / "),
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── STT Diagnostics ──
        Spacer(Modifier.height(20.dp))
        SectionHeader("STT DIAGNOSTICS")
        val ctx = LocalContext.current

        // 정적 상태 표시
        val apiLevel = Build.VERSION.SDK_INT
        val isRecogAvailable = SpeechRecognizer.isRecognitionAvailable(ctx)
        val cm = ctx.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val isNetworkOk = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        InfoRow("Android API", "API $apiLevel (min 26, R=30)")
        InfoRow("isRecognitionAvailable", if (isRecogAvailable) "YES" else "NO ← 원인")
        InfoRow("Network", if (isNetworkOk) "OK" else "OFFLINE ← Vosk 폴백")
        InfoRow("Attribution API", if (apiLevel >= Build.VERSION_CODES.R) "지원 (API $apiLevel)" else "미지원 → 수정됨 (API $apiLevel)")

        Spacer(Modifier.height(8.dp))

        // STT 즉시 테스트 버튼
        var sttStatus by remember { mutableStateOf("대기 중") }
        var sttResult by remember { mutableStateOf("") }
        var sttTesting by remember { mutableStateOf(false) }
        var sttRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

        DisposableEffect(Unit) {
            onDispose { sttRecognizer?.destroy() }
        }

        Button(
            onClick = {
                if (!sttTesting) {
                    sttTesting = true
                    sttStatus = "듣는 중... (5초)"
                    sttResult = ""

                    val recognizer = SpeechRecognizer.createSpeechRecognizer(ctx)
                    sttRecognizer = recognizer
                    recognizer.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(p: Bundle?) { sttStatus = "마이크 준비됨 — 말해보세요" }
                        override fun onBeginningOfSpeech() { sttStatus = "음성 감지됨" }
                        override fun onRmsChanged(r: Float) {}
                        override fun onBufferReceived(b: ByteArray?) {}
                        override fun onEndOfSpeech() { sttStatus = "분석 중..." }
                        override fun onError(code: Int) {
                            val msg = when (code) {
                                SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO(3): 마이크 충돌"
                                SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT(5): 클라이언트 오류"
                                SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK(2): 네트워크 오류"
                                SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH(7): 인식 실패"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT(6): 무음"
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY(8): 인식기 사용 중"
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_PERMISSIONS(9): 권한 없음"
                                else -> "ERROR($code)"
                            }
                            sttStatus = msg
                            sttTesting = false
                        }
                        override fun onResults(results: Bundle?) {
                            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                            sttStatus = "성공"
                            sttResult = text ?: "(빈 결과)"
                            sttTesting = false
                        }
                        override fun onPartialResults(partial: Bundle?) {
                            val text = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                            if (!text.isNullOrBlank()) sttStatus = "인식 중: $text"
                        }
                        override fun onEvent(t: Int, p: Bundle?) {}
                    })

                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    }
                    recognizer.startListening(intent)
                }
            },
            enabled = !sttTesting && isRecogAvailable,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (sttTesting) SurfaceHigh else Cyan.copy(alpha = 0.2f),
                contentColor = if (sttTesting) TextSecondary else Cyan
            )
        ) {
            Text(
                if (sttTesting) "테스트 중..." else "STT 즉시 테스트",
                fontFamily = Rajdhani, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
            )
        }

        if (sttStatus != "대기 중") {
            Spacer(Modifier.height(6.dp))
            Surface(shape = RoundedCornerShape(8.dp), color = SurfaceHigh) {
                Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                    Text("상태: $sttStatus", color = if (sttStatus.startsWith("성공")) Green else if (sttStatus.startsWith("ERROR")) Red else Cyan, fontSize = 13.sp)
                    if (sttResult.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text("결과: \"$sttResult\"", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // ── DB 뷰어 ─────────────────────────────────────────────
        val dbVm: DbViewerViewModel = hiltViewModel()
        val dbTables by dbVm.tables.collectAsState()
        val dbLoading by dbVm.loading.collectAsState()
        val clipboard = LocalClipboardManager.current

        SectionHeader("DB VIEWER")
        Button(
            onClick = { dbVm.load() },
            colors = ButtonDefaults.buttonColors(
                containerColor = Purple.copy(alpha = 0.2f),
                contentColor = Purple
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (dbLoading) "로딩 중..." else "DB 조회",
                fontFamily = Rajdhani, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
            )
        }

        dbTables.forEach { table ->
            Spacer(Modifier.height(10.dp))
            DbTableView(table = table, onCopy = { text ->
                clipboard.setText(AnnotatedString(text))
            })
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun DbTableView(table: DbTable, onCopy: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SurfaceHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    table.name,
                    color = Cyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Rajdhani
                )
                Text(
                    "복사",
                    color = Purple,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable {
                        val all = table.rows.joinToString("\n") { row ->
                            row.entries.joinToString(" | ") { "${it.key}=${it.value}" }
                        }
                        onCopy("=== ${table.name} ===\n$all")
                    }
                )
            }
            Spacer(Modifier.height(6.dp))

            if (table.rows.isEmpty()) {
                Text("(데이터 없음)", color = TextSecondary, fontSize = 12.sp)
            } else {
                val cols = table.rows.first().keys.toList()
                // 헤더
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    cols.forEach { col ->
                        Text(
                            col,
                            color = Purple,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .width(100.dp)
                                .border(0.5.dp, Purple.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                // 행
                table.rows.forEach { row ->
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        cols.forEach { col ->
                            Text(
                                row[col] ?: "-",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .width(100.dp)
                                    .border(0.5.dp, Purple.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = Purple,
        fontFamily = Orbitron,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun ApiKeyRow(name: String, isSet: Boolean, isMode: Boolean = false, extra: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (extra != null) {
            Text(extra, color = Cyan, fontSize = 13.sp, fontFamily = Rajdhani)
        } else {
            Text(
                if (isSet) "✅" else "❌",
                fontSize = 14.sp,
                color = if (isSet) Green else Red
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(label, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextPrimary, fontSize = 12.sp, fontFamily = Rajdhani)
    }
}
