package com.example.ez_capstone.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.ez_capstone.AgentUiState
import com.example.ez_capstone.CardType
import com.example.ez_capstone.agent.GeminiAgentEngine
import com.example.ez_capstone.agent.GeminiApiException
import com.example.ez_capstone.agent.UiDataParser
import com.example.ez_capstone.agent.models.AgentContext
import com.example.ez_capstone.agent.models.AgentDrivingState
import com.example.ez_capstone.models.ChatUiMessage
import com.example.ez_capstone.server.models.AgentCardPayload
import com.example.ez_capstone.server.models.PlaceItem
import com.example.ez_capstone.server.models.Coord
import com.example.ez_capstone.server.models.Guide
import com.example.ez_capstone.server.models.RouteItem
import com.example.ez_capstone.server.models.ScheduleActionPayload
import com.example.ez_capstone.server.models.ScheduleEvent
import com.example.ez_capstone.config.ApiKeyProvider
import com.example.ez_capstone.db.dao.ProfileDao
import com.example.ez_capstone.db.dao.RouteHistoryDao
import com.example.ez_capstone.db.dao.ScheduleDao
import com.example.ez_capstone.multimodal.VisionProcessor
import com.example.ez_capstone.skill.SkillLifecycleManager
import com.example.ez_capstone.skill.SkillStatus
import com.example.ez_capstone.trace.DecisionTraceDao
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ConversationViewModel @Inject constructor(
    private val agentEngine: GeminiAgentEngine,
    private val skillLifecycleManager: SkillLifecycleManager,
    private val decisionTraceDao: DecisionTraceDao,
    private val scheduleDao: ScheduleDao,
    private val gson: Gson,
    private val visionProcessor: VisionProcessor,
    private val profileDao: ProfileDao,
    private val routeHistoryDao: RouteHistoryDao,
    private val apiKeyProvider: ApiKeyProvider,
    private val liveFunctionCallBridge: com.example.ez_capstone.voice.LiveFunctionCallBridge,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val statsPrefs by lazy {
        appContext.getSharedPreferences("ez_stats", Context.MODE_PRIVATE)
    }

    data class QuickDestination(val label: String, val address: String)

    data class AmbientData(
        val nextEventTitle: String? = null
    )

    private val _quickDestinations = MutableStateFlow<List<QuickDestination>>(emptyList())
    val quickDestinations: StateFlow<List<QuickDestination>> = _quickDestinations.asStateFlow()

    private val _ambientData = MutableStateFlow(AmbientData())
    val ambientData: StateFlow<AmbientData> = _ambientData.asStateFlow()

    private fun loadAmbientData() {
        viewModelScope.launch {
            try {
                val events = scheduleDao.getActive()
                _ambientData.value = AmbientData(
                    nextEventTitle = events.firstOrNull()?.title
                )
            } catch (_: Exception) {}
        }
    }

    fun loadQuickDestinations() {
        viewModelScope.launch {
            val profile = profileDao.getProfile()
            val recent = routeHistoryDao.getRecent(3).mapNotNull {
                val name = it.destName?.trim().orEmpty()
                if (name.isBlank()) null else QuickDestination(name, name)
            }
            val fixed = mutableListOf<QuickDestination>()
            if (!profile?.homeAddress.isNullOrBlank())
                fixed.add(QuickDestination("집", profile!!.homeAddress!!))
            if (!profile?.workAddress.isNullOrBlank())
                fixed.add(QuickDestination("회사", profile!!.workAddress!!))
            _quickDestinations.value = fixed + recent
        }
    }

    // 대화 메시지
    private val _messages = MutableStateFlow<List<ChatUiMessage>>(emptyList())
    val messages: StateFlow<List<ChatUiMessage>> = _messages

    // 에이전트 UI 상태
    private val _uiState = MutableStateFlow<AgentUiState>(AgentUiState.Idle)
    val uiState: StateFlow<AgentUiState> = _uiState

    // 입력 가능 여부
    private val _inputEnabled = MutableStateFlow(true)
    val inputEnabled: StateFlow<Boolean> = _inputEnabled

    // 실데이터: Skill 활성 수, 최근 trace, 일정 수
    private val _activeSkillCount = MutableStateFlow(0)
    val activeSkillCount: StateFlow<Int> = _activeSkillCount

    private val _lastTraceSummary = MutableStateFlow<String?>(null)
    val lastTraceSummary: StateFlow<String?> = _lastTraceSummary

    private val _scheduleCount = MutableStateFlow(0)
    val scheduleCount: StateFlow<Int> = _scheduleCount

    init {
        refreshContextData()
        loadAmbientData()
    }

    private fun refreshContextData() {
        viewModelScope.launch {
            _activeSkillCount.value = skillLifecycleManager.getAllHealth()
                .count { it.status != SkillStatus.AUTO_DISABLED && it.status != SkillStatus.KEY_MISSING }
            try {
                val traces = decisionTraceDao.getRecent(1)
                _lastTraceSummary.value = traces.firstOrNull()?.toHumanSummary()
            } catch (_: Exception) {}
            try {
                _scheduleCount.value = scheduleDao.getActive().size
            } catch (_: Exception) {}
        }
    }

    // 다중 경로 캐러셀
    private val _routeOptions = MutableStateFlow<List<RouteItem>>(emptyList())
    val routeOptions: StateFlow<List<RouteItem>> = _routeOptions.asStateFlow()

    private val _selectedRouteIndex = MutableStateFlow(0)
    val selectedRouteIndex: StateFlow<Int> = _selectedRouteIndex.asStateFlow()

    // 일정 자동 액션
    private val _scheduleAction = MutableStateFlow<ScheduleActionPayload?>(null)
    val scheduleAction: StateFlow<ScheduleActionPayload?> = _scheduleAction.asStateFlow()

    // Navigation events (화면 전환)
    private val _navigationEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigationEvent: SharedFlow<String> = _navigationEvent.asSharedFlow()

    // 음성 처리 완료 신호(성공/실패 모두). nullable ttsText. 화면이 collect 해서
    // coordinator.notifyProcessingDone()을 호출 → 에러/재시도 시에도 마이크가 PROCESSING에
    // 갇히지 않게 한다. uiState 기반 speak는 같은 상태 반복(재시도) 시 발화되지 않는 문제가 있음.
    private val _agentDone = MutableSharedFlow<String?>(extraBufferCapacity = 1)
    val agentDone: SharedFlow<String?> = _agentDone.asSharedFlow()

    private var conversationId: String? = null
    var lastRouteId: Int = -1
        private set

    fun highlightRoute(index: Int) {
        _selectedRouteIndex.value = index
    }

    // 현재 위치 (ConversationScreen에서 설정)
    var currentLat: Double = 0.0
    var currentLng: Double = 0.0

    // ── 메시지 전송 (온디바이스 GeminiAgentEngine) ──

    fun sendAgentMessage(text: String, sttAlternatives: List<String> = emptyList()) {
        if (text.isBlank()) return

        // Trial 모드 한도 초과 시 API 호출 없이 즉시 차단
        if (apiKeyProvider.isTrialMode && apiKeyProvider.isTrialLimitReached()) {
            _uiState.value = AgentUiState.Error(
                message = "오늘 무료 체험 한도(30회)에 도달했습니다. 설정에서 API 키를 등록하시면 무제한으로 사용하실 수 있어요.",
                cardType = CardType.ALERT,
                actionRoute = "settings"
            )
            _inputEnabled.value = true
            return
        }

        val userMsg = ChatUiMessage(role = "user", content = text)
        _messages.value = _messages.value + userMsg
        _inputEnabled.value = false
        // 주행 상태는 Processing으로 덮기 '전에' 캡처한다. (이후 _uiState는 Processing이라
        // is Navigating 검사가 항상 false → drivingState가 IDLE로 잘못 읽히던 순서 버그.)
        val wasNavigating = _uiState.value is AgentUiState.Navigating
        _uiState.value = AgentUiState.Processing()

        viewModelScope.launch {
            // 처리 완료 시 코디네이터에 전달할 발화 텍스트(없으면 null=무발화 IDLE). finally에서 1회만 emit.
            var doneTts: String? = null
            try {
                val context = AgentContext(
                    locationX = if (currentLng != 0.0) currentLng else null,
                    locationY = if (currentLat != 0.0) currentLat else null,
                    drivingState = if (wasNavigating) {
                        AgentDrivingState.NAVIGATING
                    } else {
                        AgentDrivingState.IDLE
                    },
                    sttAlternatives = sttAlternatives
                )
                val response = withContext(Dispatchers.IO) {
                    agentEngine.chat(text, context)
                }

                val assistantMsg = ChatUiMessage(
                    role = "assistant",
                    content = response.replyText,
                    explanation = response.explanation
                )
                _messages.value = _messages.value + assistantMsg
                // 메인 "왜?" 카드를 이 응답의 정확한 근거로 즉시 갱신
                // (기존엔 DB getRecent(1) 재조회 → 비동기 저장 레이스로 직전 trace를 보일 수 있었음)
                response.explanation?.let { _lastTraceSummary.value = it }

                @Suppress("UNCHECKED_CAST")
                val uiData = response.uiData as? Map<String, Any>
                val routes = parseRoutes(uiData)
                if (routes.isNotEmpty()) {
                    _routeOptions.value = routes
                    _selectedRouteIndex.value = 0
                }
                // 운전 중이면 voiceSummary(첫 문장)만 TTS, 아니면 전체 ttsText
                // (wasNavigating: Processing으로 덮기 전 캡처한 주행 상태 — 위 순서 버그와 동일 원인)
                val effectiveTts = if (wasNavigating) response.voiceSummary else response.ttsText
                retryAttempts = 0
                handleAction(response.uiAction, response.replyText, routes.ifEmpty { null }, effectiveTts, null, uiData)
                doneTts = effectiveTts?.ifBlank { null } ?: response.replyText.ifBlank { null }
            } catch (e: GeminiApiException.RateLimited) {
                Log.w("ConversationVM", "Rate limited — scheduling retry in 30s")
                _uiState.value = AgentUiState.Error(
                    message = "요청이 너무 많아요. 30초 후 다시 시도할게요.",
                    cardType = CardType.ALERT,
                    retryAfterMs = 30_000L
                )
                scheduleRetry(text, 30_000L)
            } catch (e: GeminiApiException.InvalidKey) {
                Log.e("ConversationVM", "Invalid Gemini API key")
                _uiState.value = AgentUiState.Error(
                    message = "Gemini API 키를 확인해주세요.",
                    cardType = CardType.ALERT,
                    actionRoute = "settings"
                )
            } catch (e: GeminiApiException.NetworkError) {
                Log.w("ConversationVM", "Network error: ${e.message}")
                _uiState.value = AgentUiState.Error(
                    message = "인터넷 연결을 확인해주세요.",
                    cardType = CardType.ALERT
                )
            } catch (e: Exception) {
                Log.e("ConversationVM", "Agent error: ${e.message}", e)
                _uiState.value = AgentUiState.Error(
                    message = "오류가 발생했어요. 다시 말씀해주세요.",
                    cardType = CardType.ALERT
                )
            } finally {
                _inputEnabled.value = true
                // 성공/실패/재시도 모든 경로에서 코디네이터를 PROCESSING에서 해제 (마이크 먹통 방지)
                _agentDone.tryEmit(doneTts)
            }
        }
    }

    // ── 이미지 처리 (Vision) ──

    fun processImage(base64: String) {
        viewModelScope.launch {
            _uiState.value = AgentUiState.Processing()
            _inputEnabled.value = false
            try {
                val result = visionProcessor.processImageBase64(base64)
                val query = when {
                    result.placeName != null -> "${result.placeName}으로 안내해줘"
                    result.address != null -> "${result.address}으로 안내해줘"
                    else -> "이 이미지에 대해 알려줘: ${result.extractedText}"
                }
                sendAgentMessage(query)
            } catch (e: Exception) {
                Log.e("ConversationVM", "Vision error: ${e.message}", e)
                _uiState.value = AgentUiState.Error(
                    message = "이미지 처리 중 오류가 발생했습니다.",
                    cardType = CardType.ALERT
                )
                _inputEnabled.value = true
            }
        }
    }

    // ── 스트리밍 메시지 전송 ──

    fun sendMessageStreaming(userText: String) {
        if (userText.isBlank()) return
        val userMsg = ChatUiMessage(role = "user", content = userText)
        _messages.value = _messages.value + userMsg
        _inputEnabled.value = false
        // 주행 상태는 Processing으로 덮기 전에 캡처 (sendAgentMessage와 동일한 순서 버그 방지)
        val wasNavigating = _uiState.value is AgentUiState.Navigating
        viewModelScope.launch {
            val sb = StringBuilder()
            _uiState.value = AgentUiState.Processing()
            try {
                val context = AgentContext(
                    locationX = if (currentLng != 0.0) currentLng else null,
                    locationY = if (currentLat != 0.0) currentLat else null,
                    drivingState = if (wasNavigating) {
                        AgentDrivingState.NAVIGATING
                    } else {
                        AgentDrivingState.IDLE
                    }
                )
                agentEngine.chatStream(userText, context).collect { chunk ->
                    sb.append(chunk)
                    _uiState.value = AgentUiState.Streaming(sb.toString())
                }
                val finalText = sb.toString()
                val assistantMsg = ChatUiMessage(role = "assistant", content = finalText)
                _messages.value = _messages.value + assistantMsg
                _uiState.value = AgentUiState.AgentMessage(finalText)
            } catch (e: GeminiApiException.RateLimited) {
                _uiState.value = AgentUiState.Error(
                    message = "요청이 너무 많아요. 30초 후 다시 시도할게요.",
                    cardType = CardType.ALERT,
                    retryAfterMs = 30_000L
                )
                scheduleRetry(userText, 30_000L)
            } catch (e: GeminiApiException.InvalidKey) {
                _uiState.value = AgentUiState.Error(
                    message = "Gemini API 키를 확인해주세요.",
                    cardType = CardType.ALERT,
                    actionRoute = "settings"
                )
            } catch (e: Exception) {
                _uiState.value = AgentUiState.Error(
                    message = "오류가 발생했어요. 다시 말씀해주세요.",
                    cardType = CardType.ALERT
                )
            } finally {
                _inputEnabled.value = true
            }
        }
    }

    // ── 음성 텍스트 처리 (on-device STT → Agent) ──

    fun processVoiceText(text: String, lat: Double, lng: Double, sttAlternatives: List<String> = emptyList()) {
        currentLat = lat
        currentLng = lng
        liveFunctionCallBridge.updateLocationContext(lat = lat, lng = lng)
        statsPrefs.edit()
            .putInt("voice_count", statsPrefs.getInt("voice_count", 0) + 1)
            .apply()
        sendAgentMessage(text, sttAlternatives)
    }

    // ── 내비게이션 제어 ──

    fun startNavigation(route: RouteItem) {
        Log.d("ConversationVM", "startNavigation: coords=${route.coords.size}")
        if (route.coords.isEmpty()) {
            _uiState.value = AgentUiState.AgentMessage("경로 데이터가 비어있습니다")
            return
        }
        _uiState.value = AgentUiState.Navigating(route)
    }

    fun stopNavigation() {
        _uiState.value = AgentUiState.Idle
    }

    fun updateNavigationRoute(route: RouteItem) {
        _uiState.value = AgentUiState.Navigating(route)
    }

    // ── 대화 초기화 ──

    fun clearConversation() {
        conversationId = null
        _messages.value = emptyList()
        _uiState.value = AgentUiState.Idle
        _routeOptions.value = emptyList()
        _scheduleAction.value = null
    }

    // ── 내부: ui_action 기반 분기 ──

    private fun handleAction(
        action: String,
        reply: String,
        updatedRoutes: List<RouteItem>?,
        ttsText: String? = null,
        card: AgentCardPayload? = null,
        uiData: Map<String, Any>? = null
    ) {
        // 서버에서 카드 데이터가 오면 AgentCard 상태로 전환
        if (card != null && updatedRoutes.isNullOrEmpty() && action != "show_places" && action != "show_schedule" && action != "show_message_draft" && action != "show_call" && action != "navigate_screen") {
            val cardType = when (card.type) {
                "route_summary" -> CardType.ROUTE_SUMMARY
                "confirmation" -> CardType.CONFIRMATION
                "alert" -> CardType.ALERT
                "recommendation" -> CardType.RECOMMENDATION
                "schedule_popup" -> CardType.SCHEDULE_POPUP
                "weather" -> CardType.WEATHER
                "departure" -> CardType.DEPARTURE
                "proactive" -> CardType.PROACTIVE
                "skill_result" -> CardType.SKILL_RESULT
                "safety_alert" -> CardType.SAFETY_ALERT
                else -> CardType.INFO
            }
            _uiState.value = AgentUiState.AgentCard(
                type = cardType,
                title = card.title ?: "",
                body = card.body ?: reply,
                actions = card.actions ?: emptyList(),
                ttsText = ttsText
            )
            return
        }

        when (action) {
            "show_route", "route_request", "route_modify" -> {
                if (!updatedRoutes.isNullOrEmpty()) {
                    _uiState.value = AgentUiState.RoutePreview(
                        routes = updatedRoutes,
                        agentMessage = reply,
                        routeId = lastRouteId,
                        ttsText = ttsText
                    )
                } else {
                    _uiState.value = AgentUiState.AgentMessage(reply, ttsText)
                }
            }

            "show_places" -> {
                val places = parsePlaces(uiData)
                if (places.isNotEmpty()) {
                    _uiState.value = AgentUiState.ShowPlaces(
                        places = places,
                        message = reply,
                        ttsText = ttsText
                    )
                } else {
                    _uiState.value = AgentUiState.AgentMessage(reply, ttsText)
                }
            }

            "show_cards" -> {
                if (card != null) {
                    val cardType = when (card.type) {
                        "route_summary" -> CardType.ROUTE_SUMMARY
                        "confirmation" -> CardType.CONFIRMATION
                        "alert" -> CardType.ALERT
                        "recommendation" -> CardType.RECOMMENDATION
                        "schedule_popup" -> CardType.SCHEDULE_POPUP
                        else -> CardType.INFO
                    }
                    _uiState.value = AgentUiState.AgentCard(
                        type = cardType,
                        title = card.title ?: "",
                        body = card.body ?: reply,
                        actions = card.actions ?: emptyList(),
                        ttsText = ttsText
                    )
                } else {
                    _uiState.value = AgentUiState.AgentMessage(reply, ttsText)
                }
            }

            "show_schedule" -> {
                val events = parseScheduleEvents(uiData)
                if (events.isNotEmpty()) {
                    _uiState.value = AgentUiState.ShowSchedule(
                        events = events,
                        message = reply,
                        ttsText = ttsText
                    )
                } else {
                    _uiState.value = AgentUiState.AgentMessage(reply, ttsText)
                }
            }

            "start_navigation" -> {
                // 사용자가 카루셀에서 고른 경로(_selectedRouteIndex)를 항상 적용 — [0] 고정 금지
                val sel = _selectedRouteIndex.value
                when {
                    !updatedRoutes.isNullOrEmpty() -> {
                        val idx = sel.coerceIn(0, updatedRoutes.lastIndex)
                        Log.d("ConversationVM", "start_navigation: updatedRoutes[$idx]")
                        _uiState.value = AgentUiState.Navigating(updatedRoutes[idx])
                    }
                    _routeOptions.value.isNotEmpty() -> {
                        val idx = sel.coerceIn(0, _routeOptions.value.lastIndex)
                        Log.d("ConversationVM", "start_navigation: cached routeOptions[$idx]")
                        _uiState.value = AgentUiState.Navigating(_routeOptions.value[idx])
                    }
                    else -> _uiState.value = AgentUiState.AgentMessage(reply, ttsText)
                }
            }

            "show_message_draft" -> {
                val recipient = (uiData?.get("recipient") as? String) ?: ""
                val phone = (uiData?.get("phone") as? String) ?: ""
                val draftMessage = (uiData?.get("message") as? String) ?: reply
                val method = (uiData?.get("method") as? String) ?: "sms"
                _uiState.value = AgentUiState.ShowMessageDraft(
                    recipient = recipient,
                    phone = phone,
                    message = draftMessage,
                    method = method,
                    ttsText = ttsText
                )
            }

            "show_call" -> {
                val name = (uiData?.get("name") as? String) ?: ""
                val phone = (uiData?.get("phone") as? String) ?: ""
                _uiState.value = AgentUiState.ShowCallConfirmation(
                    name = name,
                    phone = phone,
                    ttsText = ttsText
                )
            }

            "navigate_screen" -> {
                val screen = (uiData?.get("screen") as? String) ?: ""
                if (screen.isNotBlank()) {
                    _navigationEvent.tryEmit(screen)
                }
                _uiState.value = AgentUiState.AgentMessage(reply, ttsText)
            }

            "update_route", "route_updated" -> {
                if (!updatedRoutes.isNullOrEmpty()) {
                    val current = _uiState.value
                    if (current is AgentUiState.Navigating) {
                        _uiState.value = AgentUiState.Navigating(updatedRoutes[0])
                    } else {
                        _uiState.value = AgentUiState.RoutePreview(
                            routes = updatedRoutes,
                            agentMessage = reply,
                            routeId = lastRouteId,
                            ttsText = ttsText
                        )
                    }
                } else {
                    _uiState.value = AgentUiState.AgentMessage(reply, ttsText)
                }
            }

            "none", "chat" -> {
                _uiState.value = AgentUiState.AgentMessage(reply, ttsText)
            }

            else -> {
                _uiState.value = AgentUiState.AgentMessage(reply, ttsText)
            }
        }
    }

    // ── ui_data 파싱 헬퍼 ──

    // ui_data 파싱은 순수 함수 UiDataParser에 위임(JVM 단위테스트로 키 계약 회귀 방지).
    private fun parsePlaces(uiData: Map<String, Any>?): List<PlaceItem> =
        UiDataParser.parsePlaces(uiData).also { Log.d("ConversationVM", "parsePlaces: parsed ${it.size}") }

    private fun parseRoutes(uiData: Map<String, Any>?): List<RouteItem> {
        Log.d("ConversationVM", "parseRoutes: uiData keys=${uiData?.keys}, has routes=${uiData?.containsKey("routes")}")
        return UiDataParser.parseRoutes(uiData).also { Log.d("ConversationVM", "parseRoutes: found ${it.size} routes") }
    }

    private fun parseScheduleEvents(uiData: Map<String, Any>?): List<ScheduleEvent> =
        UiDataParser.parseScheduleEvents(uiData).also { Log.d("ConversationVM", "parseScheduleEvents: parsed ${it.size}") }

    // ── Rate Limit 자동 재시도 ──

    private var retryJob: Job? = null
    private var retryAttempts = 0
    private val MAX_RETRY_ATTEMPTS = 3

    private fun scheduleRetry(userText: String, delayMs: Long) {
        if (retryAttempts >= MAX_RETRY_ATTEMPTS) return
        retryJob?.cancel()
        retryJob = viewModelScope.launch {
            retryAttempts++
            delay(delayMs)
            if (_uiState.value is AgentUiState.Error) {
                sendAgentMessage(userText)
            }
        }
    }
}
