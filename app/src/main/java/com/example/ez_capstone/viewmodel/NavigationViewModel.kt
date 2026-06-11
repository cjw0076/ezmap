package com.example.ez_capstone.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ez_capstone.agent.GeminiAgentEngine
import com.example.ez_capstone.agent.models.AgentContext
import com.example.ez_capstone.agent.models.AgentDrivingState
import com.example.ez_capstone.agent.models.DirectionsRoute
import com.example.ez_capstone.api.KakaoLocalApi
import com.example.ez_capstone.api.KakaoMobilityApi
import com.example.ez_capstone.config.ApiKeyProvider
import com.example.ez_capstone.db.dao.DrivingScoreDao
import com.example.ez_capstone.db.dao.FeedbackDao
import com.example.ez_capstone.db.dao.ProfileDao
import com.example.ez_capstone.db.dao.RouteHistoryDao
import com.example.ez_capstone.db.entity.FeedbackEntity
import com.example.ez_capstone.db.entity.RouteHistoryEntity
import com.example.ez_capstone.navi.AiAlertType
import com.example.ez_capstone.navi.AiAlertUi
import com.example.ez_capstone.navi.AiDrivingAdvisor
import com.example.ez_capstone.navi.ChargingPrewarmAdvisor
import com.example.ez_capstone.navi.EvRouteOptimizer
import com.example.ez_capstone.navi.DrivingHudState
import com.example.ez_capstone.navi.DrivingScorer
import com.example.ez_capstone.navi.DrivingSummary
import com.example.ez_capstone.navi.IncidentReporter
import com.example.ez_capstone.navi.NavigationGuideState
import com.example.ez_capstone.navi.ParkingAdvisor
import com.example.ez_capstone.navi.RestAreaAdvisor
import com.example.ez_capstone.navi.RestGuideEngine
import com.example.ez_capstone.navi.RoadHazardEngine
import com.example.ez_capstone.navi.RouteContextEngine
import com.example.ez_capstone.navi.ShadowRerouteEngine
import com.example.ez_capstone.navi.SpeedEnforcement
import com.example.ez_capstone.navi.TtsEvent
import com.example.ez_capstone.server.models.Coord
import com.example.ez_capstone.server.models.Guide
import com.example.ez_capstone.server.models.PlaceItem
import com.example.ez_capstone.server.models.RouteItem
import com.example.ez_capstone.server.models.ScheduleEvent
import com.example.ez_capstone.ui.components.MicState
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val agentEngine: GeminiAgentEngine,
    private val shadowRerouteEngine: ShadowRerouteEngine,
    private val profileDao: ProfileDao,
    private val kakaoLocalApi: KakaoLocalApi,
    private val kakaoMobilityApi: KakaoMobilityApi,
    private val apiKeyProvider: ApiKeyProvider,
    private val gson: Gson,
    @ApplicationContext private val appContext: Context,
    // Phase 7 엔진
    private val speedEnforcement: SpeedEnforcement,
    private val roadHazardEngine: RoadHazardEngine,
    private val incidentReporter: IncidentReporter,
    private val drivingScorer: DrivingScorer,
    private val restAreaAdvisor: RestAreaAdvisor,
    private val parkingAdvisor: ParkingAdvisor,
    private val aiDrivingAdvisor: AiDrivingAdvisor,
    private val drivingScoreDao: DrivingScoreDao,
    private val feedbackDao: FeedbackDao,
    private val routeHistoryDao: RouteHistoryDao,
    private val evRouteOptimizer: EvRouteOptimizer,
    private val chargingPrewarmAdvisor: ChargingPrewarmAdvisor
) : ViewModel() {

    private var guideEngine: RestGuideEngine? = null

    private val _hudState = MutableStateFlow(DrivingHudState())
    val hudState: StateFlow<DrivingHudState> = _hudState.asStateFlow()

    // A3: 동적 카메라 타깃(속도/턴거리→줌·틸트). 엔진 데이터로 VM에서 계산 → 안정 StateFlow로 노출.
    private val _cameraTarget = MutableStateFlow<com.example.ez_capstone.navi.camera.NavCameraTarget?>(null)
    val cameraTarget: StateFlow<com.example.ez_capstone.navi.camera.NavCameraTarget?> = _cameraTarget.asStateFlow()
    private var camPrev: com.example.ez_capstone.navi.camera.NavCameraTarget? = null
    private var camSmoothedSpeed = 0f

    private val _voiceState = MutableStateFlow(MicState.IDLE)
    val voiceState: StateFlow<MicState> = _voiceState.asStateFlow()

    // VM 소유 StateFlow로 미러링 — getter 위임은 화면이 guideEngine 생성 전에 구독하면
    // 죽은 MutableStateFlow를 잡아 위치/방위가 영원히 갱신 안 됨(카메라가 아바타를 안 따라감).
    // cameraTarget과 동일 원리로 안정 인스턴스 유지 → initNavigation에서 엔진 flow를 미러링.
    private val _userLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val userLocation: StateFlow<Pair<Double, Double>?> = _userLocation.asStateFlow()
    private val _userBearing = MutableStateFlow(0f)
    val userBearing: StateFlow<Float> = _userBearing.asStateFlow()

    private val _routeCoords = MutableStateFlow<List<Coord>>(emptyList())
    val routeCoords: StateFlow<List<Coord>> = _routeCoords.asStateFlow()

    // VM 소유 안정 인스턴스 — guideEngine 재생성 후에도 화면 구독이 살아있음
    private val _ttsEvents = MutableSharedFlow<TtsEvent>(extraBufferCapacity = 5)
    val ttsEvents: SharedFlow<TtsEvent> = _ttsEvents.asSharedFlow()

    val drivingSummary: DrivingSummary get() = guideEngine?.getDrivingSummary() ?: DrivingSummary()

    /** 주행 중 음성 명령 결과 카드 (주유소 목록, 일정 목록 등) */
    data class NavVoiceCard(
        val type: String,  // "places" | "schedule"
        val title: String,
        val places: List<PlaceItem> = emptyList(),
        val events: List<ScheduleEvent> = emptyList()
    )

    private val _voiceCard = MutableStateFlow<NavVoiceCard?>(null)
    val voiceCard: StateFlow<NavVoiceCard?> = _voiceCard.asStateFlow()

    // Emits TTS text (nullable) when agent finishes processing — consumed by NavigationScreen
    // to call coordinator.notifyProcessingDone() and un-stick the PROCESSING state.
    private val _agentTts = MutableSharedFlow<String?>(extraBufferCapacity = 1)
    val agentTts: SharedFlow<String?> = _agentTts.asSharedFlow()

    fun dismissVoiceCard() { _voiceCard.value = null }

    fun saveFeedback(rating: Int, comment: String) {
        viewModelScope.launch {
            feedbackDao.insert(FeedbackEntity(rating = rating, comment = comment.trim().ifBlank { null }))
        }
    }

    private var currentRoute: RouteItem? = null
    private var navStartTime = 0L
    private var lastContextCheckTime = 0L
    private var cachedFuelType = "gasoline"
    private var cachedBatteryCapacityKwh = 0f
    private var cachedBatteryPct = 80
    private var cachedChargingSpeedKw = 50f
    private var navSessionId = java.util.UUID.randomUUID().toString()

    init {
        viewModelScope.launch {
            val profile = profileDao.getProfile()
            cachedFuelType = profile?.fuelType ?: "gasoline"
            cachedBatteryCapacityKwh = profile?.batteryCapacityKwh ?: 0f
            cachedBatteryPct = profile?.currentBatteryPct ?: 80
            cachedChargingSpeedKw = profile?.chargingSpeedKw ?: 50f
        }
    }
    private val CONTEXT_CHECK_INTERVAL_MS = 5 * 60 * 1000L  // 5분

    fun initNavigation(routeJson: String) {
        val route = try {
            gson.fromJson(routeJson, RouteItem::class.java)
        } catch (e: Exception) {
            Log.e("NavigationVM", "Route parse error: ${e.message}")
            return
        } ?: run {
            Log.e("NavigationVM", "Route parsed as null")
            return
        }
        currentRoute = route
        _routeCoords.value = route.coords ?: emptyList()
        navSessionId = java.util.UUID.randomUUID().toString()
        navStartTime = System.currentTimeMillis()
        // H6: 주행 진입 시 대화 히스토리 격리. ConversationVM(홈)과 NavigationVM(주행)이 단일
        // conversationHistory를 공유하므로, 직전 홈 대화 맥락이 주행 명령에 누출돼 비순차 답
        // (예: 문자 명령에 엉뚱한 직전 답이 섞임)을 유발할 수 있다. 주행 시작점에서 새 세션으로
        // 끊어 주행 명령이 깨끗한 컨텍스트에서 시작되게 한다. (경로는 routeJson으로 전달되므로 무손실.)
        agentEngine.newSession()
        tripRecorded = false
        drivingScorer.reset()
        parkingAdvisor.reset()
        chargingPrewarmAdvisor.reset()

        val engine = RestGuideEngine(
            appContext, kakaoLocalApi, apiKeyProvider,
            speedEnforcement, roadHazardEngine, incidentReporter
        )
        guideEngine = engine

        // 엔진 위치/방위/TTS를 VM 소유 Flow로 미러링 → 화면이 엔진 생성 전에 구독해도 안정 인스턴스
        viewModelScope.launch { engine.userLocation.collect { _userLocation.value = it } }
        viewModelScope.launch { engine.userBearing.collect { _userBearing.value = it } }
        viewModelScope.launch { engine.ttsEvents.collect { _ttsEvents.emit(it) } }

        // Phase 7: 휴게소 사전 로드
        viewModelScope.launch {
            restAreaAdvisor.loadForRoute(route.coords, routeName = null)
        }

        // Phase 7: EV 경로 최적화 (EV 사용자만)
        if (cachedBatteryCapacityKwh > 0f) {
            viewModelScope.launch {
                val evPlan = evRouteOptimizer.planRoute(
                    routeCoords = route.coords,
                    currentBatteryPct = cachedBatteryPct,
                    batteryCapacityKwh = cachedBatteryCapacityKwh,
                    chargingSpeedKw = cachedChargingSpeedKw
                )
                evPlan?.let { plan ->
                    if (plan.chargeStops.isNotEmpty()) {
                        guideEngine?.addAiAlert(AiAlertUi(
                            type = AiAlertType.AI_RESPONSE,
                            message = "충전 경유 ${plan.chargeStops.size}회 포함 경로 — 총 충전 ${plan.chargeTimeMin}분",
                            autoDismissMs = 12000
                        ))
                    }
                }
            }
        }

        viewModelScope.launch {
            engine.hudState.collect { hud ->
                _hudState.value = hud
                // A3: 매 위치틱마다 동적 카메라 타깃 갱신. EMA 속도 평활(A2) + NavCameraController(A1).
                camSmoothedSpeed = camSmoothedSpeed * 0.7f + hud.speed.toFloat() * 0.3f
                val input = com.example.ez_capstone.navi.camera.NavCameraInput(
                    speedKmh = camSmoothedSpeed,
                    bearingDeg = engine.userBearing.value,
                    distToTurnM = hud.turnDirection?.distance
                )
                val next = com.example.ez_capstone.navi.camera.NavCameraController.compute(input, camPrev)
                if (com.example.ez_capstone.navi.camera.NavCameraController.shouldApply(camPrev, next)) {
                    camPrev = next
                    _cameraTarget.value = next
                    Log.d("NavCam", "spd=${camSmoothedSpeed.toInt()} dist=${hud.turnDirection?.distance} → zoom=${next.zoom} tilt=${next.tiltDeg.toInt()}")
                }
            }
        }

        engine.start(route, object : RestGuideEngine.RerouteCallback {
            override fun onRerouteNeeded(currentLat: Double, currentLng: Double) {
                requestReroute(currentLat, currentLng)
            }
        }, object : RestGuideEngine.LocationUpdateCallback {
            override fun onLocationChanged(lat: Double, lng: Double, speedKmh: Int, bearing: Float) {
                checkRouteContext(lat, lng, speedKmh)
                // Phase 7: 운전점수 실시간 업데이트
                val limitSpeed = _hudState.value.speedCameraAlert?.limitSpeed ?: 80
                val liveScore = drivingScorer.update(speedKmh, bearing, limitSpeed)
                _hudState.value = _hudState.value.copy(liveScore = liveScore)
                // Phase 7: EV 충전소 예열 알림
                if (cachedBatteryCapacityKwh > 0f) {
                    val remainMin = (_hudState.value.remainTime / 60).coerceAtLeast(1)
                    chargingPrewarmAdvisor.checkPrewarm(remainMin)?.let { alert ->
                        guideEngine?.addAiAlert(alert)
                    }
                }

                // Phase 7: 주차장 선제 안내
                val remainDist = _hudState.value.remainDistance
                if (remainDist in 1..2000) {
                    val dest = route.coords.lastOrNull() ?: return
                    viewModelScope.launch {
                        parkingAdvisor.checkAndRecommend(dest.lat, dest.lng, remainDist)
                            ?.let { rec ->
                                guideEngine?.addAiAlert(AiAlertUi(
                                    type = AiAlertType.PARKING,
                                    message = "목적지 근처 주차장 — ${rec.message}",
                                    actions = listOf("안내받기", "닫기"),
                                    autoDismissMs = 20000
                                ))
                            }
                    }
                }
            }
        })

        // ShadowRerouteEngine 시작
        val dest = route.coords.lastOrNull()
        if (dest != null) {
            shadowRerouteEngine.startMonitoring(
                destLat = dest.lat,
                destLng = dest.lng,
                currentDurationS = route.duration_s ?: 0,
                getCurrentLocation = {
                    guideEngine?.userLocation?.value
                },
                onBetterRoute = { better ->
                    guideEngine?.addAiAlert(AiAlertUi(
                        type = AiAlertType.ALTERNATIVE_ROUTE,
                        message = "${better.savingsSeconds / 60}분 빠른 경로를 찾았어요",
                        actions = listOf("변경하기", "유지하기")
                    ))
                }
            )
        }
    }

    /** RouteContextEngine + Phase 7 어드바이저 프로액티브 추천 (5분마다) */
    private fun checkRouteContext(lat: Double, lng: Double, speedKmh: Int) {
        val now = System.currentTimeMillis()
        if (now - lastContextCheckTime < CONTEXT_CHECK_INTERVAL_MS) return
        lastContextCheckTime = now

        val drivingMin = ((now - navStartTime) / 60000).toInt()
        val remainingDist = _hudState.value.remainDistance
        val remainingMin = (remainingDist / 1000.0 / 40.0 * 60).toInt()

        // 기존 RouteContextEngine 제안
        val suggestions = RouteContextEngine.checkTriggers(drivingMin, remainingMin, cachedFuelType)
        suggestions.firstOrNull()?.let { suggestion ->
            guideEngine?.addAiAlert(AiAlertUi(
                type = AiAlertType.AI_RESPONSE,
                message = suggestion.message,
                actions = listOf("검색하기", "괜찮아요"),
                autoDismissMs = 10000
            ))
        }

        viewModelScope.launch {
            // 휴게소 추천 (공공 API, Gemini 미사용)
            restAreaAdvisor.getNextRecommendation(lat, lng, remainingDist, drivingMin, cachedFuelType)
                ?.let { rec ->
                    guideEngine?.addAiAlert(AiAlertUi(
                        type = AiAlertType.REST_AREA,
                        message = "${rec.restArea.name} ${rec.etaMinutes}분 후 — ${rec.reason}",
                        actions = listOf("경유하기", "닫기"),
                        autoDismissMs = 15000
                    ))
                }
            // aiDrivingAdvisor.proactiveCheck 제거 — 주행 중 Gemini 호출 최소화 (rate limit 방지)
        }
    }

    fun processVoiceText(text: String, sttAlternatives: List<String> = emptyList()) {
        if (text.isBlank()) return
        val voiceStartMs = System.currentTimeMillis()
        val location = guideEngine?.userLocation?.value

        Log.d("NavigationVM", "[VOICE] ← STT: '${text.take(50)}' | GPS=${location != null} | chatBusy=${agentEngine.isChatBusy}")
        if (agentEngine.isChatBusy) {
            Log.w("NavigationVM", "[VOICE] chatMutex is locked — previous request still in-flight, queuing")
        }

        _voiceState.value = MicState.PROCESSING
        agentEngine.isVoiceCommandActive = true
        shadowRerouteEngine.isPaused = true

        viewModelScope.launch {
            try {
                val destCoord = currentRoute?.coords?.lastOrNull()
                val context = AgentContext(
                    locationX = location?.second,
                    locationY = location?.first,
                    drivingState = AgentDrivingState.NAVIGATING,
                    destX = destCoord?.lng,
                    destY = destCoord?.lat,
                    sttAlternatives = sttAlternatives
                )
                Log.d("NavigationVM", "[VOICE] → Gemini start (${System.currentTimeMillis() - voiceStartMs}ms since STT)")
                // 에이전트 루프(프롬프트 빌드/JSON 파싱/스킬 매칭)를 IO로 분리 — 메인 스레드 블로킹(ANR) 방지
                val response = withContext(Dispatchers.IO) { agentEngine.chat(text, context) }
                val elapsed = System.currentTimeMillis() - voiceStartMs
                Log.d("NavigationVM", "[VOICE] ← Gemini done: ${elapsed}ms | action=${response.uiAction} | reply='${response.replyText.take(40)}'")

                // 주행 중 메시지는 카드 확인이 불가 → 즉시 발송하고 음성 확인으로 대체.
                // 그 외에는 에이전트 응답을 그대로 읽어준다. (agentTts는 when 처리 후 1회 emit)
                var ttsToSpeak = response.ttsText?.ifBlank { null }

                guideEngine?.addAiAlert(AiAlertUi(
                    type = AiAlertType.AI_RESPONSE,
                    message = response.replyText,
                    autoDismissMs = 8000
                ))

                when (response.uiAction) {
                    "show_places" -> {
                        val places = parsePlaces(response.uiData)
                        if (places.isNotEmpty()) {
                            _voiceCard.value = NavVoiceCard(
                                type = "places",
                                title = response.replyText,
                                places = places
                            )
                        }
                    }
                    "show_schedule" -> {
                        val events = parseScheduleEvents(response.uiData)
                        if (events.isNotEmpty()) {
                            _voiceCard.value = NavVoiceCard(
                                type = "schedule",
                                title = response.replyText,
                                events = events
                            )
                        }
                    }
                    "show_route", "route_updated", "update_route" -> {
                        parseRouteFromUiData(response.uiData)?.let { newRoute ->
                            guideEngine?.updateRoute(newRoute)
                            _routeCoords.value = newRoute.coords
                            currentRoute = newRoute
                        }
                    }
                    "show_message_draft" -> {
                        // 주행 중 즉시 발송 + 음성 확인으로 ttsToSpeak 교체
                        val confirm = sendMessageDirect(response.uiData)
                        ttsToSpeak = confirm
                        guideEngine?.addAiAlert(AiAlertUi(
                            type = AiAlertType.AI_RESPONSE,
                            message = confirm,
                            autoDismissMs = 8000
                        ))
                    }
                }

                // coordinator를 PROCESSING에서 해제하며 최종 음성 안내 (메시지면 발송 확인 멘트)
                _agentTts.tryEmit(ttsToSpeak)
            } catch (e: Exception) {
                Log.e("NavigationVM", "Voice processing error: ${e.message}", e)
                _agentTts.tryEmit(null)  // release coordinator from PROCESSING on error
                guideEngine?.addAiAlert(AiAlertUi(
                    type = AiAlertType.AI_RESPONSE,
                    message = "처리 중 오류가 발생했어요: ${e.message?.take(50) ?: "알 수 없는 오류"}"
                ))
            } finally {
                agentEngine.isVoiceCommandActive = false
                shadowRerouteEngine.isPaused = false  // 재탐색 재개
                _voiceState.value = MicState.IDLE
            }
        }
    }

    fun dismissAiAlert(alert: AiAlertUi) {
        guideEngine?.dismissAlert(alert)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parsePlaces(uiData: Map<String, Any?>?): List<PlaceItem> {
        val list = uiData?.get("places") as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            PlaceItem(
                name = m["name"] as? String ?: return@mapNotNull null,
                address = m["address"] as? String ?: "",
                lat = (m["lat"] as? Number)?.toDouble() ?: return@mapNotNull null,
                lng = (m["lng"] as? Number)?.toDouble() ?: return@mapNotNull null,
                category = m["category"] as? String ?: "",
                distance_m = (m["distance_m"] as? Number)?.toInt() ?: 0,
                price = (m["price"] as? Number)?.toInt(),
                brand = m["brand"] as? String
            )
        }
    }

    /** 주행 중 메시지 즉시 발송. 발송 결과를 음성으로 안내할 한 줄 문장 반환. */
    private fun sendMessageDirect(uiData: Map<String, Any?>?): String {
        val recipient = (uiData?.get("recipient") as? String).orEmpty()
        val phone = (uiData?.get("phone") as? String).orEmpty()
        val message = (uiData?.get("message") as? String).orEmpty()
        if (phone.isBlank()) return "${recipient.ifBlank { "상대" }}님 전화번호를 몰라서 못 보냈어요. 연락처에 먼저 등록해주세요."
        if (message.isBlank()) return "보낼 내용을 못 알아들었어요. 다시 말씀해주세요."
        val granted = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return "문자 권한이 없어 못 보냈어요. 설정에서 SMS 권한을 허용해주세요."
        return try {
            @Suppress("DEPRECATION")
            val sms = SmsManager.getDefault()
            val parts = sms.divideMessage(message)
            sms.sendMultipartTextMessage(phone, null, parts, null, null)
            Log.d("NavigationVM", "[MSG] sent to $recipient ($phone): ${message.take(20)}")
            "${recipient.ifBlank { "상대방" }}님께 '${message}' 보냈어요."
        } catch (e: Exception) {
            Log.e("NavigationVM", "[MSG] send failed: ${e.message}", e)
            "메시지 전송에 실패했어요."
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseScheduleEvents(uiData: Map<String, Any?>?): List<ScheduleEvent> {
        val list = uiData?.get("events") as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            ScheduleEvent(
                id = m["id"] as? String ?: "",
                title = m["title"] as? String ?: return@mapNotNull null,
                start_time = m["start_time"] as? String ?: "",
                location = m["location"] as? String ?: ""
            )
        }
    }

    private var tripRecorded = false

    /**
     * 주행 데이터(운전점수 + RouteHistory)를 1회만 기록 — 도착/수동종료 어느 경로로 와도 정확히 1회.
     * (이전엔 도착 시 stopNavigation 미호출로 정상 완주가 아무것도 기록하지 않던 버그.)
     */
    private fun recordTripData() {
        if (tripRecorded) return
        tripRecorded = true
        val summary = guideEngine?.getDrivingSummary()

        // Phase 7: 운전점수 DB 저장
        if (summary != null && summary.totalDistance > 0) {
            viewModelScope.launch {
                drivingScoreDao.insert(
                    drivingScorer.buildEntity(navSessionId, navStartTime, summary.totalDistance)
                )
            }
        }

        // Pillar 4 연료: 의미있는 주행(>500m)을 RouteHistory에 기록 → 패턴/예측/루틴의 입력.
        val route = currentRoute
        if (route != null && (summary?.totalDistance ?: 0) > 500 && route.coords.size >= 2) {
            val o = route.coords.first()
            val d = route.coords.last()
            val dow = java.time.LocalDate.now().dayOfWeek.value  // 1(월)~7(일)
            val waypointsJson = route.waypoints?.takeIf { it.isNotEmpty() }?.let { gson.toJson(it) }
            viewModelScope.launch {
                try {
                    val rid = routeHistoryDao.insert(RouteHistoryEntity(
                        originName = null, originLat = o.lat, originLng = o.lng,
                        destName = null, destLat = d.lat, destLng = d.lng,
                        waypointsJson = waypointsJson,
                        distanceM = route.distance_m, durationS = route.duration_s,
                        departedAt = navStartTime, arrivedAt = System.currentTimeMillis(),
                        dayOfWeek = dow
                    ))
                    android.util.Log.d("TripRecord", "RouteHistory 기록 id=$rid dist=${route.distance_m}m dow=$dow")
                } catch (e: Exception) {
                    android.util.Log.e("NavigationVM", "RouteHistory insert 실패: ${e.message}")
                }
            }
        }
    }

    /** 목적지 도착 시(화면 ARRIVED) 호출 — 데이터 기록 + 백그라운드 재탐색 중지. 도착 오버레이 유지 위해 FINISHED는 설정 안 함. */
    fun onArrived() {
        recordTripData()
        shadowRerouteEngine.stop()
    }

    fun stopNavigation() {
        shadowRerouteEngine.stop()
        recordTripData()
        guideEngine?.stop()
        _hudState.value = _hudState.value.copy(guideState = NavigationGuideState.FINISHED)
    }

    /**
     * [DEBUG] 현재 경로를 가짜 GPS로 재생 → 내비 User Test(카메라/HUD/TTS)를 실기기에서 반복 검증.
     *
     * 트리거: ADB `__SIMDRIVE__` 또는 `__SIMDRIVE__:<시나리오>`.
     * 시나리오:
     *  - default: 도심(20→60)→고속(90)→감속. 회전 임박 시 자동 감속(회전 미리보기·TTS 검증).
     *  - city   : 일정 40km/h (street-level 줌 유지 관찰).
     *  - highway: 일정 100km/h (최소 줌·최대 틸트 관찰).
     *  - slow   : default 프로파일 0.5x 재생 (카메라 거동 정밀 관찰).
     *  - fast   : default 프로파일 2x 재생 (빠른 스모크).
     * 객관 로그: "SimDrive"(진행) + "NavCam"(매 틱 zoom/tilt/bearing 타깃).
     */
    fun debugSimulateDriving(scenario: String = "default") {
        if (!com.example.ez_capstone.BuildConfig.DEBUG) return
        val coords = currentRoute?.coords ?: return
        if (coords.isEmpty()) return
        val playbackMul = when (scenario) { "slow" -> 0.5f; "fast" -> 2f; else -> 1f }
        val delayMs = (400f / playbackMul).toLong()
        viewModelScope.launch {
            guideEngine?.debugStartSim()
            val n = coords.size
            val step = (n / 60).coerceAtLeast(1)  // 최대 ~60스텝으로 샘플링
            android.util.Log.d("SimDrive", "▶ 시나리오='$scenario' 좌표=$n 스텝=$step 재생=${playbackMul}x")
            var i = 0
            var prev = coords.first()
            while (i < n) {
                val c = coords[i]
                val p = i.toFloat() / n
                // 회전 임박(<120m)이면 감속 — 회전 미리보기 줌인·TTS 타이밍을 사실적으로 재현.
                // (city/highway는 순수 상수 모드라 감속 미적용)
                val turnDist = _hudState.value.turnDirection?.distance
                val nearTurn = turnDist != null && turnDist < 120 && scenario != "city" && scenario != "highway"
                val spd = if (nearTurn) 20 else speedForScenario(scenario, p)
                // 진행방향(bearing) = 직전 샘플→현재. 헤딩업 정렬에 필수.
                val brg = bearingDeg(prev.lat, prev.lng, c.lat, c.lng)
                guideEngine?.debugFeedLocation(c.lat, c.lng, spd, brg)
                prev = c
                kotlinx.coroutines.delay(delayMs)
                i += step
            }
            coords.last().let { guideEngine?.debugFeedLocation(it.lat, it.lng, 0, bearingDeg(prev.lat, prev.lng, it.lat, it.lng)) }
            android.util.Log.d("SimDrive", "■ 시뮬 종료 (시나리오='$scenario')")
        }
    }

    /** 시나리오·진행도(p) → 목표 속도(km/h). */
    private fun speedForScenario(scenario: String, p: Float): Int = when (scenario) {
        "city" -> 40
        "highway" -> 100
        else -> when {  // default / slow / fast = 도심→고속→감속 프로파일
            p < 0.2f -> (20 + p / 0.2f * 40).toInt()
            p < 0.7f -> 90
            else -> (90 - (p - 0.7f) / 0.3f * 80).toInt().coerceAtLeast(10)
        }
    }

    /** 두 좌표 사이 진행 방위각(0~360°, 0=북). */
    private fun bearingDeg(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        if (lat1 == lat2 && lng1 == lng2) return 0f
        val dLng = Math.toRadians(lng2 - lng1)
        val y = Math.sin(dLng) * Math.cos(Math.toRadians(lat2))
        val x = Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) -
            Math.sin(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(dLng)
        return (((Math.toDegrees(Math.atan2(y, x)) + 360) % 360)).toFloat()
    }

    /** Gemini 없이 KakaoMobilityApi 직접 호출 — 경로 이탈 시 rate limit / mutex 충돌 방지 */
    private fun requestReroute(currentLat: Double, currentLng: Double) {
        val dest = currentRoute?.coords?.lastOrNull() ?: run {
            Log.w("NavigationVM", "Reroute skipped: no destination")
            return
        }
        Log.d("NavigationVM", "[REROUTE] Kakao direct: ($currentLat,$currentLng) → (${dest.lat},${dest.lng})")

        viewModelScope.launch {
            try {
                val routes = kakaoMobilityApi.getDirections(
                    originLng = currentLng, originLat = currentLat,
                    destLng = dest.lng, destLat = dest.lat,
                    alternatives = false
                )
                val best = routes.firstOrNull() ?: run {
                    Log.w("NavigationVM", "Reroute: no routes returned")
                    guideEngine?.addAiAlert(AiAlertUi(type = AiAlertType.REROUTE, message = "재탐색 실패 — 경로를 찾지 못했어요"))
                    return@launch
                }
                val newRoute = best.toRouteItem()
                guideEngine?.updateRoute(newRoute)
                _routeCoords.value = newRoute.coords
                currentRoute = newRoute
                Log.d("NavigationVM", "[REROUTE] Done: ${best.distanceM}m ${best.durationS / 60}min")
                guideEngine?.addAiAlert(AiAlertUi(
                    type = AiAlertType.REROUTE,
                    message = "경로 재탐색 완료 — 약 ${best.durationS / 60}분",
                    autoDismissMs = 5000
                ))
            } catch (e: Exception) {
                Log.e("NavigationVM", "[REROUTE] Failed: ${e.message}")
                guideEngine?.addAiAlert(AiAlertUi(type = AiAlertType.REROUTE, message = "재탐색 실패"))
            }
        }
    }

    private fun DirectionsRoute.toRouteItem() = RouteItem(
        coords = coords.map { Coord(it.lat, it.lng) },
        guides = guides.map { Guide(it.lat, it.lng, it.name, it.type, it.guidance, it.distance) },
        distance_m = distanceM,
        duration_s = durationS,
        waypoints = null
    )

    @Suppress("UNCHECKED_CAST")
    private fun parseRouteFromUiData(uiData: Map<String, Any?>): RouteItem? {
        val routesList = uiData["routes"] as? List<*> ?: return null
        val first = routesList.firstOrNull() as? Map<String, Any> ?: return null

        val coords = (first["coords"] as? List<*>)?.mapNotNull { c ->
            val cm = c as? Map<String, Any> ?: return@mapNotNull null
            val lat = (cm["lat"] as? Number)?.toDouble() ?: return@mapNotNull null
            val lng = (cm["lng"] as? Number)?.toDouble() ?: return@mapNotNull null
            Coord(lat, lng)
        } ?: return null
        if (coords.isEmpty()) return null

        val guides = (first["guides"] as? List<*>)?.mapNotNull { g ->
            val gm = g as? Map<String, Any> ?: return@mapNotNull null
            com.example.ez_capstone.server.models.Guide(
                lat = (gm["lat"] as? Number)?.toDouble() ?: 0.0,
                lng = (gm["lng"] as? Number)?.toDouble() ?: 0.0,
                name = (gm["name"] as? String) ?: "",
                type = (gm["type"] as? Number)?.toInt() ?: 0,
                guidance = (gm["guidance"] as? String) ?: "",
                distance = (gm["distance"] as? Number)?.toInt() ?: 0
            )
        }

        return RouteItem(
            coords = coords,
            distance_m = (first["distance_m"] as? Number)?.toInt() ?: 0,
            duration_s = (first["duration_s"] as? Number)?.toInt() ?: 0,
            waypoints = null,
            guides = guides
        )
    }

    override fun onCleared() {
        shadowRerouteEngine.stop()
        guideEngine?.stop()
        super.onCleared()
    }
}
