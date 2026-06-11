package com.example.ez_capstone.agent

import android.util.Log
import com.example.ez_capstone.agent.models.AgentDrivingState
import com.example.ez_capstone.agent.models.AgentContext
import com.example.ez_capstone.agent.models.AgentResponse
import com.example.ez_capstone.analytics.AgentAnalytics
import com.example.ez_capstone.offline.OnDeviceLlm
import com.example.ez_capstone.predictive.ContextSnapshot
import com.example.ez_capstone.predictive.PredictiveCache
import com.example.ez_capstone.db.dao.ConversationDao
import com.example.ez_capstone.governance.AgentCapability
import com.example.ez_capstone.governance.PermissionManager
import com.example.ez_capstone.governance.PermissionResult
import com.example.ez_capstone.resilience.NetworkMonitor
import com.example.ez_capstone.safety.DrivingState
import com.example.ez_capstone.safety.SafetyContext
import com.example.ez_capstone.safety.SafetyPolicyEngine
import com.example.ez_capstone.skill.PlanCacheFallbackException
import com.example.ez_capstone.skill.SkillExecutor
import com.example.ez_capstone.skill.SkillLearner
import com.example.ez_capstone.skill.SkillMatchResult
import com.example.ez_capstone.skill.SkillMatcher
import com.example.ez_capstone.security.SensitiveDataMasker
import com.example.ez_capstone.trace.DecisionTraceBuilder
import com.example.ez_capstone.trace.DecisionTraceDao
import com.example.ez_capstone.trace.TraceStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import com.example.ez_capstone.db.entity.ConversationEntity
import com.google.gson.Gson
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemini 2.5 Flash Agent Loop — 온디바이스.
 * 서버 geminiAgent.js agentChat() 포팅.
 *
 * 흐름: 유저 메시지 → Gemini 호출 → functionCall 감지 → Tool 실행 → 결과 추가 → 반복 → 최종 텍스트 반환
 */
@Singleton
class GeminiAgentEngine @Inject constructor(
    private val apiClient: GeminiApiClient,
    private val apiKeyProvider: com.example.ez_capstone.config.ApiKeyProvider,
    private val systemPrompt: SystemPrompt,
    private val toolExecutor: ToolExecutor,
    private val fallbackStrategy: FallbackStrategy,
    private val safetyPolicyEngine: SafetyPolicyEngine,
    private val permissionManager: PermissionManager,
    private val networkMonitor: NetworkMonitor,
    private val conversationDao: ConversationDao,
    private val decisionTraceDao: DecisionTraceDao,
    private val gson: Gson,
    private val sensitiveDataMasker: SensitiveDataMasker,
    private val agentAnalytics: AgentAnalytics,
    private val onDeviceLlm: OnDeviceLlm,
    private val predictiveCache: PredictiveCache,
    // L3 계층 (SELF_LEARNING_AGENT.md) — Gemini 우회 Pre-flight
    private val skillMatcher: SkillMatcher,
    private val skillExecutor: SkillExecutor,
    private val skillLearner: SkillLearner,
    // MCP 플러그인 도구 — 외부 MCP 서버 도구를 동적으로 Function Calling에 병합
    private val mcpGateway: com.example.ez_capstone.mcp.client.McpToolGateway
) {
    companion object {
        private const val TAG = "GeminiAgentEngine"
        private const val MAX_ITERATIONS = 6
        private const val AGENT_TIMEOUT_MS = 75_000L

        // Reflection 힌트 — 도구 실패 후 다음 반복에서 모델이 원인 진단·재계획하도록 유도.
        // (연구 적용: 구조화·'행동지향' 에러 메시지가 모호한 것보다 회복률이 훨씬 높다 → error_kind별 타깃 힌트.)
        private const val REFLECTION_HINT =
            "방금 일부 도구가 실패했어요. 같은 호출을 그대로 반복하지 말고, 실패 원인" +
            "(잘못된 인자·없는 데이터·좌표 누락 등)을 추정해서 다른 도구나 다른 인자로 다시 시도하세요. " +
            "빠진 인자는 대부분 컨텍스트(오늘 날짜·현재 위치·프로필·일정)에 이미 있으니 그걸로 채워 재시도하세요 — 사용자에게 되묻지 마세요. " +
            "단, 도구 결과에 needs_user_input이 있으면 그 한 가지(의미의 핵심)만 짧게 물어보세요."

        // 키/인증/한도 같은 '재시도 무의미' 에러 — 같은 도구 반복 금지, 대체수단 또는 짧은 안내로.
        private const val NON_RETRYABLE_HINT =
            "이 도구는 키 미설정·인증·요청한도 문제라 같은 호출을 다시 해도 실패합니다. 같은 도구를 재시도하지 마세요. " +
            "키 없이 되는 대체 수단(예: search_knowledge·get_weather)이 있으면 그걸 쓰고, 없으면 사용자에게 1문장으로 " +
            "(설정에서 키 등록 / 잠시 후 재시도) 짧게 안내하세요. 다른 인자로 우회 시도도 하지 마세요."

        /** error_kind에 따라 회복 가능 여부가 다르다 → 재시도 무의미 에러엔 다른 힌트를 준다. */
        fun reflectionHintFor(errorKind: String): String = when (errorKind) {
            "MISSING_KEY", "AUTH_FAILED", "RATE_LIMITED" -> NON_RETRYABLE_HINT
            else -> REFLECTION_HINT  // NETWORK / SERVER_ERROR / UPSTREAM / 빈 결과 등 — 재계획·재시도 가치 있음
        }

        // ── STT N-best 결정론 재해석 ──
        // 실측(2026-06-08): STT 대안을 프롬프트(sttSection)에 넣어도 Flash가 재해석 지시를 무시하고
        // 깨진 1순위를 그대로 답함("허리 수도"→"찾을 수 없어요"). 모델 가중치에 기대지 말고
        // 하니스가 직접 판단한다(ASR 연구의 'context re-rank' = 프롬프트에 떠넘기지 말 것).

        // '못 찾음/막다른 답' 신호 — 1순위가 빗나가 도구가 빈 결과를 낸 흔적.
        // 게이트가 이미 좁아(대안 존재 시에만 호출) 휴리스틱 오탐 위험 낮음.
        private val UNRESOLVED_MARKERS = listOf(
            "찾을 수 없", "찾지 못", "못 찾", "정보가 없", "정보는 찾", "알 수 없", "모르겠"
        )
        fun isUnresolvedReply(text: String): Boolean = UNRESOLVED_MARKERS.any { text.contains(it) }

        /**
         * STT 대안(N-best)을 어떻게 쓸지 결정. (정책: 내비/장소=조용히 재시도, 그 외=확인)
         * 1순위가 막다른 답을 냈고 대안이 있을 때만 작동 — 정상 응답엔 개입 안 함.
         */
        fun nbestActionFor(
            intent: IntentCategory,
            replyText: String,
            alternatives: List<String>
        ): NbestAction {
            val best = alternatives.firstOrNull { it.isNotBlank() } ?: return NbestAction.None
            if (!isUnresolvedReply(replyText)) return NbestAction.None
            return when (intent) {
                IntentCategory.NAVIGATION, IntentCategory.PLACES -> NbestAction.SilentRetry(best)
                else -> NbestAction.Confirm(best)
            }
        }

        // Predictive cache 단락 — 현재 폐기(항상 false).
        // 캐시 키(PredictiveCache.keyFor)가 (요일×시간대)뿐이라 목적지를 구분하지 못해,
        // "집에 가자"에 그 시간대 마지막 프리페치된 '다른 목적지' 경로를 반환하는 오답을 낸다.
        // 주행 중 무력화(모든 발화가 캐시 경로로 덮임)는 이미 막았고, IDLE의 잠재 오답(엉뚱한
        // 목적지 경로)까지 닫기 위해 단락 자체를 끈다. 목적지를 lookup 시점에 알 수 없어
        // 키로 매칭이 불가하므로 '항상 미사용'이 유일하게 옳은 동작이다.
        // (PredictiveCache 인프라/Worker는 보존 — 추후 목적지-인지 키로 재설계 시 재활성.)
        @Suppress("UNUSED_PARAMETER")
        fun shouldUsePredictiveCache(drivingState: String, userText: String): Boolean = false
    }

    /** STT N-best 결정론 처리 결과. (companion 함수 nbestActionFor가 생성) */
    sealed class NbestAction {
        data class SilentRetry(val alternative: String) : NbestAction()  // 내비/장소: 조용히 대안 재시도
        data class Confirm(val alternative: String) : NbestAction()      // 그 외: 대안으로 되물어 확인
        object None : NbestAction()
    }

    // 인메모리 대화 히스토리 (Gemini contents 배열) — thread-safe (코루틴 동시 접근 방어)
    private val conversationHistory = CopyOnWriteArrayList<JSONObject>()
    private var sessionId = UUID.randomUUID().toString()

    /**
     * 음성 명령 처리 중 플래그.
     * 백그라운드 Gemini 호출(AiDrivingAdvisor 등)이 이 플래그를 확인하고 건너뛰어
     * conversationHistory 경쟁과 응답 큐 고갈을 방지한다.
     */
    @Volatile var isVoiceCommandActive = false

    /**
     * doChatLoop 직렬화 Mutex.
     * conversationHistory는 CopyOnWriteArrayList로 단일 연산은 스레드 안전하지만,
     * "add → prune → loop → add" 복합 시퀀스는 원자적이지 않음.
     * 두 개의 chat() 호출이 히스토리를 동시에 조작하는 것을 막는다.
     * 백그라운드 호출자는 isChatBusy로 사전 확인 후 건너뜀.
     */
    private val chatMutex = Mutex()

    /** true면 doChatLoop 실행 중 — 백그라운드 호출자가 건너뛸 때 사용 */
    val isChatBusy: Boolean get() = chatMutex.isLocked

    @Volatile
    private var lastAgentRun: AgentRun? = null

    fun lastAgentRunSnapshot(): AgentRun? = lastAgentRun

    // Singleton 전용 스코프 — 무명 CoroutineScope 생성 금지 (메모리 누수 방지)
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val routePlanner = AgentRoutePlanner(systemPrompt, apiKeyProvider, mcpGateway)
    private val toolExecutionHarness = AgentToolExecutionHarness(
        toolExecutor, fallbackStrategy, agentAnalytics,
        locationPermissionGranted = {
            permissionManager.check(AgentCapability.LOCATION_ACCESS) is PermissionResult.Granted
        },
        // 외부 MCP 도구의 분류된 위험등급 → 안전 게이트가 effectful 외부 도구를 정확히 차단.
        externalRiskTier = { mcpGateway.riskTierOf(it) }
    )
    private val responseAdapter = AgentResponseAdapter()

    /**
     * 메인 에이전트 채팅.
     * @param userText 사용자 입력 텍스트
     * @param context 현재 위치, 주행 상태 등
     * @return AgentResponse (replyText, uiAction, uiData 포함)
     */
    /**
     * 에이전트 채팅 진입점. 호출자 디스패처(예: viewModelScope=Main)에서 무거운 작업
     * (프롬프트 빌드/JSON 파싱/스킬 매칭/Tool 루프)이 돌지 않도록 항상 백그라운드로 분리한다.
     * 네트워크는 내부에서 다시 Dispatchers.IO로 감싸진다. → 메인 스레드 블로킹(ANR) 방지.
     */
    suspend fun chat(userText: String, context: AgentContext): AgentResponse =
        chatWithMode(userText, context, AgentRunInputMode.TEXT)

    suspend fun chatWithMode(
        userText: String,
        context: AgentContext,
        inputMode: AgentRunInputMode
    ): AgentResponse =
        withContext(Dispatchers.Default) { chatInternal(userText, context, inputMode) }

    private suspend fun chatInternal(
        userText: String,
        context: AgentContext,
        inputMode: AgentRunInputMode
    ): AgentResponse {
        val normalizedContext = context.copy(
            drivingState = AgentDrivingState.normalize(context.drivingState)
        )
        val runRecorder = AgentRunRecorder.start(userText, normalizedContext, inputMode)

        // 데모 모드 — 발표/쇼케이스 시 네트워크 없이도 동작
        if (apiKeyProvider.isDemoMode) {
            val demo = DemoResponseRepository.match(userText)
            if (demo != null) {
                Log.d(TAG, "[DEMO] Returning pre-written response for: $userText")
                runRecorder.recordRoute("demo")
                return finishAgentRun(runRecorder, demo)
            }
            // 매칭 없으면 fallthrough → 실제 API 호출
        }

        // 오프라인 체크
        if (!networkMonitor.isOnline.value) {
            val response = onDeviceLlm.handleOffline(userText, normalizedContext)
            runRecorder.recordRoute("offline_on_device_llm")
            return finishAgentRun(runRecorder, response)
        }

        // Predictive cache — 출발 전(IDLE)의 '예측 경로' 요청에만 적용한다.
        // 캐시 키(PredictiveCache.keyFor)는 (요일×시간대)뿐이라 목적지·발화를 구분하지 못한다.
        // 주행 중(navigating)에 켜두면 "주유소/날씨/문자" 등 모든 발화가 캐시된 경로 응답으로
        // 덮여 에이전트가 무력화된다(주행 중 전 기능이 '엉뚱한 경로 답'만 반복하던 근본 원인).
        // → 주행 중에는 절대 단락하지 않고, IDLE + 명시적 경로 요청일 때만 프리워밍 캐시를 쓴다.
        if (shouldUsePredictiveCache(normalizedContext.drivingState, userText)) {
            val now = java.util.Calendar.getInstance()
            predictiveCache.getCachedPrediction(
                ContextSnapshot(
                    hourOfDay = now.get(java.util.Calendar.HOUR_OF_DAY),
                    dayOfWeek = now.get(java.util.Calendar.DAY_OF_WEEK),
                    isCharging = false,
                    isWifi = false,
                    latitude = normalizedContext.locationY ?: 0.0,
                    longitude = normalizedContext.locationX ?: 0.0
                )
            )?.let { cached ->
                Log.d(TAG, "Predictive cache HIT — returning cached nav response")
                runRecorder.recordRoute("predictive_cache")
                return finishAgentRun(runRecorder, cached)
            }
        }

        // ── L3: LearnedSkill / Template Pre-flight (SELF_LEARNING_AGENT.md) ──
        // Gemini 우회 — 일치하는 Skill이 있으면 즉시 실행. 실패 시 Gemini 경로로 폴백.
        runCatching {
            val safetyCtx = SafetyContext(
                drivingState = DrivingState.fromAgentState(normalizedContext.drivingState),
                hasGps = normalizedContext.locationX != null && normalizedContext.locationY != null &&
                    !(normalizedContext.locationX == 0.0 && normalizedContext.locationY == 0.0)
            )
            skillMatcher.tryMatch(userText, safetyCtx)
        }.onSuccess { match ->
            if (match != null) {
                Log.d(TAG, "[L3] Skill hit: source=${match.source} id=${match.id()} score=${match.score}")
                runRecorder.recordRoute("l3_${match.source.name.lowercase()}")
                try {
                    val response = skillExecutor.execute(match, normalizedContext, runRecorder)
                    // Template 매칭 시 즉시 LearnedSkill로 승격 (결정 4: 2회 조건 면제)
                    if (match.source == SkillMatchResult.Source.TEMPLATE) {
                        engineScope.launch {
                            runCatching {
                                skillLearner.promoteFromTemplate(userText, match, decisionTraceId = null)
                            }.onFailure { Log.w(TAG, "Template promotion failed: ${it.message}") }
                        }
                    }
                    agentAnalytics.recordConversation()
                    // Phase B-2: L3 hit tier별 계측 (fingerprint/slot 등 raw 데이터는 로깅 금지)
                    val tier = match.learnedSkill?.maxToolRiskTier
                        ?: match.template?.maxToolRiskTier
                        ?: "SAFE"
                    agentAnalytics.recordL3Hit(tier)
                    // 성공 피드백 (명시적 실패는 없으므로 기본 positive)
                    agentAnalytics.recordLearnedSkillFeedback(positive = true)
                    return finishAgentRun(runRecorder, response)
                } catch (e: PlanCacheFallbackException) {
                    runRecorder.recordFallback("l3_plan_cache_fallback", e.message)
                    if (match.source == SkillMatchResult.Source.LEARNED_SKILL) {
                        agentAnalytics.recordLearnedSkillFeedback(positive = false)
                    }
                    Log.w(TAG, "[L3] Skill execution fallback, continuing to Gemini: ${e.message}")
                }
            }
        }.onFailure {
            runRecorder.recordFallback("skill_matcher_error", it.message)
            Log.w(TAG, "SkillMatcher error, falling back to Gemini: ${it.message}")
        }

        return try {
            withTimeout(AGENT_TIMEOUT_MS) {
                chatMutex.withLock {
                    doChatLoop(userText, normalizedContext, runRecorder)
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w(TAG, "Agent timeout")
            val response = AgentResponse(
                replyText = "처리 시간이 초과되었습니다. 좀 더 구체적으로 말씀해주시면 빠르게 도와드릴게요.",
                uiAction = "none"
            )
            runRecorder.recordFallback("timeout", e.message)
            finishAgentRun(runRecorder, response)
        } catch (e: GeminiApiException.InvalidKey) {
            val response = AgentResponse(
                replyText = "API 키가 유효하지 않습니다. 설정에서 Gemini 키를 확인해주세요.",
                uiAction = "none"
            )
            runRecorder.recordFallback("invalid_key", e.message)
            finishAgentRun(runRecorder, response)
        } catch (e: GeminiApiException.RateLimited) {
            val response = AgentResponse(
                replyText = "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.",
                uiAction = "none"
            )
            runRecorder.recordFallback("rate_limited", e.message)
            finishAgentRun(runRecorder, response)
        } catch (e: GeminiApiException.ServerError) {
            Log.w(TAG, "Gemini server error ${e.code} (재시도 후에도 실패)")
            // 사용자에게 오류 코드(503 등)를 노출하지 않는다("에러를 사용자에게 보이지 마라").
            // apiClient가 이미 5xx/429를 지수 백오프로 자동 재시도하므로, 여기 도달은 재시도 소진 후.
            val response = AgentResponse(
                replyText = "지금 연결이 잠깐 불안정해요. 잠시 후 다시 시도해 드릴게요.",
                uiAction = "none"
            )
            runRecorder.recordFallback("server_error", e.message)
            finishAgentRun(runRecorder, response)
        } catch (e: Exception) {
            Log.e(TAG, "Agent error: ${e.message}", e)
            val response = AgentResponse(
                replyText = "요청 처리 중 문제가 발생했습니다. 잠시 후 다시 시도해주세요.",
                uiAction = "none"
            )
            runRecorder.recordFallback("agent_error", e.message)
            finishAgentRun(runRecorder, response)
        }
    }

    private fun finishAgentRun(recorder: AgentRunRecorder, response: AgentResponse): AgentResponse {
        lastAgentRun = recorder.finish(response)
        return response
    }

    /**
     * 스트리밍 채팅 — Tool Call Loop를 완료한 후 최종 텍스트를 청크 단위로 emit.
     * UI에서 collect하여 Streaming 상태로 실시간 렌더링 가능.
     */
    fun chatStream(userText: String, context: AgentContext): Flow<String> = flow {
        val response = chat(userText, context)
        response.replyText.chunked(3).forEach { chunk ->
            emit(chunk)
            delay(20)
        }
    }

    /** 새 대화 세션 시작 */
    fun newSession() {
        // chatMutex 밖 clear()는 진행 중인 doChatLoop와 경쟁 — engineScope에서 직렬화
        engineScope.launch { chatMutex.withLock {
            conversationHistory.clear()
            sessionId = UUID.randomUUID().toString()
        } }
    }

    // ── 내부 Agent Loop ──

    private suspend fun doChatLoop(
        userText: String,
        context: AgentContext,
        runRecorder: AgentRunRecorder
    ): AgentResponse {
        // 권한 게이트: 위치 접근이 차단되어 있으면 안내 — (0.0, 0.0) 더미 좌표는 유효하지 않음
        val hasValidLocation = context.locationX != null && context.locationY != null &&
            !(context.locationX == 0.0 && context.locationY == 0.0)
        if (hasValidLocation && permissionManager.check(AgentCapability.LOCATION_ACCESS) is PermissionResult.Denied) {
            val response = AgentResponse(
                replyText = "위치 접근이 차단되어 있어요. 설정에서 허용해주세요.",
                uiAction = "none"
            )
            runRecorder
                .recordRoute("permission_gate")
                .recordPermissionDecision("LOCATION_ACCESS", allowed = false)
            return finishAgentRun(runRecorder, response)
        }

        agentAnalytics.recordConversation()

        val agentStart = System.currentTimeMillis()
        val traceBuilder = DecisionTraceBuilder(sessionId, userText)
        runRecorder.recordRoute(AgentRoutePlanner.GEMINI_FUNCTION_LOOP)

        val plan = routePlanner.planGeminiFunctionLoop(userText, context, sessionId)
        val intent = plan.intent
        val prompt = plan.prompt
        val tools = plan.tools
        Log.d(TAG, "Intent detected: $intent for: \"${userText.take(30)}\"")
        runRecorder.recordObservation("planner", plan.routeLabel, "intent=$intent")

        // 유저 메시지를 히스토리에 추가 (참조 보관 — 취소 시 롤백용)
        val userHistoryMsg = JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", userText)))
        }
        conversationHistory.add(userHistoryMsg)

        // 대화 기록 영속화
        persistTurn("user", userText, null, null)

        // 히스토리 윈도우: 최근 6턴만 유지 (토큰 절약)
        pruneHistory()

        // Tool 결과 저장소 (UI용 전체 데이터)
        val toolResultStore = mutableMapOf<String, Any>()
        val toolsUsed = Collections.synchronizedList(mutableListOf<String>())
        // 이번 턴 성공(에러 없이 실행)한 도구 — grounding 위반 측정용
        val succeededTools = Collections.synchronizedSet(mutableSetOf<String>())

        try {
        for (i in 0 until MAX_ITERATIONS) {
            val tLlm = System.currentTimeMillis()

            // 강제 그라운딩(근본 처방): 첫 턴 + 구체 의도(AUTO 아님)면 도구 호출을 강제한다.
            // → 에이전트가 프롬프트 안에서 "추가했습니다"를 지어내거나 "없습니다"로 거절하지 못하고,
            //   반드시 실제 도구를 호출해 앱을 건드린 뒤(수행/조회) 그 결과로 답한다.
            //   tools는 이미 intent로 한정돼 있어 ANY의 호출 범위도 자동으로 좁혀진다.
            //   2번째 턴부터는 AUTO → 도구 결과를 종합해 최종 답변 생성(요약 턴까지 강제하지 않음).
            val forceMode = routePlanner.forceModeForIteration(i, intent)

            // Gemini API 호출
            val (parts, _) = apiClient.generateContent(prompt, conversationHistory, tools, forceMode)
            val llmDuration = System.currentTimeMillis() - tLlm
            Log.d(TAG, "[Perf] LLM call #${i + 1}: ${llmDuration}ms")
            traceBuilder.addStep(TraceStep(iteration = i, type = "llm_call", durationMs = llmDuration))
            runRecorder.recordObservation("llm_call", "iteration=${i + 1}", "durationMs=$llmDuration")

            // functionCall 파트 추출
            val functionCalls = parts.filter { it.has("functionCall") }

            if (functionCalls.isEmpty()) {
                // 최종 텍스트 응답
                var textResponse = parts
                    .filter { it.has("text") }
                    .joinToString("") { it.optString("text", "") }

                // 빈 응답 방어
                if (textResponse.isBlank()) {
                    Log.w(TAG, "Empty text response from Gemini — using fallback")
                    textResponse = """{"reply_text":"죄송해요, 다시 한번 말씀해주세요.","ui_action":"none","ui_data":{}}"""
                }

                // 모델 응답을 히스토리에 추가
                conversationHistory.add(JSONObject().apply {
                    put("role", "model")
                    put("parts", JSONArray().put(JSONObject().put("text", textResponse)))
                })

                val elapsed = System.currentTimeMillis() - agentStart
                Log.d(TAG, "[Perf] Agent total: ${elapsed}ms (${i + 1} iterations)")

                val normalized = responseAdapter.adapt(textResponse, userText, toolResultStore)
                val preResponse = normalized.copy(toolsUsed = toolsUsed)

                // 안전 정책 필터
                val safetyCtx = SafetyContext(
                    drivingState = DrivingState.fromAgentState(context.drivingState),
                    hasGps = context.locationX != null && context.locationY != null &&
                        !(context.locationX == 0.0 && context.locationY == 0.0)
                )
                val (safetyDecision, evaluated) = safetyPolicyEngine.evaluate(preResponse, safetyCtx)
                runRecorder.recordSafetyDecision(safetyDecision::class.simpleName ?: "Unknown")

                // 의사결정 근거(설명가능성): trace를 동기 빌드해 사람이 읽을 요약을 응답에 부착.
                // 같은 trace 객체를 비동기 저장에도 재사용 → 중복 빌드 없음.
                val trace = traceBuilder.build(
                    iterationCount = i + 1,
                    safetyDecision = safetyDecision::class.simpleName
                )
                runRecorder.linkDecisionTrace(trace)
                var finalResponse = evaluated.copy(explanation = trace.toHumanSummary())

                // [N-best 결정론] 1순위가 막다른 답 + STT 대안 존재 시 하니스가 직접 처리.
                // SilentRetry는 chatMutex 안에서 이미 도는 doChatLoop를 '직접' 재귀(재진입 데드락 회피),
                // 대안을 비워 깊이 1로 제한. Confirm은 응답을 되묻기로 교체.
                when (val nb = nbestActionFor(intent, finalResponse.replyText, context.sttAlternatives)) {
                    is NbestAction.SilentRetry -> {
                        Log.i(TAG, "AgentAB-Nbest | silentRetry intent=$intent → '${nb.alternative.take(24)}'")
                        return doChatLoop(nb.alternative, context.copy(sttAlternatives = emptyList()), runRecorder)
                    }
                    is NbestAction.Confirm -> {
                        Log.i(TAG, "AgentAB-Nbest | confirm → '${nb.alternative.take(24)}'")
                        finalResponse = finalResponse.copy(
                            replyText = "혹시 '${nb.alternative}' 말씀이신가요? 맞으면 한 번만 더 말씀해 주세요.",
                            ttsText = "혹시, ${nb.alternative}, 맞나요?"
                        )
                    }
                    NbestAction.None -> {}
                }

                // 그라운딩 위반 관측(측정 전용, 거절/차단 X): 완료 주장인데 성공 WRITE 도구가
                // 없으면 = 도구 없이 지어낸 거짓 → 로그로 가시화(회귀 게이트). 강제 그라운딩이
                // 정상이면 거의 발생하지 않아야 함.
                val sTools = synchronized(succeededTools) { succeededTools.toSet() }
                if (GroundingGuard.isViolation(finalResponse.replyText, sTools)) {
                    Log.w(TAG, "GroundingViolation: 완료주장+WRITE도구0 | reply='${finalResponse.replyText.take(50)}' tools=$sTools")
                }

                // Trace 비동기 저장 (응답 지연 없음) — engineScope 재사용으로 누수 방지
                engineScope.launch {
                    try {
                        decisionTraceDao.insert(trace)
                    } catch (e: Exception) {
                        Log.e(TAG, "Trace persist failed: ${e.message}")
                    }
                }

                // 영속화
                persistTurn("agent", finalResponse.replyText, finalResponse.uiAction, toolsUsed)

                agentAnalytics.recordChainDepth(i + 1)
                // [A/B 캡처] 모델·도구·최종응답을 한 줄로 — logcat grep "AgentAB" 로 비교 수집
                Log.i(TAG, "AgentAB | model=${GeminiApiClient.activeModel} | intent=$intent | tools=[${toolsUsed.joinToString(",")}] | reply='${finalResponse.replyText.take(120)}'")
                return finishAgentRun(runRecorder, finalResponse)
            }

            // functionCall 있음 — 모델 응답(functionCall 포함)을 히스토리에 추가
            Log.d(TAG, "Iteration ${i + 1}: ${functionCalls.size} tool call(s): ${
                functionCalls.joinToString { it.getJSONObject("functionCall").optString("name") }
            }")

            conversationHistory.add(JSONObject().apply {
                put("role", "model")
                put("parts", JSONArray().apply { parts.forEach { put(it) } })
            })

            val toolResponses = toolExecutionHarness.executeAll(
                functionCalls = functionCalls,
                iteration = i,
                context = context,
                traceBuilder = traceBuilder,
                runRecorder = runRecorder,
                toolResultStore = toolResultStore,
                toolsUsed = toolsUsed,
                succeededTools = succeededTools
            )

            // Reflection(자기반성): 실패한 functionResponse의 response 객체 안에 재계획 힌트를 주입.
            // (별도 text part를 functionResponse 턴에 섞으면 일부 Gemini 버전에서 400을 유발하므로,
            //  도구 결과의 일부로 전달해 모델이 원인 추정→대안 재계획하도록 유도.)
            // 마지막 반복 직전엔 생략 — 재시도 여력이 없음.
            if (i < MAX_ITERATIONS - 1) {
                var injected = false
                toolResponses.forEach { tr ->
                    val resp = tr.optJSONObject("functionResponse")?.optJSONObject("response")
                    if (resp != null && resp.has("error")) {
                        resp.put("reflection_hint", reflectionHintFor(resp.optString("error_kind", "UNCLASSIFIED")))
                        injected = true
                    }
                }
                if (injected) Log.d(TAG, "Reflection hint injected (iteration ${i + 1}) — tool error detected")
            }

            // Tool 결과를 유저 턴으로 히스토리에 추가 (functionResponse parts만 — Gemini 규격 준수)
            conversationHistory.add(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply { toolResponses.forEach { put(it) } })
            })
        }

        // Max iteration 도달
        Log.w(TAG, "Max iterations reached")
        val response = AgentResponse(
            replyText = "처리 중 문제가 발생했습니다. 다시 시도해주세요.",
            uiAction = "none"
        )
        runRecorder.recordFallback("max_iterations", "Reached $MAX_ITERATIONS iterations")
        return finishAgentRun(runRecorder, response)
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 코루틴 취소 시 고아 user 턴 제거 — Gemini는 user-model 교대 규칙을 강제하므로
            // 응답 없는 user 항목이 남으면 다음 호출에서 400 Bad Request 발생.
            conversationHistory.remove(userHistoryMsg)
            throw e
        }
    }

    // ── 영속화 ──

    private suspend fun persistTurn(
        role: String,
        text: String,
        uiAction: String?,
        toolsUsed: List<String>?
    ) {
        try {
            conversationDao.insertMessage(ConversationEntity(
                sessionId = sessionId,
                role = role,
                text = sensitiveDataMasker.mask(text),
                uiAction = uiAction,
                toolsUsed = toolsUsed?.joinToString(","),
                createdAt = System.currentTimeMillis()
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist turn: ${e.message}")
        }
    }

    // ── 히스토리 관리 ──

    /**
     * 최근 MAX_HISTORY_PAIRS 사용자 턴만 유지하여 토큰 절약.
     * functionCall-functionResponse 쌍이 깨지지 않도록 user turn 경계에서만 자른다.
     * Gemini 규칙: model(functionCall) 바로 다음에 user(functionResponse)가 와야 함.
     */
    private fun pruneHistory() {
        // H5: 6→8. "방금 그 카페/거기" 같은 참조가 몇 개 명령 뒤에도 살아남도록 윈도를 약간 넓힘.
        // (장소 엔티티 전용 메모리의 경량 대체 — 토큰 증가는 미미.)
        val maxPairs = 8

        // 순수 "user" 메시지 (functionResponse가 아닌) 인덱스만 수집
        val userTextIndices = conversationHistory.indices.filter { idx ->
            val entry = conversationHistory[idx]
            if (entry.optString("role") != "user") return@filter false
            // functionResponse parts가 있으면 이건 tool 결과 turn이므로 제외
            val parts = entry.optJSONArray("parts")
            if (parts == null || parts.length() == 0) return@filter true
            !parts.getJSONObject(0).has("functionResponse")
        }

        if (userTextIndices.size > maxPairs) {
            val removeCount = userTextIndices.size - maxPairs
            val removeUntil = userTextIndices[removeCount]
            Log.d(TAG, "pruneHistory: removing $removeUntil entries (keeping last $maxPairs user turns)")
            // CopyOnWriteArrayList.subList().clear() throws UnsupportedOperationException — remove one by one
            repeat(removeUntil) { conversationHistory.removeAt(0) }
        }
    }

}
