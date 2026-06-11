package com.example.ez_capstone.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ez_capstone.skill.SkillHealth
import com.example.ez_capstone.skill.SkillStatus
import com.example.ez_capstone.ui.theme.*
import com.example.ez_capstone.viewmodel.SkillStoreViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillStoreScreen(
    viewModel: SkillStoreViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val allSkills by viewModel.skills.collectAsState()
    val filtered = viewModel.filteredSkills(allSkills)

    var dialogSkill by remember { mutableStateOf<SkillHealth?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // ── Top Bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBack,
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.semantics { contentDescription = "뒤로 가기" }
            ) {
                Text("←", color = TextSecondary, fontSize = 20.sp)
            }
            Text(
                "SKILL STORE",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Accent,
                fontFamily = Rajdhani,
                letterSpacing = 3.sp,
                modifier = Modifier.weight(1f)
            )
            val activeCount = allSkills.count { it.status == SkillStatus.ACTIVE }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Success.copy(alpha = 0.15f),
                modifier = Modifier.padding(end = 16.dp)
            ) {
                Text(
                    "$activeCount / ${allSkills.size}",
                    color = Success,
                    fontSize = 12.sp,
                    fontFamily = Rajdhani,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        // ── Category Tabs ──
        ScrollableTabRow(
            selectedTabIndex = selectedCategory.ordinal,
            containerColor = Surface,
            contentColor = Accent,
            edgePadding = 12.dp,
            divider = {}
        ) {
            SkillStoreViewModel.Category.entries.forEachIndexed { _, cat ->
                Tab(
                    selected = selectedCategory == cat,
                    onClick = { viewModel.selectCategory(cat) },
                    selectedContentColor = Accent,
                    unselectedContentColor = TextSecondary
                ) {
                    Text(
                        cat.label,
                        fontFamily = Rajdhani,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
                    )
                }
            }
        }

        // ── Skill List ──
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (filtered.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "⚡",
                            fontSize = 40.sp
                        )
                        Text(
                            text = "이 카테고리에 스킬이 없습니다",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            fontFamily = Rajdhani,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "다른 카테고리를 탐색해보세요",
                            color = TextSecondary.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                items(filtered, key = { it.skillName }) { skill ->
                    SkillCard(
                        skill = skill,
                        displayName = viewModel.displayName(skill.skillName),
                        description = viewModel.description(skill.skillName),
                        onTap = {
                            if (skill.status == SkillStatus.KEY_MISSING) {
                                dialogSkill = skill
                            }
                        },
                        onReset = { viewModel.resetSkill(skill.skillName) }
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // ── KEY_MISSING Dialog ──
    dialogSkill?.let { skill ->
        AlertDialog(
            onDismissRequest = { dialogSkill = null },
            containerColor = SurfaceHigh,
            title = {
                Text(
                    skill.skillName.replace("_", " ").uppercase(),
                    color = AccentPurple,
                    fontFamily = Rajdhani,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 1.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "이 스킬을 활성화하려면 API 키가 필요합니다.",
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AccentPurple.copy(alpha = 0.12f)
                    ) {
                        Text(
                            "필요한 키: ${keyRequiredForSkill(skill.skillName)}",
                            color = AccentPurple,
                            fontSize = 13.sp,
                            fontFamily = Rajdhani,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    dialogSkill = null
                    onNavigateSettings()
                }) {
                    Text("설정으로 이동", color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogSkill = null }) {
                    Text("닫기", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun SkillCard(
    skill: SkillHealth,
    displayName: String,
    description: String?,
    onTap: () -> Unit,
    onReset: () -> Unit
) {
    val statusColor = skillStatusColor(skill.status)
    val isClickable = skill.status == SkillStatus.KEY_MISSING

    val cardDesc = "$displayName, 상태: ${skill.status.name}" +
        if (skill.status == SkillStatus.KEY_MISSING) ", 탭하여 API 키 등록" else ""
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isClickable) Modifier.clickable(onClick = onTap) else Modifier)
            .semantics { contentDescription = cardDesc },
        shape = RoundedCornerShape(12.dp),
        color = SurfaceHigh,
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // ── Header row ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Status dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusColor)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        displayName,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = Rajdhani,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (description != null) {
                        Text(
                            description,
                            color = TextSecondary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                // Status badge (색상 + 형태 심볼 — 색각 이상 대응)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        skillStatusLabel(skill.status),
                        color = statusColor,
                        fontSize = 10.sp,
                        fontFamily = Rajdhani,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // ── Stats row ──
            if (skill.totalCalls > 0 || skill.consecutiveFailures > 0) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (skill.totalCalls > 0) {
                        MiniStat("호출", "${skill.totalCalls}회")
                        MiniStat("성공률", "${"%.0f".format(skill.successRate * 100)}%")
                        MiniStat("지연", "${skill.avgLatencyMs}ms")
                    }
                    if (skill.consecutiveFailures > 0) {
                        MiniStat("연속실패", "${skill.consecutiveFailures}회", Warning)
                    }
                }
            }

            // ── Progress bar ──
            if (skill.totalCalls > 0) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { skill.successRate },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = statusColor,
                    trackColor = statusColor.copy(alpha = 0.12f)
                )
            }

            // ── Last error ──
            skill.lastError?.takeIf { it.isNotBlank() }?.let { err ->
                Spacer(Modifier.height(6.dp))
                Text(
                    err,
                    color = Error.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // ── Reset for AUTO_DISABLED ──
            if (skill.status == SkillStatus.AUTO_DISABLED) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onReset,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
                ) {
                    Text("↺ 상태 초기화", color = Warning, fontSize = 12.sp, fontFamily = Rajdhani)
                }
            }

            // ── KEY_MISSING hint ──
            if (skill.status == SkillStatus.KEY_MISSING) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "탭하여 API 키 등록 →",
                    color = AccentPurple.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontFamily = Rajdhani
                )
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, valueColor: Color = TextSecondary) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = TextDim, fontSize = 11.sp, fontFamily = Rajdhani)
        Text(value, color = valueColor, fontSize = 11.sp, fontFamily = Rajdhani, fontWeight = FontWeight.Bold)
    }
}

/** 색상+형태 조합 레이블 — 색각 이상 사용자도 심볼로 구분 가능 */
private fun skillStatusLabel(status: SkillStatus): String = when (status) {
    SkillStatus.ACTIVE        -> "● ACTIVE"
    SkillStatus.DEGRADED      -> "◐ DEGRADED"
    SkillStatus.FAILING       -> "✕ FAILING"
    SkillStatus.AUTO_DISABLED -> "⊘ DISABLED"
    SkillStatus.KEY_MISSING   -> "⚿ KEY MISSING"
}

private fun skillStatusColor(status: SkillStatus): Color = when (status) {
    SkillStatus.ACTIVE        -> Success
    SkillStatus.DEGRADED      -> Warning
    SkillStatus.FAILING       -> Error
    SkillStatus.AUTO_DISABLED -> TextDim
    SkillStatus.KEY_MISSING   -> AccentPurple
}

private fun keyRequiredForSkill(skillName: String): String = when {
    skillName in setOf(
        "get_directions_naver", "get_traffic_speed", "get_traffic_incidents",
        "get_highway_alerts", "get_road_risk"
    ) -> "네이버 API 키"
    skillName == "get_transit_route" -> "ODsay API 키"
    skillName == "get_gas_stations" -> "오피넷 API 키"
    skillName in setOf(
        "get_ev_chargers", "get_parking", "get_realtime_parking",
        "search_pharmacies", "search_hospitals", "get_weather_kma", "get_air_quality"
    ) -> "공공데이터 API 키"
    else -> "Kakao REST API 키"
}
