package com.example.ez_capstone.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ez_capstone.ui.components.GlassCard
import com.example.ez_capstone.ui.theme.Accent
import com.example.ez_capstone.ui.theme.AccentDim
import com.example.ez_capstone.ui.theme.Background
import com.example.ez_capstone.ui.theme.Error
import com.example.ez_capstone.ui.theme.Rajdhani
import com.example.ez_capstone.ui.theme.Success
import com.example.ez_capstone.ui.theme.Surface
import com.example.ez_capstone.ui.theme.TextDim
import com.example.ez_capstone.ui.theme.TextPrimary
import com.example.ez_capstone.ui.theme.TextSecondary
import com.example.ez_capstone.ui.theme.Warning
import com.example.ez_capstone.db.entity.ProfileEntity
import com.example.ez_capstone.memory.MemoryGovernor
import com.example.ez_capstone.skill.CustomRoutineEntity
import com.example.ez_capstone.skill.SkillHealth
import com.example.ez_capstone.skill.SkillStatus
import com.example.ez_capstone.viewmodel.ProfileViewModel

private val prefColors = listOf(Warning, Accent, Success, AccentDim, Error)

@Composable
fun ProfileScreen(
    onBackClick: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val skillHealthList by viewModel.skillHealthList.collectAsState()
    val memoryStats by viewModel.memoryStats.collectAsState()
    val routines by viewModel.routines.collectAsState()
    val editProfile by viewModel.editProfile.collectAsState()

    val completeness = remember(editProfile) {
        val p = editProfile ?: return@remember 0f
        var filled = 0
        if (!p.homeAddress.isNullOrBlank()) filled++
        if (!p.workAddress.isNullOrBlank()) filled++
        if (p.vehicleType.isNotBlank()) filled++
        if (p.fuelType.isNotBlank()) filled++
        filled / 4f
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = Accent.copy(alpha = 0.7f))
            }
            Text(
                text = "Driver Profile",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = Rajdhani,
                    color = TextPrimary
                )
            )
        }

        // 8.4: 프로필 완성도 바 (completeness < 100%)
        if (completeness < 1f) {
            Spacer(modifier = Modifier.height(4.dp))
            GlassCard(glowEnabled = false, backgroundAlpha = 0.4f, contentPadding = 12.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "프로필 완성도",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = TextSecondary,
                                fontFamily = Rajdhani
                            )
                        )
                        Text(
                            text = "${(completeness * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Accent,
                                fontFamily = Rajdhani,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Surface)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(completeness)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    Brush.horizontalGradient(listOf(Accent, AccentDim))
                                )
                        )
                    }
                    Text(
                        text = "완성하면 AI가 더 정확해집니다",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Warning.copy(alpha = 0.8f),
                            fontFamily = Rajdhani
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Avatar with gradient ring
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    // Gradient ring
                    Canvas(modifier = Modifier.size(88.dp)) {
                        drawCircle(
                            brush = Brush.sweepGradient(listOf(Accent, AccentDim, Accent)),
                            radius = size.minDimension / 2,
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                    // Inner icon
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Accent.copy(alpha = 0.15f), AccentDim.copy(alpha = 0.15f)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(40.dp))
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "AI 학습 레벨 LV.7",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = Rajdhani,
                        color = Accent,
                        letterSpacing = 1.sp
                    )
                )
                Text(
                    text = "${state.totalRoutes} 대화 기반",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextDim)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Stats cards — unique colors
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                value = "${state.totalRoutes}",
                label = "총 경로",
                color = Accent,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                value = "%.0f km".format(state.totalDistance / 1000.0),
                label = "총 거리",
                color = AccentDim,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                value = "${(state.voiceUsageRate * 100).toInt()}%",
                label = "음성 사용",
                color = Success,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Preferences section
        Text(
            text = "AI LEARNED PREFERENCES",
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = Rajdhani,
                color = TextDim,
                letterSpacing = 2.sp
            )
        )
        Spacer(modifier = Modifier.height(12.dp))

        GlassCard(
            glowEnabled = false,
            backgroundAlpha = 0.5f,
            contentPadding = 14.dp
        ) {
            Column {
                state.preferences.forEachIndexed { i, pref ->
                    val color = prefColors[i % prefColors.size]
                    PreferenceBarItem(label = pref.label, value = pref.value, color = color)
                    if (i < state.preferences.lastIndex) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Frequent routes
        Text(
            text = "FREQUENT ROUTES",
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = Rajdhani,
                color = TextDim,
                letterSpacing = 2.sp
            )
        )
        Spacer(modifier = Modifier.height(12.dp))

        state.frequentRoutes.forEach { route ->
            FrequentRouteItem(route)
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ── PROFILE SETTINGS ──
        Spacer(modifier = Modifier.height(24.dp))
        ProfileEditSection(viewModel = viewModel)

        // ── CONNECTED SERVICES (기존 서비스 연결 상태) ──
        Spacer(modifier = Modifier.height(24.dp))
        ConnectedServicesSection(state.serviceStatus)

        // ── SKILL 건강 대시보드 (Phase 4.5.7) ──
        Spacer(modifier = Modifier.height(24.dp))
        SkillHealthSection(skillHealthList)

        // ── EZ의 기억 (Phase 4.5.7) ──
        Spacer(modifier = Modifier.height(24.dp))
        MemorySection(memoryStats)

        // ── 내 루틴 (Phase 4.5.7) ──
        Spacer(modifier = Modifier.height(24.dp))
        RoutineSection(routines, onToggle = { id, active -> viewModel.toggleRoutine(id, active) })

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ── Skill 건강 대시보드 ──

@Composable
private fun SkillHealthSection(healthList: List<SkillHealth>) {
    Text(
        "연결된 Skills",
        style = MaterialTheme.typography.titleMedium.copy(
            fontFamily = Rajdhani, color = Accent, letterSpacing = 2.sp
        ),
        modifier = Modifier.padding(bottom = 12.dp)
    )

    val displayList = healthList.filter { it.totalCalls > 0 }.ifEmpty { healthList.take(5) }

    GlassCard(glowEnabled = false, contentPadding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            displayList.forEach { skill ->
                val statusColor = when (skill.status) {
                    SkillStatus.ACTIVE -> Success
                    SkillStatus.DEGRADED -> Warning
                    SkillStatus.FAILING, SkillStatus.AUTO_DISABLED -> Error
                    SkillStatus.KEY_MISSING -> TextDim
                }
                val statusText = when (skill.status) {
                    SkillStatus.ACTIVE -> "정상"
                    SkillStatus.DEGRADED -> "성능 저하"
                    SkillStatus.FAILING -> "실패 중"
                    SkillStatus.AUTO_DISABLED -> "비활성화"
                    SkillStatus.KEY_MISSING -> "키 없음"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⬡ ", color = statusColor, fontSize = 14.sp)
                        Text(skill.skillName, color = TextPrimary, fontSize = 14.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("● $statusText", color = statusColor, fontSize = 12.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("${skill.totalCalls}회", color = TextDim, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ── EZ의 기억 ──

@Composable
private fun MemorySection(stats: MemoryGovernor.MemoryStats) {
    Text(
        "EZ의 기억",
        style = MaterialTheme.typography.titleMedium.copy(
            fontFamily = Rajdhani, color = Accent, letterSpacing = 2.sp
        ),
        modifier = Modifier.padding(bottom = 12.dp)
    )

    GlassCard(glowEnabled = false, contentPadding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MemoryRow("대화 세션", "${stats.conversationSessions}개")
            MemoryRow("선호도", "${stats.preferences}개")
            MemoryRow("판단 기록", "${stats.decisionTraces}개")
            MemoryRow("자동 만료", "대화 30일 / 판단 7일")
        }
    }
}

@Composable
private fun MemoryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextPrimary, fontSize = 14.sp)
        Text("$value →", color = TextSecondary, fontSize = 13.sp)
    }
}

// ── 내 루틴 ──

@Composable
private fun RoutineSection(routines: List<CustomRoutineEntity>, onToggle: (Long, Boolean) -> Unit) {
    Text(
        "내 루틴",
        style = MaterialTheme.typography.titleMedium.copy(
            fontFamily = Rajdhani, color = Accent, letterSpacing = 2.sp
        ),
        modifier = Modifier.padding(bottom = 12.dp)
    )

    GlassCard(glowEnabled = false, contentPadding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (routines.isEmpty()) {
                Text("아직 학습된 루틴이 없어요", color = TextDim, fontSize = 13.sp)
            } else {
                routines.forEach { routine ->
                    val dayName = routine.triggerDayOfWeek?.let {
                        arrayOf("일", "월", "화", "수", "목", "금", "토").getOrNull(it) ?: ""
                    } ?: "매일"
                    val timeStr = routine.triggerTimeStart?.let { "${it / 60}:${"%02d".format(it % 60)}" } ?: ""
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(routine.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("$dayName $timeStr · ${routine.executionCount}회 실행", color = TextSecondary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = routine.isActive,
                            onCheckedChange = { onToggle(routine.id, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Accent,
                                checkedTrackColor = Accent.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditSection(viewModel: ProfileViewModel) {
    val editProfile by viewModel.editProfile.collectAsState()

    var homeAddr by remember { mutableStateOf("") }
    var workAddr by remember { mutableStateOf("") }
    var vehicleType by remember { mutableStateOf("sedan") }
    var fuelType by remember { mutableStateOf("gasoline") }
    var hasHipass by remember { mutableStateOf(false) }
    var ttsStyle by remember { mutableStateOf("polite") }

    LaunchedEffect(editProfile) {
        editProfile?.let { p ->
            homeAddr = p.homeAddress ?: ""
            workAddr = p.workAddress ?: ""
            vehicleType = p.vehicleType
            fuelType = p.fuelType
            hasHipass = p.hasHipass
            ttsStyle = p.ttsStyle
        }
    }

    Text(
        text = "PROFILE SETTINGS",
        style = MaterialTheme.typography.titleMedium.copy(
            fontFamily = Rajdhani, color = Accent, letterSpacing = 2.sp
        ),
        modifier = Modifier.padding(bottom = 12.dp)
    )

    GlassCard(glowColor = Accent, glowEnabled = true, contentPadding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 집 주소
            OutlinedTextField(
                value = homeAddr,
                onValueChange = { homeAddr = it },
                label = { Text("집 주소", color = if (homeAddr.isBlank()) Warning else TextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = if (homeAddr.isBlank()) Warning.copy(alpha = 0.7f) else TextDim,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Accent
                ),
                trailingIcon = if (homeAddr.isBlank()) {
                    { Icon(Icons.Filled.Cancel, contentDescription = null, tint = Warning.copy(alpha = 0.6f), modifier = Modifier.size(16.dp)) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 회사 주소
            OutlinedTextField(
                value = workAddr,
                onValueChange = { workAddr = it },
                label = { Text("회사 주소", color = if (workAddr.isBlank()) Warning else TextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = if (workAddr.isBlank()) Warning.copy(alpha = 0.7f) else TextDim,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Accent
                ),
                trailingIcon = if (workAddr.isBlank()) {
                    { Icon(Icons.Filled.Cancel, contentDescription = null, tint = Warning.copy(alpha = 0.6f), modifier = Modifier.size(16.dp)) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 차량 유형
            Text("차량 유형", style = MaterialTheme.typography.labelLarge.copy(color = TextSecondary))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("sedan" to "승용차", "SUV" to "SUV", "truck" to "트럭", "electric" to "전기차").forEach { (key, label) ->
                    FilterChip(
                        selected = vehicleType == key,
                        onClick = { vehicleType = key },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Accent.copy(alpha = 0.2f),
                            selectedLabelColor = Accent,
                            containerColor = Surface,
                            labelColor = TextSecondary
                        )
                    )
                }
            }

            // 연료 유형
            Text("연료 유형", style = MaterialTheme.typography.labelLarge.copy(color = TextSecondary))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("gasoline" to "휘발유", "diesel" to "경유", "electric" to "전기", "LPG" to "LPG").forEach { (key, label) ->
                    FilterChip(
                        selected = fuelType == key,
                        onClick = { fuelType = key },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Accent.copy(alpha = 0.2f),
                            selectedLabelColor = Accent,
                            containerColor = Surface,
                            labelColor = TextSecondary
                        )
                    )
                }
            }

            // 하이패스
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("하이패스", style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary))
                Switch(
                    checked = hasHipass,
                    onCheckedChange = { hasHipass = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Accent,
                        checkedTrackColor = Accent.copy(alpha = 0.3f)
                    )
                )
            }

            // TTS 스타일
            Text("TTS 스타일", style = MaterialTheme.typography.labelLarge.copy(color = TextSecondary))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("casual" to "캐주얼", "polite" to "정중한", "ez" to "EZ 스타일").forEach { (key, label) ->
                    FilterChip(
                        selected = ttsStyle == key,
                        onClick = { ttsStyle = key },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentDim.copy(alpha = 0.2f),
                            selectedLabelColor = AccentDim,
                            containerColor = Surface,
                            labelColor = TextSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 저장 버튼
            Button(
                onClick = {
                    viewModel.saveProfile(
                        ProfileEntity(
                            homeAddress = homeAddr.ifBlank { null },
                            workAddress = workAddr.ifBlank { null },
                            vehicleType = vehicleType,
                            fuelType = fuelType,
                            hasHipass = hasHipass,
                            ttsStyle = ttsStyle
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent.copy(alpha = 0.15f),
                    contentColor = Accent
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    "저장",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = Rajdhani, fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier,
        glowColor = color,
        glowEnabled = true,
        contentPadding = 14.dp,
        cornerRadius = 14.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = Rajdhani,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    fontSize = 20.sp
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = Rajdhani,
                    color = TextDim
                )
            )
        }
    }
}

@Composable
private fun PreferenceBarItem(label: String, value: Float, color: Color) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary))
            Text(
                text = "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = Rajdhani,
                    color = color
                )
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        ) {
            // Track
            drawRect(color = TextDim.copy(alpha = 0.15f), size = size)
            // Fill
            drawRect(
                brush = Brush.horizontalGradient(listOf(color.copy(alpha = 0.5f), color)),
                size = Size(size.width * value, size.height)
            )
        }
    }
}

@Composable
private fun ConnectedServicesSection(serviceStatus: ProfileViewModel.ServiceStatus) {
    Text(
        "CONNECTED SERVICES",
        style = MaterialTheme.typography.titleMedium.copy(
            fontFamily = Rajdhani, color = Accent, letterSpacing = 2.sp
        ),
        modifier = Modifier.padding(bottom = 12.dp)
    )

    GlassCard(glowColor = Accent, glowEnabled = true, contentPadding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ServiceStatusRow("Gemini AI", serviceStatus.geminiConnected, "필수")
            ServiceStatusRow("카카오 로컬/경로", serviceStatus.kakaoConnected, "필수")
            ServiceStatusRow("공공데이터 (날씨/미세먼지/약국/병원/주차/충전)", serviceStatus.publicDataConnected, "선택")
            ServiceStatusRow("오피넷 (주유소 가격)", serviceStatus.opinetConnected, "선택")
            ServiceStatusRow("네이버 (듀얼 경로)", serviceStatus.naverConnected, "프리미엄")
            ServiceStatusRow("ODsay (대중교통)", serviceStatus.odsayConnected, "프리미엄")

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { /* navigate to settings or onboarding */ },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentDim.copy(alpha = 0.15f),
                    contentColor = AccentDim
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("서비스 추가 / 관리", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ServiceStatusRow(name: String, isConnected: Boolean, tier: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary))
            Text(tier, style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
        }
        if (isConnected) {
            Icon(Icons.Default.CheckCircle, "연결됨", tint = Success)
        } else {
            Icon(Icons.Default.Cancel, "미연결", tint = TextDim)
        }
    }
}

@Composable
private fun FrequentRouteItem(route: ProfileViewModel.FrequentRoute) {
    GlassCard(
        glowEnabled = false,
        backgroundAlpha = 0.4f,
        contentPadding = 12.dp,
        cornerRadius = 14.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Navigation, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${route.originName} → ${route.destName}",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary)
                )
            }
            Text(
                text = "${route.count}회",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = Rajdhani,
                    color = Accent
                )
            )
        }
    }
}
