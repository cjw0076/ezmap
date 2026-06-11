package com.example.ez_capstone.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ez_capstone.navi.NavigationCameraMode
import com.example.ez_capstone.navi.NavigationCameraStateMachine
import com.example.ez_capstone.navi.NavigationGuideState
import com.example.ez_capstone.voice.VoiceStateCoordinator
import com.example.ez_capstone.ui.components.AgentBubble
import com.example.ez_capstone.ui.components.KakaoMapCompose
import com.example.ez_capstone.ui.components.MicState
import com.example.ez_capstone.ui.components.navigation.ArrivalOverlay
import com.example.ez_capstone.ui.components.navigation.NavigationOverlay
import com.example.ez_capstone.ui.theme.Background
import com.example.ez_capstone.ui.theme.Cyan
import com.example.ez_capstone.ui.theme.Green
import com.example.ez_capstone.ui.theme.Orbitron
import com.example.ez_capstone.ui.theme.Surface
import com.example.ez_capstone.ui.theme.TextPrimary
import com.example.ez_capstone.ui.theme.TextSecondary
import com.example.ez_capstone.ui.theme.Warning
import com.example.ez_capstone.viewmodel.NavigationViewModel

private const val SHOW_NAV_DEBUG_COMMAND_PANEL = false

@Composable
fun NavigationScreen(
    routeJson: String,
    onNavigationFinished: () -> Unit,
    coordinator: VoiceStateCoordinator,
    viewModel: NavigationViewModel = hiltViewModel()
) {
    val hudState by viewModel.hudState.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val userLocation by viewModel.userLocation.collectAsStateWithLifecycle()
    val routeCoords by viewModel.routeCoords.collectAsStateWithLifecycle()
    val userBearing by viewModel.userBearing.collectAsStateWithLifecycle()

    // Voice coordinator state
    val coordVoiceState by coordinator.state.collectAsStateWithLifecycle()
    val micState = when (coordVoiceState) {
        VoiceStateCoordinator.State.IDLE -> MicState.IDLE
        VoiceStateCoordinator.State.WAKE_DETECTED -> MicState.LISTENING
        VoiceStateCoordinator.State.PROCESSING -> MicState.PROCESSING
        VoiceStateCoordinator.State.SPEAKING -> MicState.IDLE
    }
    val voiceCard by viewModel.voiceCard.collectAsStateWithLifecycle()
    var partialText by remember { mutableStateOf("") }
    var showExitConfirm by remember { mutableStateOf(false) }
    var activeTtsEvent by remember { mutableStateOf<com.example.ez_capstone.navi.TtsEvent?>(null) }
    var cameraMode by remember { mutableStateOf(NavigationCameraMode.GPS_WAITING) }
    // Debug panel state — always declared to respect Compose rules (DEBUG check at render site)
    var navDebugExpanded by remember { mutableStateOf(false) }
    var navDebugText by remember { mutableStateOf("") }

    fun confirmExit() {
        viewModel.stopNavigation()
        onNavigationFinished()
    }

    // 시스템 뒤로가기 → 실수 방지를 위해 종료 확인
    BackHandler {
        showExitConfirm = true
    }

    // Coordinator activate/deactivate — 하나의 DisposableEffect로 Job 추적:
    // 빠른 화면 이탈 시 delay(300) 도중 onDispose가 먼저 실행돼 Job을 취소하고,
    // 300ms 후 already-disposed scope에서 activate가 호출되는 누수를 방지한다.
    val coordinatorOwner = remember { Any() }
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        val job = scope.launch {
            delay(300)
            coordinator.activate(
                owner = coordinatorOwner,
                callback = object : VoiceStateCoordinator.CommandCallback {
                    override fun onCommand(text: String, alternatives: List<String>) {
                        partialText = ""
                        if (text.startsWith("__SIMDRIVE__")) {
                            viewModel.debugSimulateDriving(text.substringAfter("__SIMDRIVE__:", "default"))
                        } else viewModel.processVoiceText(text, alternatives)
                    }
                    override fun onWakeWordOnly() { partialText = "" }
                    override fun onPartialSpeech(text: String) { partialText = text }
                },
                // 주행 중에도 웨이크워드 상시 대기 → 핸즈프리. 안내 TTS 중 "이지야"로
                // 바지인(TTS 즉시 중단 후 청취)이 가능해진다(onWakeWordDetected가 처리).
                useWakeWord = true
            )
        }
        onDispose {
            job.cancel()
            coordinator.deactivate(coordinatorOwner)
        }
    }

    // Init navigation
    LaunchedEffect(routeJson) {
        viewModel.initNavigation(routeJson)
    }

    // 3-stage TTS events: speak + show maneuver popup for 3 seconds
    LaunchedEffect(Unit) {
        viewModel.ttsEvents.collect { event ->
            coordinator.speak(event.text)
            activeTtsEvent = event
            delay(3000)
            activeTtsEvent = null
        }
    }

    // Release coordinator from PROCESSING state after agent responds
    LaunchedEffect(Unit) {
        viewModel.agentTts.collect { ttsText ->
            coordinator.notifyProcessingDone(ttsText)
        }
    }

    // TTS for AI alerts — yield briefly so maneuver TTS can claim SPEAKING state first
    LaunchedEffect(hudState.aiAlerts) {
        val alert = hudState.aiAlerts.lastOrNull() ?: return@LaunchedEffect
        delay(100)
        if (coordinator.state.value != VoiceStateCoordinator.State.SPEAKING) {
            coordinator.speak(alert.message)
        }
    }

    // Handle arrival — TTS only, overlay handles dismiss
    LaunchedEffect(hudState.guideState) {
        if (hudState.guideState == NavigationGuideState.ARRIVED) {
            viewModel.onArrived()  // 도착 즉시 주행점수+RouteHistory 1회 기록(수동 종료 의존 X)
            coordinator.speak("목적지에 도착했습니다.")
        }
    }

    // Map center from user location — derivedStateOf prevents recomposition on identical GPS values
    val centerLat by remember { derivedStateOf { userLocation?.first ?: 35.5433 } }
    val centerLng by remember { derivedStateOf { userLocation?.second ?: 129.2599 } }
    val hasLocation by remember { derivedStateOf { userLocation != null } }
    val isTrackingUser by remember { derivedStateOf { NavigationCameraStateMachine.shouldTrackUser(cameraMode, hasLocation) } }

    LaunchedEffect(hasLocation) {
        cameraMode = when {
            !hasLocation -> NavigationCameraMode.GPS_WAITING
            cameraMode == NavigationCameraMode.GPS_WAITING -> NavigationCameraMode.FOLLOW_USER
            else -> cameraMode
        }
    }

    // hasLocation이 변경돼도 15초 타이머가 리셋되지 않도록 rememberUpdatedState 사용
    val currentHasLocation by rememberUpdatedState(hasLocation)
    LaunchedEffect(cameraMode) {
        if (cameraMode == NavigationCameraMode.USER_PANNING) {
            delay(15_000)
            cameraMode = NavigationCameraStateMachine.onTimeout(cameraMode, currentHasLocation)
        }
    }

    // A3: VM이 엔진 데이터로 계산한 동적 카메라 타깃 수집(getter 기반 dead-flow 회피).
    val camTarget by viewModel.cameraTarget.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Layer 1: Map with route polyline + camera tracking
        KakaoMapCompose(
            modifier = Modifier.fillMaxSize(),
            routeCoords = routeCoords,
            centerLat = centerLat,
            centerLng = centerLng,
            zoomLevel = 17,
            trackUser = isTrackingUser,
            bearing = userBearing,
            isOverspeed = hudState.speedCameraAlert?.isOverSpeed == true,
            navZoom = camTarget?.zoom,
            navTiltDeg = camTarget?.tiltDeg,
            onMapGesture = {
                cameraMode = NavigationCameraStateMachine.onMapGesture(cameraMode)
            }
        )

        // GPS acquiring overlay — shown until first location fix
        if (!hasLocation) {
            val gpsTransition = rememberInfiniteTransition(label = "gpsWait")
            val gpsAlpha by gpsTransition.animateFloat(
                initialValue = 0.35f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                label = "gpsAlpha"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Background.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.GpsFixed,
                        contentDescription = null,
                        tint = Warning.copy(alpha = gpsAlpha),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("GPS 신호를 받는 중...", color = Warning, fontFamily = Orbitron, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("실외에서 잠시 기다려 주세요", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }

        // Layer 2: Navigation HUD
        NavigationOverlay(
            hudState = hudState,
            voiceState = micState,
            activeTtsEvent = activeTtsEvent,
            onVoiceClick = {
                // 마이크 탭 = "지금 말할게요". SPEAKING 중이면 triggerVoiceInput이 바지인(TTS 즉시 중단)+청취.
                coordinator.triggerVoiceInput()
            },
            onQuickAction = { action -> viewModel.processVoiceText(action) },
            onDismissAlert = { viewModel.dismissAiAlert(it) },
            onExitClick = {
                showExitConfirm = true
            },
            cameraMode = cameraMode,
            onRecenterClick = {
                cameraMode = NavigationCameraStateMachine.onRecenter(cameraMode, hasLocation)
            },
            onOverviewClick = {
                cameraMode = NavigationCameraStateMachine.onOverview(cameraMode)
            }
        )

        // Debug text command panel (debug builds only)
        if (com.example.ez_capstone.BuildConfig.DEBUG && SHOW_NAV_DEBUG_COMMAND_PANEL) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 170.dp),
                horizontalAlignment = Alignment.Start
            ) {
                if (navDebugExpanded) {
                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Surface.copy(alpha = 0.95f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = navDebugText,
                                onValueChange = { navDebugText = it },
                                placeholder = { Text("내비 명령...", fontSize = 12.sp, color = TextSecondary) },
                                modifier = Modifier.width(200.dp),
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 13.sp, color = TextPrimary)
                            )
                            Spacer(Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    val cmd = navDebugText.trim()
                                    if (cmd.isNotBlank()) {
                                        viewModel.processVoiceText(cmd)
                                        navDebugText = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Cyan.copy(alpha = 0.2f),
                                    contentColor = Cyan
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) { Text("전송", fontSize = 12.sp) }
                        }
                    }
                }
                SmallFloatingActionButton(
                    onClick = { navDebugExpanded = !navDebugExpanded },
                    containerColor = Surface,
                    contentColor = Cyan,
                    modifier = Modifier.size(36.dp)
                ) {
                    Text(if (navDebugExpanded) "×" else "✎", fontSize = 14.sp, color = Cyan)
                }
            }
        }

        // Partial text overlay
        if (micState == MicState.LISTENING && partialText.isNotBlank()) {
            AgentBubble(
                text = "\uD83C\uDFA4 $partialText",
                onDismiss = {},
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 160.dp)
            )
        }

        // Voice result card (gas stations, schedule)
        voiceCard?.let { card ->
            NavVoiceResultCard(
                card = card,
                onDismiss = { viewModel.dismissVoiceCard() },
                onNavigate = { lat, lng, name ->
                    viewModel.processVoiceText("${name}\uC73C\uB85C \uACBD\uB85C \uC548\uB0B4\uD574\uC918 \uC704\uB3C4 $lat \uACBD\uB3C4 $lng")
                    viewModel.dismissVoiceCard()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 168.dp, start = 12.dp, end = 12.dp)
            )
        }

        // Arrival overlay
        if (hudState.guideState == NavigationGuideState.ARRIVED) {
            ArrivalOverlay(
                summary = viewModel.drivingSummary,
                drivingScore = hudState.liveScore,
                onFeedbackSubmit = { rating, comment -> viewModel.saveFeedback(rating, comment) },
                onDismiss = onNavigationFinished
            )
        }

        if (showExitConfirm) {
            AlertDialog(
                onDismissRequest = { showExitConfirm = false },
                title = { Text("안내를 종료할까요?") },
                text = { Text("현재 주행 안내가 중단됩니다.") },
                confirmButton = {
                    Button(onClick = { confirmExit() }) {
                        Text("종료")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitConfirm = false }) {
                        Text("계속 안내")
                    }
                }
            )
        }
    }
}

@Composable
private fun NavVoiceResultCard(
    card: NavigationViewModel.NavVoiceCard,
    onDismiss: () -> Unit,
    onNavigate: (lat: Double, lng: Double, name: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Surface.copy(alpha = 0.95f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (card.type == "places") "주변 장소" else "일정",
                color = Cyan,
                fontFamily = Orbitron,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "닫기", tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(6.dp))

        val maxItems = 5
        when (card.type) {
            "places" -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(card.places.take(maxItems)) { place ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate(place.lat, place.lng, place.name) }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(place.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            if (place.distance_m > 0) {
                                val dist = if (place.distance_m >= 1000)
                                    "${"%.1f".format(place.distance_m / 1000.0f)}km"
                                else "${place.distance_m}m"
                                Text(dist, color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                        if (place.price != null) {
                            Text(
                                "${"%,d".format(place.price)}원/L",
                                color = Green,
                                fontFamily = Orbitron,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (card.places.indexOf(place) < card.places.take(maxItems).size - 1) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    }
                }
            }
            "schedule" -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(card.events.take(maxItems)) { event ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Text(event.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        if (event.start_time.isNotBlank() || event.location.isNotBlank()) {
                            Text(
                                listOf(event.start_time, event.location).filter { it.isNotBlank() }.joinToString(" · "),
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    if (card.events.indexOf(event) < card.events.take(maxItems).size - 1) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    }
                }
            }
        }
    }
}
