package com.example.ez_capstone.ui.screens

import android.Manifest
import android.os.Looper
import android.util.Log
import com.example.ez_capstone.server.LocationData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.io.ByteArrayOutputStream
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.ez_capstone.AgentUiState
import com.example.ez_capstone.navigation.NavRoutes
import com.example.ez_capstone.voice.VoiceStateCoordinator
import com.example.ez_capstone.server.models.RouteItem
import com.example.ez_capstone.CardType
import com.example.ez_capstone.ui.components.AmbientDashboard
import com.example.ez_capstone.ui.components.AgentBubble
import com.example.ez_capstone.ui.components.CallConfirmationCard
import com.example.ez_capstone.ui.components.GpsPulseOverlay
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.ez_capstone.ui.components.RouteParticleOverlay
import com.example.ez_capstone.ui.components.cardStyleFor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import com.example.ez_capstone.R
import com.example.ez_capstone.ui.theme.Orbitron
import com.example.ez_capstone.ui.theme.Rajdhani
import com.example.ez_capstone.ui.theme.SurfaceHigh
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.platform.LocalDensity
import com.example.ez_capstone.ui.components.ChipData
import com.example.ez_capstone.ui.components.ContextChipRow
import com.example.ez_capstone.ui.components.ConversationBottomSheetContent
import com.example.ez_capstone.ui.components.ConversationTopBar
import com.example.ez_capstone.ui.components.DynamicCard
import com.example.ez_capstone.ui.components.KakaoMapCompose
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.example.ez_capstone.ui.components.MessageDraftCard
import com.example.ez_capstone.ui.components.MicFAB
import com.example.ez_capstone.ui.components.MicState
import com.example.ez_capstone.ui.components.PlacesOverlay
import com.example.ez_capstone.ui.components.ProcessingCard
import com.example.ez_capstone.ui.components.RouteCarousel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.example.ez_capstone.ui.components.cards.ConfirmationCard
import com.example.ez_capstone.ui.components.cards.ProactiveSuggestionCard
import com.example.ez_capstone.ui.components.cards.TextReplyCard
import com.example.ez_capstone.ui.components.SchedulePopup
import com.example.ez_capstone.ui.screens.RouteOption
import com.example.ez_capstone.ui.theme.Accent
import com.example.ez_capstone.ui.theme.AccentDim
import com.example.ez_capstone.ui.theme.Background
import com.example.ez_capstone.ui.theme.LocalVoiceOnlyMode
import com.example.ez_capstone.ui.theme.Surface
import com.example.ez_capstone.ui.theme.TextDim
import com.example.ez_capstone.ui.theme.TextPrimary
import com.example.ez_capstone.ui.theme.TextSecondary
import com.example.ez_capstone.viewmodel.ConversationViewModel
import com.example.ez_capstone.server.models.Waypoint
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.example.ez_capstone.agent.models.PlaceResult
import com.google.gson.Gson
import kotlinx.coroutines.launch

private const val TAG = "ConversationScreen"
private const val SHOW_DEBUG_COMMAND_PANEL = false

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    navController: NavHostController? = null,
    coordinator: VoiceStateCoordinator,
    viewModel: ConversationViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val inputEnabled by viewModel.inputEnabled.collectAsState()
    val routeOptions by viewModel.routeOptions.collectAsState()
    val selectedRouteIndex by viewModel.selectedRouteIndex.collectAsState()
    val activeSkillCount by viewModel.activeSkillCount.collectAsState()
    val lastTraceSummary by viewModel.lastTraceSummary.collectAsState()
    val scheduleCount by viewModel.scheduleCount.collectAsState()
    val quickDestinations by viewModel.quickDestinations.collectAsState()
    val ambientData by viewModel.ambientData.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadQuickDestinations()
    }

    // SearchScreen에서 장소 선택 시 자동 경로 탐색
    LaunchedEffect(Unit) {
        navController?.currentBackStackEntry?.savedStateHandle
            ?.getStateFlow<String?>("searchDestination", null)
            ?.collect { json ->
                if (json != null) {
                    val place = try { Gson().fromJson(json, PlaceResult::class.java) } catch (_: Exception) { null }
                    if (place != null) {
                        viewModel.sendAgentMessage("${place.name}(으)로 경로 안내해줘")
                    }
                    navController.currentBackStackEntry?.savedStateHandle?.remove<String>("searchDestination")
                }
            }
    }

    // Drawer state
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Voice coordinator state
    val voiceState by coordinator.state.collectAsState()
    val micState = when (voiceState) {
        VoiceStateCoordinator.State.IDLE -> MicState.IDLE
        VoiceStateCoordinator.State.WAKE_DETECTED -> MicState.LISTENING
        VoiceStateCoordinator.State.PROCESSING -> MicState.PROCESSING
        VoiceStateCoordinator.State.SPEAKING -> MicState.SPEAKING
    }
    var partialText by remember { mutableStateOf("") }
    var expandedCard by remember { mutableStateOf<AgentUiState.AgentCard?>(null) }
    // Debug panel state — always declared to avoid conditional remember
    var debugExpanded by remember { mutableStateOf(false) }
    var debugText by remember { mutableStateOf("") }

    // Current location
    var currentLat by remember { mutableStateOf(35.5433) }
    var currentLng by remember { mutableStateOf(129.2599) }

    // Permission launcher
    var hasAudioPermission by remember { mutableStateOf(false) }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasAudioPermission = granted }

    var hasLocationPermission by remember { mutableStateOf(false) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions.values.all { it }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            val baos = ByteArrayOutputStream()
            it.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            viewModel.processImage(base64)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) cameraLauncher.launch(null)
    }

    // 커뮤니케이션 권한: SMS(문자 발송) + 연락처(이름→번호 조회). 문자 보내기엔 둘 다 필요.
    // (이전엔 SMS만 요청 → READ_CONTACTS가 매니페스트에만 있고 요청은 안 돼 영영 거부 →
    //  '엄마/○○ 연락처를 찾을 수 없어요'가 권한 문제인데 '없는 사람'처럼 보이던 버그.)
    val commsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        // 이미 온보딩한 유저용 안전망: 온보딩 일괄 요청을 못 받은 dangerous 권한을 진입 시 재요청.
        // (audio·location·camera는 아래 별도 launcher가 처리 → 여기선 나머지: SMS·연락처·전화·일정.)
        val needed = listOf(
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.CALL_PHONE,
            android.Manifest.permission.READ_CALENDAR
        ).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) commsPermissionLauncher.launch(needed.toTypedArray())
    }

    // Request permissions on launch
    LaunchedEffect(Unit) {
        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    // Real-time location tracking (10-second interval)
    DisposableEffect(hasLocationPermission) {
        if (!hasLocationPermission) return@DisposableEffect onDispose {}
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10_000L).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    currentLat = it.latitude
                    currentLng = it.longitude
                }
            }
        }
        try {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission denied: ${e.message}")
        }
        onDispose { fusedClient.removeLocationUpdates(callback) }
    }

    // Coordinator activate/deactivate
    val coordinatorOwner = remember { Any() }
    DisposableEffect(Unit) {
        coordinator.activate(coordinatorOwner, object : VoiceStateCoordinator.CommandCallback {
            override fun onCommand(text: String, alternatives: List<String>) {
                partialText = ""
                viewModel.processVoiceText(text, currentLat, currentLng, alternatives)
            }
            override fun onWakeWordOnly() {
                partialText = ""
                coordinator.speak("다시 말씀해주세요")
            }
            override fun onPartialSpeech(text: String) {
                partialText = text
            }
        })
        onDispose { coordinator.deactivate(coordinatorOwner) }
    }

    // 음성 처리 완료 시 코디네이터 해제 — 성공이면 발화, 실패/재시도면 IDLE 복귀.
    // (uiState 기반 speak는 같은 상태 반복 시 발화되지 않아 마이크가 PROCESSING에 갇혔음)
    LaunchedEffect(Unit) {
        viewModel.agentDone.collect { ttsText ->
            coordinator.notifyProcessingDone(ttsText)
        }
    }

    // Navigate to screen from agent command
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { screen ->
            when (screen) {
                "schedule" -> navController?.navigate(NavRoutes.SCHEDULE)
                "profile" -> navController?.navigate(NavRoutes.PROFILE)
                "settings" -> navController?.navigate(NavRoutes.SETTINGS)
                "contacts" -> navController?.navigate(NavRoutes.CONTACTS)
                "search" -> navController?.navigate(NavRoutes.SEARCH)
                "skill_store", "skills" -> navController?.navigate(NavRoutes.SKILL_STORE)
                "agent_memory", "memory" -> navController?.navigate(NavRoutes.AGENT_MEMORY)
            }
        }
    }

    // KakaoMap 직접 참조 — onPlaceSelected 카메라 이동에 사용
    var kakaoMapRef by remember { mutableStateOf<KakaoMap?>(null) }

    // Route coords for map
    val routeCoords = when (val state = uiState) {
        is AgentUiState.RoutePreview -> {
            if (state.routes.isNotEmpty() && selectedRouteIndex in state.routes.indices) {
                state.routes[selectedRouteIndex].coords
            } else emptyList()
        }
        is AgentUiState.Navigating -> state.route.coords
        else -> if (routeOptions.isNotEmpty() && selectedRouteIndex in routeOptions.indices) {
            routeOptions[selectedRouteIndex].coords
        } else emptyList()
    }

    // Waypoints for route markers
    val routeWaypoints: List<Waypoint> = when (val state = uiState) {
        is AgentUiState.RoutePreview -> {
            if (state.routes.isNotEmpty() && selectedRouteIndex in state.routes.indices) {
                state.routes[selectedRouteIndex].waypoints ?: emptyList()
            } else emptyList()
        }
        is AgentUiState.Navigating -> state.route.waypoints ?: emptyList()
        else -> if (routeOptions.isNotEmpty() && selectedRouteIndex in routeOptions.indices) {
            routeOptions[selectedRouteIndex].waypoints ?: emptyList()
        } else emptyList()
    }

    // Place pins for map
    val placePins = when (val state = uiState) {
        is AgentUiState.ShowPlaces -> state.places
        else -> emptyList()
    }

    // Schedule → 자동 경로 탐색 (ScheduleScreen에서 장소 클릭 시)
    val autoNavigate = navController?.currentBackStackEntry
        ?.savedStateHandle?.get<String>("autoNavigate")
    LaunchedEffect(autoNavigate) {
        if (!autoNavigate.isNullOrBlank()) {
            navController?.currentBackStackEntry?.savedStateHandle?.remove<String>("autoNavigate")
            viewModel.currentLat = currentLat
            viewModel.currentLng = currentLng
            viewModel.sendAgentMessage("${autoNavigate}으로 안내해줘")
        }
    }

    // start_navigation: navigate to NavigationScreen
    LaunchedEffect(uiState) {
        if (uiState is AgentUiState.Navigating && navController != null
            && navController.currentDestination?.route != NavRoutes.NAVIGATION) {
            val route = (uiState as AgentUiState.Navigating).route
            val routeJson = Gson().toJson(route)
            navController.currentBackStackEntry?.savedStateHandle?.set("routeJson", routeJson)
            navController.navigate(NavRoutes.NAVIGATION)
            // 안내 진입 직후 Navigating 상태 소비 — 안 그러면 주행 종료 후 복귀 시
            // 이 LaunchedEffect가 같은 상태로 재실행돼 NAVIGATION으로 되돌아간다(홈 진입 실패).
            viewModel.stopNavigation()
        }
    }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded
        )
    )

    // ── ModalNavigationDrawer + BottomSheetScaffold ──

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,  // 엣지 스와이프 비활성화 — 지도 팬과 충돌 방지 (햄버거 버튼으로만 열기)
        drawerContent = {
            DrawerContent(
                onHomeClick = {
                    viewModel.clearConversation()
                    scope.launch { drawerState.close() }
                },
                onProfileClick = {
                    scope.launch { drawerState.close() }
                    navController?.navigate(NavRoutes.PROFILE)
                },
                onScheduleClick = {
                    scope.launch { drawerState.close() }
                    navController?.navigate(NavRoutes.SCHEDULE)
                },
                onContactsClick = {
                    scope.launch { drawerState.close() }
                    navController?.navigate(NavRoutes.CONTACTS)
                },
                onSettingsClick = {
                    scope.launch { drawerState.close() }
                    navController?.navigate(NavRoutes.SETTINGS)
                }
            )
        }
    ) {
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = 132.dp,
            sheetContainerColor = Background,
            sheetDragHandle = null,  // 커스텀 핸들은 ConversationBottomSheetContent 내부에 있음
            sheetContent = {
                ConversationBottomSheetContent(
                    messages = messages,
                    inputEnabled = inputEnabled,
                    onSendMessage = { text ->
                        viewModel.currentLat = currentLat; viewModel.currentLng = currentLng; viewModel.sendAgentMessage(text)
                    },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.ime)
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Background)
            ) {
                val voiceOnly = LocalVoiceOnlyMode.current

                // 지도가 필요한 상태: 경로/장소/내비게이션
                val isMapVisible = uiState is AgentUiState.RoutePreview ||
                        uiState is AgentUiState.ShowPlaces ||
                        uiState is AgentUiState.Navigating

                if (isMapVisible && !voiceOnly) {
                    // Layer 1: Map (경로·장소·내비 상태에서만 표시)
                    KakaoMapCompose(
                        modifier = Modifier.fillMaxSize(),
                        routeCoords = routeCoords,
                        waypoints = routeWaypoints,
                        placePins = placePins,
                        centerLat = currentLat,
                        centerLng = currentLng,
                        onMapReady = { map -> kakaoMapRef = map }
                    )
                    // Layer 1.1: GPS 소나 펄스 오버레이 (경로가 없을 때만 표시)
                    if (routeCoords.isEmpty()) GpsPulseOverlay(
                        modifier = Modifier
                            .size(120.dp)
                            .align(Alignment.Center)
                    )
                    // Layer 1.2: 경로 파티클 스트림
                    if (routeCoords.size >= 2) {
                        val density = LocalDensity.current
                        val screenWidthPx = with(density) { 400.dp.toPx() }
                        val screenHeightPx = with(density) { 800.dp.toPx() }
                        val routeOffsets = remember(routeCoords, currentLat, currentLng) {
                            routeCoords.map { coord ->
                                val scale = Math.pow(2.0, 15.0) * 256.0 / 360.0
                                androidx.compose.ui.geometry.Offset(
                                    x = ((coord.lng - currentLng) * scale + screenWidthPx / 2).toFloat(),
                                    y = ((-coord.lat + currentLat) * scale + screenHeightPx / 2).toFloat()
                                )
                            }
                        }
                        RouteParticleOverlay(
                            routePoints = routeOffsets,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else if (!voiceOnly) {
                    // Layer 1 대체: Ambient Intelligence Dashboard
                    AmbientDashboard(
                        ambientData = ambientData,
                        quickDestinations = quickDestinations,
                        onQuickDestination = { address ->
                            viewModel.currentLat = currentLat
                            viewModel.currentLng = currentLng
                            viewModel.sendAgentMessage("$address 으로 경로 안내해줘")
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Layer 2: Top bar (OBSIDIAN v2 반투명)
                ConversationTopBar(
                    skillCount = activeSkillCount,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onSearchClick = { navController?.navigate(NavRoutes.SEARCH) },
                    modifier = Modifier.align(Alignment.TopStart)
                )

                // Layer 2.5: 컨텍스트 칩 (지도 하단)
                ContextChipRow(
                    chips = buildList {
                        add(ChipData("◎", "현재 위치", null) { /* 지도 줌 */ })
                        if (scheduleCount > 0) {
                            add(ChipData("◉", "$scheduleCount 일정", "오늘") {
                                viewModel.sendAgentMessage("오늘 일정 알려줘")
                            })
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 180.dp)
                )

                // Layer 3: Dynamic cards overlay
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 48.dp)
                ) {
                    Spacer(modifier = Modifier.weight(1f))

                    when (val state = uiState) {
                        is AgentUiState.Processing -> {
                            AnimatedVisibility(
                                visible = true,
                                enter = slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(300)),
                                exit = fadeOut(tween(200))
                            ) {
                                ProcessingCard(hint = state.hint)
                            }
                        }
                        is AgentUiState.AgentCard -> {
                            // CardType별 전용 카드 분기
                            when (state.type) {
                                CardType.CONFIRMATION -> {
                                    AnimatedVisibility(
                                        visible = true,
                                        enter = slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(300)),
                                        exit = fadeOut(tween(150))
                                    ) {
                                        ConfirmationCard(
                                            message = state.body,
                                            onConfirm = { viewModel.sendAgentMessage("출발") },
                                            onCancel = { viewModel.clearConversation() },
                                            modifier = Modifier.padding(horizontal = 12.dp)
                                        )
                                    }
                                }
                                CardType.PROACTIVE -> {
                                    // 상단에서 슬라이드 다운 (ui_flow.md §5 PROACTIVE_SUGGESTION)
                                    AnimatedVisibility(
                                        visible = true,
                                        enter = slideInVertically(initialOffsetY = { -it }),
                                        exit = slideOutVertically(targetOffsetY = { -it })
                                    ) {
                                        ProactiveSuggestionCard(
                                            message = state.body,
                                            onAction = { viewModel.sendAgentMessage(state.actions.firstOrNull() ?: "출발") },
                                            onDismiss = { viewModel.clearConversation() },
                                            modifier = Modifier.padding(horizontal = 12.dp)
                                        )
                                    }
                                }
                                else -> {
                                    // SharedElement 모핑 전환 지원 카드
                                    CardAnimationHost(
                                        card = state,
                                        expandedCard = expandedCard,
                                        onExpand = { expandedCard = state },
                                        onCollapse = { expandedCard = null },
                                        onAction = { action -> viewModel.sendAgentMessage(action) },
                                        onDismiss = { viewModel.clearConversation() }
                                    )
                                }
                            }
                        }
                        is AgentUiState.AgentMessage -> {
                            AnimatedVisibility(
                                visible = true,
                                enter = slideInVertically(tween(350, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(350)),
                                exit = slideOutVertically(tween(200)) { it / 4 } + fadeOut(tween(200))
                            ) {
                                TextReplyCard(
                                    text = state.text,
                                    traceSummary = lastTraceSummary,
                                    onDismiss = { viewModel.clearConversation() },
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                            }
                        }
                        is AgentUiState.RoutePreview -> {
                            // Route carousel shown below
                        }
                        is AgentUiState.ShowPlaces -> {
                            // AgentBubble for the message
                            if (state.message.isNotBlank()) {
                                AgentBubble(
                                    text = state.message,
                                    onDismiss = { }
                                )
                            }
                        }
                        is AgentUiState.ShowSchedule -> {
                            SchedulePopup(
                                events = state.events,
                                message = state.message,
                                onNavigateToEvent = { event ->
                                    viewModel.sendAgentMessage("${event.location}으로 안내해줘")
                                },
                                onDismiss = { viewModel.clearConversation() }
                            )
                        }
                        is AgentUiState.ShowMessageDraft -> {
                            MessageDraftCard(
                                recipient = state.recipient,
                                phone = state.phone,
                                message = state.message,
                                method = state.method,
                                onDismiss = { viewModel.clearConversation() }
                            )
                        }
                        is AgentUiState.ShowCallConfirmation -> {
                            CallConfirmationCard(
                                name = state.name,
                                phone = state.phone,
                                onCall = {
                                    try {
                                        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${state.phone}"))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Call failed: ${e.message}")
                                    }
                                    viewModel.clearConversation()
                                },
                                onDismiss = { viewModel.clearConversation() }
                            )
                        }
                        else -> {}
                    }

                    // Route carousel + 경로 비교 버튼
                    if (routeOptions.isNotEmpty() && uiState is AgentUiState.RoutePreview) {
                        RouteCarousel(
                            routes = routeOptions,
                            selectedIndex = selectedRouteIndex,
                            onRouteSelected = { viewModel.highlightRoute(it) },
                            onStartNavigation = { route ->
                                if (navController != null) {
                                    val routeJson = Gson().toJson(route)
                                    navController.currentBackStackEntry?.savedStateHandle?.set("routeJson", routeJson)
                                    navController.navigate(NavRoutes.NAVIGATION)
                                }
                            },
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        // 경로 비교 전체화면 버튼
                        if (navController != null && routeOptions.size > 1) {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    val routeOptionsList = routeOptions.mapIndexed { idx, r ->
                                        RouteOption(
                                            label = if (idx == 0) "추천 경로" else "대안 ${idx}",
                                            durationMin = r.duration_s / 60,
                                            distanceKm = r.distance_m / 1000f,
                                            fare = 0,
                                            tag = if (idx == 0) "추천" else "대안",
                                            color = if (idx == 0) Accent else AccentDim,
                                            routeJson = Gson().toJson(r)
                                        )
                                    }
                                    navController.currentBackStackEntry?.savedStateHandle?.apply {
                                        set("originName", "현재 위치")
                                        set("destName", "목적지")
                                        set("routeOptionsJson", Gson().toJson(routeOptionsList))
                                    }
                                    navController.navigate(NavRoutes.ROUTE_SELECTION)
                                },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                Text("경로 비교 전체화면 →", color = Accent, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // Layer 4: Places overlay at bottom
                if (uiState is AgentUiState.ShowPlaces) {
                    val showPlacesState = uiState as AgentUiState.ShowPlaces
                    PlacesOverlay(
                        places = showPlacesState.places,
                        onPlaceSelected = { place ->
                            kakaoMapRef?.moveCamera(
                                CameraUpdateFactory.newCenterPosition(
                                    LatLng.from(place.lat, place.lng), 16
                                )
                            )
                        },
                        onNavigateToPlace = { place ->
                            viewModel.sendAgentMessage("${place.name}(${place.lat},${place.lng})로 안내해줘")
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 100.dp)
                    )
                }

                // Layer 5: Partial text overlay
                if (micState == MicState.LISTENING && partialText.isNotBlank()) {
                    AgentBubble(
                        text = "\uD83C\uDFA4 $partialText",
                        onDismiss = {},
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 180.dp)
                    )
                }

                // Layer 6: Debug text command panel (debug builds only)
                if (com.example.ez_capstone.BuildConfig.DEBUG && SHOW_DEBUG_COMMAND_PANEL) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 12.dp, bottom = 100.dp)
                            .windowInsetsPadding(WindowInsets.navigationBars),
                        horizontalAlignment = Alignment.Start
                    ) {
                        if (debugExpanded) {
                            androidx.compose.material3.Surface(
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                color = com.example.ez_capstone.ui.theme.Surface.copy(alpha = 0.95f),
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.OutlinedTextField(
                                        value = debugText,
                                        onValueChange = { debugText = it },
                                        placeholder = { Text("명령어 입력...", fontSize = 12.sp, color = com.example.ez_capstone.ui.theme.TextSecondary) },
                                        modifier = Modifier.width(220.dp),
                                        singleLine = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = com.example.ez_capstone.ui.theme.TextPrimary)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    androidx.compose.material3.Button(
                                        onClick = {
                                            val cmd = debugText.trim()
                                            if (cmd.isNotBlank()) {
                                                viewModel.processVoiceText(cmd, currentLat, currentLng)
                                                debugText = ""
                                            }
                                        },
                                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                            containerColor = com.example.ez_capstone.ui.theme.Cyan.copy(alpha = 0.2f),
                                            contentColor = com.example.ez_capstone.ui.theme.Cyan
                                        ),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) { Text("전송", fontSize = 12.sp) }
                                }
                            }
                        }
                        androidx.compose.material3.SmallFloatingActionButton(
                            onClick = { debugExpanded = !debugExpanded },
                            containerColor = com.example.ez_capstone.ui.theme.Surface,
                            contentColor = com.example.ez_capstone.ui.theme.Cyan,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text(if (debugExpanded) "×" else "✎", fontSize = 14.sp, color = com.example.ez_capstone.ui.theme.Cyan)
                        }
                    }
                }

                // Camera FAB
                SmallFloatingActionButton(
                    onClick = {
                        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = Color(0xFF4FC3F7),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 82.dp, bottom = 102.dp)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .semantics { contentDescription = "카메라로 장소 인식" }
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                }

                // Mic FAB
                MicFAB(
                    state = micState,
                    onClick = {
                        when (micState) {
                            MicState.IDLE -> {
                                if (!hasAudioPermission) {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    return@MicFAB
                                }
                                coordinator.triggerVoiceInput()
                            }
                            MicState.LISTENING -> {
                                // Coordinator handles STT automatically after wake word
                            }
                            MicState.PROCESSING -> { /* ignore taps while processing */ }
                            MicState.SPEAKING -> {
                                // 마이크 탭 = "지금 말할게요" → 바지인: TTS 즉시 중단 + 청취 시작.
                                if (!hasAudioPermission) {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    return@MicFAB
                                }
                                coordinator.triggerVoiceInput()
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 14.dp, bottom = 90.dp)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                )
            }
        }
    }
}

// ── Side Drawer Content ──

@Composable
private fun DrawerContent(
    onHomeClick: () -> Unit,
    onProfileClick: () -> Unit,
    onScheduleClick: () -> Unit,
    onContactsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = Surface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "◆ EZMAP",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Accent
            )
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = Accent.copy(alpha = 0.12f))
            Spacer(modifier = Modifier.height(16.dp))
        }

        DrawerMenuItem(icon = Icons.Filled.Home, label = "홈", isSelected = true, onClick = onHomeClick)
        DrawerMenuItem(icon = Icons.Filled.Person, label = "프로필", onClick = onProfileClick)
        DrawerMenuItem(icon = Icons.Filled.CalendarMonth, label = "일정", onClick = onScheduleClick)
        DrawerMenuItem(icon = Icons.Filled.Contacts, label = "연락처", onClick = onContactsClick)
        DrawerMenuItem(icon = Icons.Filled.Settings, label = "설정", onClick = onSettingsClick)

        Spacer(modifier = Modifier.weight(1f))

        // 하단 버전
        Text(
            text = "v2.2.0",
            color = TextDim,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 24.dp, bottom = 24.dp)
        )
    }
}

@Composable
private fun DrawerMenuItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = label, tint = if (isSelected) Accent else TextSecondary) },
        label = { Text(label, color = if (isSelected) Accent else TextPrimary, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal) },
        selected = isSelected,
        onClick = onClick,
        colors = NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = Surface,
            selectedContainerColor = Accent.copy(alpha = 0.08f)
        ),
        modifier = Modifier.padding(horizontal = 8.dp)
    )
}

// ── Card Shared Element Transition ──

/**
 * DynamicCard ↔ CardDetailOverlay 사이의 SharedElement 모핑 전환 호스트.
 *
 * 카드를 탭하면 전체화면 상세뷰로 scale+fade 확장, 배경 탭 시 복귀.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CardAnimationHost(
    card: AgentUiState.AgentCard,
    expandedCard: AgentUiState.AgentCard?,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onAction: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isExpanded = expandedCard != null

    SharedTransitionLayout(modifier = modifier) {
        AnimatedContent(
            targetState = isExpanded,
            transitionSpec = {
                (fadeIn(animationSpec = tween(280)) +
                 scaleIn(initialScale = 0.88f, animationSpec = tween(280, easing = FastOutSlowInEasing))
                ) togetherWith (
                 fadeOut(animationSpec = tween(200)) +
                 scaleOut(targetScale = 0.88f, animationSpec = tween(200))
                )
            },
            label = "cardExpand"
        ) { expanded ->
            if (!expanded) {
                DynamicCard(
                    cardState = card,
                    modifier = Modifier.sharedBounds(
                        rememberSharedContentState(key = "card_morph"),
                        animatedVisibilityScope = this
                    ),
                    onAction = onAction,
                    onDismiss = onDismiss,
                    onExpand = onExpand
                )
            } else {
                CardDetailOverlay(
                    card = card,
                    modifier = Modifier.sharedBounds(
                        rememberSharedContentState(key = "card_morph"),
                        animatedVisibilityScope = this
                    ),
                    onAction = onAction,
                    onDismiss = onCollapse
                )
            }
        }
    }
}

/**
 * 카드 전체화면 확장 오버레이.
 * 자이로스코프 패럴렉스 + 홀로그램 효과 활성화.
 */
@Composable
private fun CardDetailOverlay(
    card: AgentUiState.AgentCard,
    modifier: Modifier = Modifier,
    onAction: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val style = cardStyleFor(card.type)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Background.copy(alpha = 0.96f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        com.example.ez_capstone.ui.components.GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}  // 카드 내부 탭은 dismiss 막기
                ),
            glowColor = style.color,
            glowEnabled = true,
            contentPadding = 24.dp,
            holoEnabled = true,
            parallaxEnabled = true
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = style.icon,
                        contentDescription = null,
                        tint = style.color,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = card.title.ifBlank { card.type.name },
                        style = MaterialTheme.typography.headlineSmall,
                        color = style.color
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = card.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
                if (card.actions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        card.actions.forEach { action ->
                            androidx.compose.material3.Button(
                                onClick = { onAction(action); onDismiss() },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = style.color.copy(alpha = 0.2f)
                                )
                            ) {
                                Text(action, color = style.color)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "탭하여 닫기",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

// ── 8.5.3: 지도 위 환영 오버레이 ──
@Composable
private fun WelcomeMapOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceHigh.copy(alpha = 0.85f))
            .border(1.dp, Accent.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .padding(horizontal = 28.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ic_logo_ez 에셋 사용
            androidx.compose.foundation.Image(
                painter = painterResource(id = R.drawable.ic_logo_ez),
                contentDescription = "EZmap",
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = "EZmap",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = Orbitron,
                    fontWeight = FontWeight.Bold,
                    color = Accent,
                    letterSpacing = 1.sp
                )
            )
            Text(
                text = "AI MOBILITY AGENT",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = Rajdhani,
                    color = TextDim,
                    letterSpacing = 3.sp
                )
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "마이크를 탭하거나 메시지를 입력하세요",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary,
                    fontFamily = Rajdhani
                )
            )
        }
    }
}
