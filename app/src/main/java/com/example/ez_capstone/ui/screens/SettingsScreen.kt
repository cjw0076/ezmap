package com.example.ez_capstone.ui.screens

import android.app.Activity
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.example.ez_capstone.R
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ez_capstone.profile.ProfileType
import com.example.ez_capstone.ui.theme.Accent
import com.example.ez_capstone.ui.theme.AccentEnd
import com.example.ez_capstone.ui.theme.AccentStart
import com.example.ez_capstone.ui.theme.Alert
import com.example.ez_capstone.ui.theme.Background
import com.example.ez_capstone.ui.theme.CardBackground
import com.example.ez_capstone.ui.theme.Green
import com.example.ez_capstone.ui.theme.Secondary
import com.example.ez_capstone.ui.theme.TextDim
import com.example.ez_capstone.ui.theme.TextPrimary
import com.example.ez_capstone.ui.theme.TextSecondary
import com.example.ez_capstone.ui.theme.Warning
import com.example.ez_capstone.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToOnboarding: () -> Unit = {},
    onNavigateToDev: () -> Unit = {},
    onNavigateToAgentMemory: () -> Unit = {},
    onNavigateToMcp: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val logoutTriggered by viewModel.logoutEvent.collectAsState()
    val resetTriggered by viewModel.resetApiKeysEvent.collectAsState()

    // Spotify Custom Tab 인증 후 복귀(ON_RESUME) 시 연결 상태 새로고침
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.refreshSpotifyState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(logoutTriggered) {
        if (logoutTriggered) onLogout()
    }

    LaunchedEffect(resetTriggered) {
        if (resetTriggered) onNavigateToOnboarding()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Top bar
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = TextPrimary)
            }
            Text(
                text = "설정",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── 음성 섹션 ──
        SectionHeader("음성")
        Spacer(modifier = Modifier.height(12.dp))

        // TTS 속도
        SettingsCard {
            SettingsSliderItem(
                icon = Icons.Filled.Speed,
                label = "TTS 속도",
                value = state.ttsSpeed,
                valueLabel = "%.1fx".format(state.ttsSpeed),
                range = 0.5f..2.0f,
                onValueChange = { viewModel.setTtsSpeed(it) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Wake word 감도
        SettingsCard {
            SettingsSliderItem(
                icon = Icons.Filled.Mic,
                label = "Wake word 감도",
                value = state.wakeSensitivity,
                valueLabel = "${(state.wakeSensitivity * 100).toInt()}%",
                range = 0f..1f,
                onValueChange = { viewModel.setWakeSensitivity(it) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // TTS 활성화
        SettingsCard {
            SettingsSwitchItem(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                label = "TTS 활성화",
                checked = state.ttsEnabled,
                onCheckedChange = { viewModel.setTtsEnabled(it) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── 내비게이션 옵션 섹션 ──
        SectionHeader("내비게이션 옵션")
        Spacer(modifier = Modifier.height(12.dp))
        SettingsCard {
            Column {
                NaviOptionItem(
                    iconRes = R.drawable.img_narrow_street,
                    label = "좁은 도로 회피",
                    description = "좁은 골목길 대신 넓은 도로 우선",
                    checked = state.naviOptions["avoid_narrow"] ?: false,
                    onToggle = { viewModel.toggleNaviOption("avoid_narrow") }
                )
                HorizontalDivider(color = TextDim.copy(0.1f), modifier = Modifier.padding(horizontal = 4.dp))
                NaviOptionItem(
                    iconRes = R.drawable.img_lane_num,
                    label = "넓은 도로 선호",
                    description = "차선이 많은 대로 우선 선택",
                    checked = state.naviOptions["prefer_wide"] ?: false,
                    onToggle = { viewModel.toggleNaviOption("prefer_wide") }
                )
                HorizontalDivider(color = TextDim.copy(0.1f), modifier = Modifier.padding(horizontal = 4.dp))
                NaviOptionItem(
                    iconRes = R.drawable.img_left_turn,
                    label = "좌회전 최소화",
                    description = "우회전 중심 경로로 안전하게",
                    checked = state.naviOptions["minimize_left_turn"] ?: false,
                    onToggle = { viewModel.toggleNaviOption("minimize_left_turn") }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── 사용자 모드 섹션 ──
        SectionHeader("사용자 모드")
        Spacer(modifier = Modifier.height(12.dp))

        SettingsCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ProfileModeOption(
                    label = "나 (Owner)",
                    description = "기억 ON · 모든 기능 사용",
                    selected = state.activeProfileType == ProfileType.OWNER,
                    accentColor = Accent,
                    onClick = { viewModel.setActiveProfile(ProfileType.OWNER) }
                )
                HorizontalDivider(color = TextDim.copy(alpha = 0.1f))
                ProfileModeOption(
                    label = "가족 (Family)",
                    description = "기억 ON · 개인정보 접근 허용",
                    selected = state.activeProfileType == ProfileType.FAMILY,
                    accentColor = Accent,
                    onClick = { viewModel.setActiveProfile(ProfileType.FAMILY) }
                )
                HorizontalDivider(color = TextDim.copy(alpha = 0.1f))
                ProfileModeOption(
                    label = "게스트 (Guest)",
                    description = "기억 OFF · 개인정보 차단",
                    selected = state.activeProfileType == ProfileType.GUEST,
                    accentColor = Secondary,
                    onClick = { viewModel.setActiveProfile(ProfileType.GUEST) }
                )
                HorizontalDivider(color = TextDim.copy(alpha = 0.1f))
                ProfileModeOption(
                    label = "대리운전 (Valet)",
                    description = "내비게이션만 · 개인정보 전체 차단",
                    selected = state.activeProfileType == ProfileType.VALET,
                    accentColor = Alert,
                    onClick = { viewModel.setActiveProfile(ProfileType.VALET) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // AI 메모리 투명성
        SettingsCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToAgentMemory)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Person, null, tint = Secondary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("AI가 아는 나의 정보", fontSize = 14.sp, color = TextPrimary)
                    Text("에이전트가 활용하는 데이터 투명하게 보기", fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // ── 도구 확장 (MCP) ──
        SectionHeader("도구 확장")
        Spacer(modifier = Modifier.height(12.dp))
        SettingsCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToMcp)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Hub, null, tint = Secondary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("MCP 도구 서버", fontSize = 14.sp, color = TextPrimary)
                    Text("외부 MCP 서버 도구를 음성으로 사용", fontSize = 12.sp, color = TextSecondary)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, tint = TextDim, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // ── 계정 섹션 ──
        SectionHeader("계정")
        Spacer(modifier = Modifier.height(12.dp))

        SettingsCard {
            Column {
                // 프로필 이름
                SettingsInfoItem(
                    icon = Icons.Filled.Person,
                    label = "프로필",
                    value = state.profileName.ifEmpty { "로그인 필요" }
                )
                HorizontalDivider(color = Secondary.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))
                // 로그아웃
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { viewModel.logout() }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = Alert,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("로그아웃", fontSize = 14.sp, color = Alert)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── API 키 관리 섹션 ──
        SectionHeader("API 키 관리")
        Spacer(modifier = Modifier.height(12.dp))

        SettingsCard {
            Column {
                // 모드 상태 배지
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Key,
                            contentDescription = null,
                            tint = Secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("모드", fontSize = 14.sp, color = TextPrimary)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (state.isTrialMode) Alert.copy(alpha = 0.15f)
                                else Secondary.copy(alpha = 0.15f)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (state.isTrialMode) "체험 모드" else "개인 키",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (state.isTrialMode) Alert else Secondary
                        )
                    }
                }

                HorizontalDivider(
                    color = Secondary.copy(alpha = 0.1f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // 키별 개별 편집 — 온보딩 전체를 다시 타지 않고 키 하나만 추가/변경
                EditableApiKeyRow(
                    label = "Gemini",
                    hint = "필수 · AI 에이전트",
                    isSet = state.geminiKeySet,
                    onSave = { viewModel.setApiKey(SettingsViewModel.ApiKeyType.GEMINI, it) }
                )
                EditableApiKeyRow(
                    label = "Kakao",
                    hint = "필수 · 지도/검색/경로",
                    isSet = state.kakaoKeySet,
                    onSave = { viewModel.setApiKey(SettingsViewModel.ApiKeyType.KAKAO, it) }
                )
                EditableApiKeyRow(
                    label = "공공데이터포털",
                    hint = "날씨·미세먼지·충전소·주차장·약국·병원",
                    isSet = state.dataGoKrKeySet,
                    onSave = { viewModel.setApiKey(SettingsViewModel.ApiKeyType.DATA_GO_KR, it) }
                )
                EditableApiKeyRow(
                    label = "오피넷",
                    hint = "주유소 가격",
                    isSet = state.opinetKeySet,
                    onSave = { viewModel.setApiKey(SettingsViewModel.ApiKeyType.OPINET, it) }
                )
                EditableApiKeyRow(
                    label = "네이버 Client ID",
                    hint = "듀얼 경로 탐색",
                    isSet = state.naverKeySet,
                    onSave = { viewModel.setApiKey(SettingsViewModel.ApiKeyType.NAVER_ID, it) }
                )
                EditableApiKeyRow(
                    label = "네이버 Client Secret",
                    hint = "듀얼 경로 탐색",
                    isSet = state.naverKeySet,
                    onSave = { viewModel.setApiKey(SettingsViewModel.ApiKeyType.NAVER_SECRET, it) }
                )
                EditableApiKeyRow(
                    label = "ODsay",
                    hint = "대중교통 경로",
                    isSet = state.odsayKeySet,
                    onSave = { viewModel.setApiKey(SettingsViewModel.ApiKeyType.ODSAY, it) }
                )

                // ── Spotify 음악 제어 ──
                EditableApiKeyRow(
                    label = "Spotify Client ID",
                    hint = "음악 제어 (developer.spotify.com)",
                    isSet = state.spotifyClientIdSet,
                    onSave = { viewModel.setApiKey(SettingsViewModel.ApiKeyType.SPOTIFY_CLIENT_ID, it) }
                )
                if (state.spotifyClientIdSet) {
                    val spotifyCtx = LocalContext.current
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Spotify 계정 연결", fontSize = 14.sp, color = TextPrimary)
                            Text(
                                if (state.spotifyConnected) "연결됨 (Premium 필요)" else "재생 제어하려면 로그인",
                                fontSize = 11.sp,
                                color = if (state.spotifyConnected) Green else TextDim
                            )
                        }
                        if (state.spotifyConnected) {
                            TextButton(onClick = { viewModel.disconnectSpotify() }) {
                                Text("연결 해제", color = Alert, fontSize = 13.sp)
                            }
                        } else {
                            Button(
                                onClick = {
                                    val url = viewModel.buildSpotifyAuthUrl()
                                    if (url != null) {
                                        spotifyCtx.startActivity(
                                            android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse(url)
                                            )
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Green.copy(alpha = 0.18f), contentColor = Green
                                )
                            ) { Text("연결", fontSize = 13.sp) }
                        }
                    }
                }

                HorizontalDivider(
                    color = Secondary.copy(alpha = 0.1f),
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                // 전체 재설정 버튼 (온보딩으로)
                Button(
                    onClick = { viewModel.resetApiKeys() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Alert.copy(alpha = 0.15f),
                        contentColor = Alert
                    )
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "API 키 재설정",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── 개발자 도구 ──
        androidx.compose.material3.TextButton(onClick = onNavigateToDev) {
            Text("개발자 도구 (Dev Page)", color = Secondary, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── 앱 정보 섹션 ──
        SectionHeader("앱 정보")
        Spacer(modifier = Modifier.height(12.dp))

        SettingsCard {
            Column {
                SettingsInfoItem(
                    icon = Icons.Filled.Info,
                    label = "버전",
                    value = state.appVersion
                )
                HorizontalDivider(color = Secondary.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when (state.serverStatus) {
                                SettingsViewModel.ServerStatus.ONLINE -> Icons.Filled.Cloud
                                else -> Icons.Filled.CloudOff
                            },
                            contentDescription = null,
                            tint = Secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("서버 상태", fontSize = 14.sp, color = TextPrimary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when (state.serverStatus) {
                                        SettingsViewModel.ServerStatus.ONLINE -> Secondary
                                        SettingsViewModel.ServerStatus.OFFLINE -> Alert
                                        SettingsViewModel.ServerStatus.CHECKING -> TextSecondary
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (state.serverStatus) {
                                SettingsViewModel.ServerStatus.ONLINE -> "연결됨"
                                SettingsViewModel.ServerStatus.OFFLINE -> "오프라인"
                                SettingsViewModel.ServerStatus.CHECKING -> "확인 중..."
                            },
                            fontSize = 14.sp,
                            color = when (state.serverStatus) {
                                SettingsViewModel.ServerStatus.ONLINE -> Secondary
                                SettingsViewModel.ServerStatus.OFFLINE -> Alert
                                SettingsViewModel.ServerStatus.CHECKING -> TextSecondary
                            }
                        )
                    }
                }
            }
        }

        // ── 에이전트 권한 (Phase 4.5.8, R1) ──
        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader("에이전트 권한")
        Spacer(modifier = Modifier.height(12.dp))
        SettingsCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PermissionToggle("위치 정보", "현재 위치 파악", true)
                PermissionToggle("캘린더 읽기", "일정 기반 출발 알림", true)
                PermissionToggle("프로액티브 알림", "먼저 알림 보내기", true)
                PermissionToggle("패턴 학습", "이동 패턴 학습", true)
                PermissionToggle("대화 기억", "선호도/대화 저장", true)
                PermissionToggle("Skill 자동 체이닝", "여러 기능 자동 연결", true)
            }
        }

        // ── 안전 (Phase 4.5.8, R2) ──
        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader("안전")
        Spacer(modifier = Modifier.height(12.dp))
        SettingsCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PermissionToggle("운전 중 간소화", "주행 중 복잡한 UI 차단", true)
                PermissionToggle("과속 경고", "제한속도 초과 시 알림", true)
                PermissionToggle("악천후 보수적 모드", "안전 경로 우선", true)
            }
        }

        // ── 진단 ──
        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader("진단")
        Spacer(modifier = Modifier.height(12.dp))
        SettingsCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsSwitchItem(
                    icon = Icons.Filled.BugReport,
                    label = "오류 보고",
                    checked = state.crashReportingEnabled,
                    onCheckedChange = { viewModel.setCrashReporting(it) }
                )
                Text(
                    "앱 크래시 정보를 익명으로 수집합니다. 위치·주소·API 키는 전송되지 않습니다. 기본값: 꺼짐.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }

        // ── 데이터 (Phase 4.5.8, R6) ──
        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader("데이터")
        Spacer(modifier = Modifier.height(12.dp))
        SettingsCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SettingsActionRow("기억 전체 삭제") { viewModel.purgeAllMemory() }
                HorizontalDivider(color = TextDim.copy(alpha = 0.1f))
                SettingsActionRow("캐시 삭제") { /* 향후 구현 */ }
            }
        }

        // ── 접근성 ──
        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader("접근성")
        Spacer(modifier = Modifier.height(12.dp))
        val activity = LocalContext.current as? Activity
        SettingsCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("고대비 모드", fontSize = 14.sp, color = TextPrimary)
                        Text("텍스트/배경 대비를 높여 가독성 개선", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = state.highContrastMode,
                        onCheckedChange = { enabled ->
                            viewModel.setHighContrastMode(enabled)
                            activity?.recreate()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Accent,
                            checkedTrackColor = Accent.copy(alpha = 0.3f)
                        )
                    )
                }
                HorizontalDivider(color = TextDim.copy(alpha = 0.1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("음성 전용 모드", fontSize = 14.sp, color = TextPrimary)
                        Text("지도 숨김, TTS·STT만으로 사용 (재시작 필요)", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = state.voiceOnlyMode,
                        onCheckedChange = { enabled ->
                            viewModel.setVoiceOnlyMode(enabled)
                            activity?.recreate()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Accent,
                            checkedTrackColor = Accent.copy(alpha = 0.3f)
                        )
                    )
                }
                HorizontalDivider(color = TextDim.copy(alpha = 0.1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("큰 글씨 모드", fontSize = 14.sp, color = TextPrimary)
                        Text("시스템 설정 → 글꼴 크기에서 변경", fontSize = 11.sp, color = TextSecondary)
                    }
                    Text("시스템 설정 →", fontSize = 12.sp, color = Accent,
                        modifier = Modifier.clickable {
                            val intent = android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            activity?.startActivity(intent)
                        }
                    )
                }
            }
        }

        // ── 데모 모드 (쇼케이스/발표용) ──
        SectionHeader("발표 / 쇼케이스")
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("데모 모드", fontSize = 12.sp, color = TextSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("데모 모드", fontSize = 14.sp, color = if (state.isDemoMode) Accent else TextPrimary)
                        Text("ON 시 실제 API 없이 미리 준비된 응답으로 시연", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = state.isDemoMode,
                        onCheckedChange = { viewModel.toggleDemoMode() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Accent,
                            checkedTrackColor = Accent.copy(alpha = 0.3f)
                        )
                    )
                }
                HorizontalDivider(color = TextDim.copy(alpha = 0.1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Gemini Live 실험 모드", fontSize = 14.sp, color = if (state.liveApiMode) Warning else TextPrimary)
                        Text("WebSocket 음성 스트리밍. 기기 검증 전에는 발표용 실험 기능", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = state.liveApiMode,
                        onCheckedChange = { viewModel.setLiveApiMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Warning,
                            checkedTrackColor = Warning.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun PermissionToggle(
    label: String,
    description: String,
    initialValue: Boolean
) {
    var checked by remember { mutableStateOf(initialValue) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, color = TextPrimary)
            Text(description, fontSize = 11.sp, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = { checked = it },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Accent,
                checkedTrackColor = Accent.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
private fun SettingsActionRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = TextPrimary)
        Text("→", color = TextSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = Secondary
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground.copy(alpha = 0.6f))
            .border(1.dp, Secondary.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
private fun SettingsSliderItem(
    icon: ImageVector,
    label: String,
    value: Float,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = Secondary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(label, fontSize = 14.sp, color = TextPrimary)
            }
            Text(valueLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Secondary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Secondary,
                activeTrackColor = AccentEnd,
                inactiveTrackColor = CardBackground
            )
        )
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Secondary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(label, fontSize = 14.sp, color = TextPrimary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TextPrimary,
                checkedTrackColor = AccentEnd,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = CardBackground
            )
        )
    }
}

@Composable
private fun SettingsInfoItem(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Secondary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(label, fontSize = 14.sp, color = TextPrimary)
        }
        Text(value, fontSize = 14.sp, color = TextSecondary)
    }
}

@Composable
private fun EditableApiKeyRow(
    label: String,
    hint: String,
    isSet: Boolean,
    onSave: (String) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 14.sp, color = TextPrimary)
                Text(hint, fontSize = 11.sp, color = TextDim)
            }
            Text(
                text = if (isSet) "설정됨" else "미설정",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (isSet) Secondary else Alert
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { editing = !editing; if (editing) text = "" },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    if (isSet) Icons.Filled.Edit else Icons.Filled.Add,
                    contentDescription = if (isSet) "$label 키 변경" else "$label 키 추가",
                    tint = Accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (editing) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("키 입력", fontSize = 13.sp, color = TextDim) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(fontSize = 13.sp, color = TextPrimary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (text.isNotBlank()) { onSave(text); editing = false; text = "" }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent.copy(alpha = 0.18f),
                        contentColor = Accent
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) { Text("저장", fontSize = 13.sp) }
            }
        }
    }
}

@Composable
private fun ApiKeyStatusRow(label: String, isSet: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = TextSecondary)
        Text(
            text = if (isSet) "\u2705 설정됨" else "\u274C 미설정",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (isSet) Secondary else Alert
        )
    }
}

@Composable
private fun ProfileModeOption(
    label: String,
    description: String,
    selected: Boolean,
    accentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = accentColor,
                unselectedColor = TextSecondary
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, color = TextPrimary, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            Text(description, fontSize = 11.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun NaviOptionItem(
    iconRes: Int,
    label: String,
    description: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CardBackground)
                .padding(8.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(description, color = TextSecondary, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Background,
                checkedTrackColor = Accent
            )
        )
    }
}
