package com.example.ez_capstone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ez_capstone.ui.theme.*
import com.example.ez_capstone.viewmodel.McpViewModel

/**
 * MCP 도구 서버 설정 — 외부 MCP 서버를 등록하면 그 도구가 에이전트에 음성으로 붙는다.
 * OBSIDIAN 디자인 토큰(Color.kt) + SettingsScreen 패턴 재사용.
 */
@Composable
fun McpServersScreen(
    onBackClick: () -> Unit,
    viewModel: McpViewModel = hiltViewModel()
) {
    val state by viewModel.ui.collectAsState()

    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ── Top bar ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = TextPrimary)
            }
            Text("MCP 도구 서버", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            if (state.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Accent
                )
            } else {
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(Icons.Filled.Refresh, "새로고침", tint = Secondary)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── 소개 ──
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Hub, null, tint = Accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("외부 도구를 음성으로", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(
                        "MCP 서버를 등록하면 그 서버의 도구가 에이전트에 자동으로 붙습니다. 온디바이스로 직접 연결됩니다.",
                        fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── 서버 추가 ──
        SectionLabel("서버 추가")
        Spacer(Modifier.height(10.dp))
        Card {
            Field(value = name, onChange = { name = it; viewModel.clearMessage() }, label = "이름 (예: DeepWiki)")
            Spacer(Modifier.height(10.dp))
            Field(
                value = url, onChange = { url = it; viewModel.clearMessage() },
                label = "서버 URL (https://...)", keyboard = KeyboardType.Uri
            )
            Spacer(Modifier.height(10.dp))
            Field(value = token, onChange = { token = it }, label = "인증 토큰 (선택)")

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    name = "DeepWiki"; url = "https://mcp.deepwiki.com/mcp"; token = ""
                }) { Text("예시 채우기", color = Secondary, fontSize = 13.sp) }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { viewModel.addServer(name, url, token); name = ""; url = ""; token = "" },
                    enabled = !state.busy,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("추가", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        state.message?.let { msg ->
            Spacer(Modifier.height(10.dp))
            val color = when {
                msg.contains("실패") -> Error
                msg.contains("연결됨") || msg.contains("완료") -> Success
                else -> Warning
            }
            Text(msg, fontSize = 13.sp, color = color, modifier = Modifier.padding(horizontal = 4.dp))
        }

        Spacer(Modifier.height(24.dp))

        // ── 등록된 서버 ──
        SectionLabel("등록된 서버 (${state.servers.size})")
        Spacer(Modifier.height(10.dp))

        if (state.servers.isEmpty()) {
            Card {
                Text(
                    "등록된 서버가 없습니다. 위에서 추가하세요.",
                    fontSize = 13.sp, color = TextSecondary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        } else {
            state.servers.forEachIndexed { i, row ->
                if (i > 0) Spacer(Modifier.height(10.dp))
                ServerCard(
                    name = row.config.name,
                    url = row.config.url,
                    enabled = row.config.enabled,
                    toolCount = row.toolCount,
                    toolNames = row.toolNames,
                    onToggle = { viewModel.toggleServer(row.config.id, it) },
                    onRemove = { viewModel.removeServer(row.config.id) }
                )
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun ServerCard(
    name: String,
    url: String,
    enabled: Boolean,
    toolCount: Int,
    toolNames: List<String>,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit
) {
    val dim = if (enabled) 1f else 0.45f
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name, fontSize = 15.sp, color = TextPrimary.copy(alpha = dim),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(8.dp))
                    // 상태 배지: 꺼짐 / 도구 N개 / 연결 안 됨
                    val (badgeText, badgeColor) = when {
                        !enabled -> "꺼짐" to TextDim
                        toolCount > 0 -> "도구 ${toolCount}개" to Success
                        else -> "연결 안 됨" to Warning
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(badgeText, fontSize = 11.sp, color = badgeColor)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    url, fontSize = 11.sp, color = TextSecondary.copy(alpha = dim),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            // 활성/비활성 토글 — 끄면 이 서버 도구는 에이전트 도구 집합에서 빠짐(풀은 유지).
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = TextPrimary,
                    checkedTrackColor = Accent,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = SurfaceHigh,
                    uncheckedBorderColor = Border
                )
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.DeleteOutline, "삭제", tint = Error)
            }
        }
        if (enabled && toolNames.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                toolNames.joinToString("  ·  "),
                fontSize = 11.sp, color = TextDim, lineHeight = 16.sp
            )
        }
    }
}

// ── 디자인 토큰 재사용 헬퍼 (SettingsScreen 스타일 미러링) ──

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Secondary)
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground.copy(alpha = 0.6f))
            .border(1.dp, Secondary.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboard: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 13.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent,
            unfocusedBorderColor = Border,
            focusedLabelColor = Accent,
            unfocusedLabelColor = TextSecondary,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = Accent,
            focusedContainerColor = SurfaceHigh.copy(alpha = 0.4f),
            unfocusedContainerColor = SurfaceHigh.copy(alpha = 0.4f)
        )
    )
}
