package com.example.ez_capstone.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ez_capstone.ui.components.GlassCard
import com.example.ez_capstone.ui.theme.Accent
import com.example.ez_capstone.ui.theme.Background
import com.example.ez_capstone.ui.theme.Error
import com.example.ez_capstone.ui.theme.TextPrimary
import com.example.ez_capstone.ui.theme.TextSecondary
import com.example.ez_capstone.ui.theme.Warning
import com.example.ez_capstone.eval.FailureFinding
import com.example.ez_capstone.viewmodel.AgentMemoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AgentMemoryScreen(
    onBackClick: () -> Unit,
    viewModel: AgentMemoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Background)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = TextPrimary)
            }
            Text(
                "AI가 아는 나의 정보",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {

            item {
                MemorySection(icon = Icons.Filled.Person, title = "프로필") {
                    val p = state.profile
                    if (p == null) {
                        MemoryRow("프로필 없음", "")
                    } else {
                        MemoryRow("집", p.homeAddress ?: "미설정")
                        MemoryRow("직장", p.workAddress ?: "미설정")
                        MemoryRow("차량", "${p.vehicleType} / ${p.fuelType}")
                        MemoryRow("음성 스타일", p.ttsStyle)
                        if (p.fuelType == "electric" && p.batteryCapacityKwh > 0) {
                            MemoryRow("배터리", "${p.batteryCapacityKwh}kWh (현재 ${p.currentBatteryPct}%)")
                        }
                    }
                }
            }

            item {
                MemorySection(icon = Icons.Filled.Place, title = "자주 가는 곳 TOP 5") {
                    if (state.frequentPlaces.isEmpty()) {
                        MemoryRow("데이터 없음", "")
                    } else {
                        state.frequentPlaces.forEach { place ->
                            MemoryRow(place.name, "${place.visitCount}회 방문 · ${place.address ?: ""}")
                        }
                    }
                }
            }

            item {
                MemorySection(icon = Icons.Filled.History, title = "최근 경로 5개") {
                    if (state.recentRoutes.isEmpty()) {
                        MemoryRow("경로 기록 없음", "")
                    } else {
                        state.recentRoutes.forEach { route ->
                            val date = SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(Date(route.departedAt))
                            val origin = route.originName ?: "출발지"
                            val dest = route.destName ?: "도착지"
                            val dist = if (route.distanceM >= 1000) "${"%.1f".format(route.distanceM / 1000.0)}km" else "${route.distanceM}m"
                            MemoryRow("$origin → $dest", "$date · $dist")
                        }
                    }
                }
            }

            item {
                MemorySection(icon = Icons.AutoMirrored.Filled.Message, title = "최근 대화 10개") {
                    val userMessages = state.recentMessages.filter { it.role == "user" }
                    if (userMessages.isEmpty()) {
                        MemoryRow("대화 기록 없음", "")
                    } else {
                        userMessages.forEach { msg ->
                            val date = SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(Date(msg.createdAt))
                            MemoryRow(msg.text.take(40) + if (msg.text.length > 40) "…" else "", date)
                        }
                    }
                }
            }

            item {
                MemorySection(icon = Icons.Filled.Tune, title = "경로 선호도") {
                    if (state.preferences.isEmpty()) {
                        MemoryRow("선호도 데이터 없음", "")
                    } else {
                        state.preferences.forEach { pref ->
                            val label = when ("${pref.category}/${pref.key}") {
                                "navi_option/avoid_narrow" -> "좁은 도로 회피"
                                "navi_option/prefer_wide" -> "넓은 도로 선호"
                                "navi_option/minimize_left_turn" -> "좌회전 최소화"
                                else -> "${pref.category}: ${pref.key}"
                            }
                            MemoryRow(label, if (pref.value > 0.5f) "켜짐" else "꺼짐")
                        }
                    }
                }
            }

            item {
                MemorySection(icon = Icons.Filled.DirectionsCar, title = "최근 운전 기록") {
                    if (state.recentScores.isEmpty()) {
                        MemoryRow("운전 기록 없음", "")
                    } else {
                        state.recentScores.forEach { score ->
                            val date = SimpleDateFormat("MM/dd", Locale.KOREA).format(Date(score.startedAt))
                            val stars = when {
                                score.finalScore >= 90 -> "★★★"
                                score.finalScore >= 70 -> "★★☆"
                                else -> "★☆☆"
                            }
                            MemoryRow(
                                "$date 주행 — ${score.finalScore}점 $stars",
                                "${score.totalDistanceM / 1000}km · 속도위반 ${score.speedingCount}회"
                            )
                        }
                    }
                }
            }

            // ── 학습한 명령 (SELF_LEARNING_AGENT.md §7.1 Phase C-1) ──
            item {
                MemorySection(icon = Icons.Filled.AutoAwesome, title = "학습한 명령 (${state.learnedSkills.size}개)") {
                    if (state.learnedSkills.isEmpty()) {
                        MemoryRow("아직 학습된 명령이 없어요", "")
                        MemoryRow("예: \"근처 카페\", \"회사 가자\" 등으로 말씀해보세요", "")
                    } else {
                        state.learnedSkills.forEachIndexed { idx, skill ->
                            if (idx > 0) HorizontalDivider(
                                color = TextSecondary.copy(alpha = 0.1f),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                            LearnedSkillRow(
                                skill = skill,
                                onDelete = { viewModel.deleteLearnedSkill(skill.id) },
                                onTogglePin = { viewModel.toggleLearnedSkillPin(skill.id) }
                            )
                        }
                    }
                }
            }

            // ── 자가진단 (Phase 6 엔지니어 에이전트 — 읽기 전용, 자동수정 없음) ──
            item {
                MemorySection(icon = Icons.Filled.MonitorHeart, title = "자가진단 (${state.selfDiagnostics.size}건)") {
                    if (state.selfDiagnostics.isEmpty()) {
                        MemoryRow("반복 실패 패턴이 없어요", "")
                        MemoryRow("에이전트가 안정적으로 동작 중입니다", "")
                    } else {
                        state.selfDiagnostics.forEachIndexed { idx, finding ->
                            if (idx > 0) HorizontalDivider(
                                color = TextSecondary.copy(alpha = 0.1f),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                            DiagnosticRow(finding)
                        }
                    }
                }
            }

            item {
                MemorySection(icon = Icons.Filled.Star, title = "피드백 기록") {
                    if (state.recentFeedback.isEmpty()) {
                        MemoryRow("피드백 없음", "")
                    } else {
                        state.recentFeedback.forEach { fb ->
                            val date = SimpleDateFormat("MM/dd", Locale.KOREA).format(Date(fb.createdAt))
                            val stars = "★".repeat(fb.rating) + "☆".repeat(5 - fb.rating)
                            MemoryRow(stars, "$date · ${fb.comment ?: "코멘트 없음"}")
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun MemorySection(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(title, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
    GlassCard(
        glowEnabled = false,
        borderAlpha = 0.12f,
        contentPadding = 12.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

@Composable
private fun MemoryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (value.isNotBlank()) {
            Text(value, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/**
 * 자가진단 1건 — 반복 실패 패턴 + 원인 triage 태그. 읽기 전용(자동수정 버튼 없음, R3 설명가능성).
 * 원인별 색: 조치 필요(인증/구독·회로차단)=Error, 주의(네트워크·한도)=Warning, 정보성=TextSecondary.
 */
@Composable
private fun DiagnosticRow(finding: FailureFinding) {
    val causeColor = when (finding.cause) {
        "auth_or_subscription", "circuit_breaker" -> Error
        "network", "rate_limit" -> Warning
        else -> TextSecondary
    }
    val causeLabel = when (finding.cause) {
        "auth_or_subscription" -> "키/구독"
        "circuit_breaker" -> "회로차단"
        "network" -> "네트워크"
        "rate_limit" -> "한도초과"
        "validation" -> "인자누락"
        "empty_result" -> "결과없음"
        null -> finding.kind
        else -> finding.cause
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${finding.subject} ×${finding.count}",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(causeLabel, color = causeColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            finding.suggestion,
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
        )
    }
}

/**
 * 학습된 Skill 1개의 행 — 결정 3: 편집 금지, 삭제 + 고정(Pin) 2개 액션만.
 * 표시 정보: 대표 발화, 사용 횟수, 성공률, 평균 응답 시간, 상태 배지, Pin 상태.
 */
@Composable
private fun LearnedSkillRow(
    skill: com.example.ez_capstone.skill.LearnedSkillEntity,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit
) {
    val successRate = if (skill.usageCount > 0) {
        "%.0f".format(skill.successCount * 100f / skill.usageCount) + "%"
    } else "-"
    val statusBadge = when (skill.status) {
        "DEGRADED" -> "⚠ 저하"
        "FAILING" -> "❗ 실패 중"
        "AUTO_DISABLED" -> "● 비활성"
        else -> null
    }
    val pinned = skill.pinnedUntil != null && skill.pinnedUntil > System.currentTimeMillis()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "\"${skill.canonicalUtterance}\"",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onTogglePin, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (pinned) "고정 해제" else "고정",
                    tint = if (pinned) Accent else TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "삭제",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text(
                "${skill.usageCount}회 사용 · 성공 $successRate · ${skill.avgLatencyMs}ms",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f)
            )
            if (statusBadge != null) {
                Text(statusBadge, color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}
