package com.example.ez_capstone.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ez_capstone.R
import com.example.ez_capstone.models.ChatUiMessage
import com.example.ez_capstone.ui.theme.Accent
import com.example.ez_capstone.ui.theme.AccentDim
import com.example.ez_capstone.ui.theme.AccentPurple
import com.example.ez_capstone.ui.theme.Background
import com.example.ez_capstone.ui.theme.Rajdhani
import com.example.ez_capstone.ui.theme.Surface
import com.example.ez_capstone.ui.theme.SurfaceHigh
import com.example.ez_capstone.ui.theme.TextDim
import com.example.ez_capstone.ui.theme.TextPrimary
import com.example.ez_capstone.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class PromptSuggestion(val icon: ImageVector, val label: String, val text: String)

private val examplePrompts = listOf(
    PromptSuggestion(Icons.Filled.DirectionsCar, "경로 안내", "강남역까지 가는 길 알려줘"),
    PromptSuggestion(Icons.Filled.WbSunny, "날씨 확인", "지금 여기 날씨 어때?"),
    PromptSuggestion(Icons.Filled.LocalGasStation, "주유소 찾기", "근처 제일 싼 주유소 찾아줘"),
    PromptSuggestion(Icons.Filled.Restaurant, "맛집 추천", "근처 점심 먹을 곳 추천해줘"),
    PromptSuggestion(Icons.Filled.MedicalServices, "약국·병원", "가까운 약국 어디야?"),
    PromptSuggestion(Icons.Filled.Home, "집까지 ETA", "집까지 몇 분 걸려?")
)

@Composable
fun ConversationBottomSheetContent(
    messages: List<ChatUiMessage>,
    inputEnabled: Boolean,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = modifier.background(Surface)) {
        // 핸들 바
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Spacer(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(TextSecondary, RoundedCornerShape(2.dp))
            )
        }

        MessageInputRow(
            inputText = inputText,
            onInputTextChange = { inputText = it },
            inputEnabled = inputEnabled,
            onSendMessage = {
                if (inputText.isNotBlank()) {
                    onSendMessage(inputText)
                    inputText = ""
                }
            }
        )

        // 채팅 메시지 목록
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 8.1: 예시 프롬프트 카드 (messages 비어있을 때)
            if (messages.isEmpty()) {
                item {
                    ExamplePromptsSection(onSendMessage = onSendMessage)
                }
            } else {
                items(messages, key = { "${it.timestamp}_${it.role}" }) { message ->
                    ChatBubble(message)
                }
            }
        }
    }
}

@Composable
private fun MessageInputRow(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    inputEnabled: Boolean,
    onSendMessage: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = inputText,
            onValueChange = onInputTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("메시지를 입력하세요", color = TextSecondary) },
            enabled = inputEnabled,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SurfaceHigh,
                unfocusedContainerColor = SurfaceHigh,
                disabledContainerColor = SurfaceHigh.copy(alpha = 0.5f),
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = Accent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(24.dp),
            singleLine = true
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = onSendMessage,
            enabled = inputEnabled && inputText.isNotBlank()
        ) {
            Icon(
                painter = painterResource(id = android.R.drawable.ic_menu_send),
                contentDescription = "전송",
                tint = if (inputEnabled && inputText.isNotBlank()) Accent else TextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ExamplePromptsSection(onSendMessage: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "무엇을 도와드릴까요?",
            fontSize = 13.sp,
            fontFamily = Rajdhani,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        // 2-column grid (3 rows × 2 cols)
        examplePrompts.chunked(2).forEach { rowPrompts ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowPrompts.forEach { prompt ->
                    PromptCard(
                        prompt = prompt,
                        onClick = { onSendMessage(prompt.text) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Fill empty slot if odd number
                if (rowPrompts.size < 2) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PromptCard(
    prompt: PromptSuggestion,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(80),
        label = "prompt_scale"
    )
    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceHigh)
            .border(1.dp, Accent.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                imageVector = prompt.icon,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = prompt.label,
                fontSize = 11.sp,
                fontFamily = Rajdhani,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = prompt.text,
                fontSize = 10.sp,
                color = TextDim,
                lineHeight = 13.sp
            )
        }
    }
}

@Composable
private fun ChatBubble(message: ChatUiMessage) {
    val isUser = message.role == "user"
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeText = remember(message.timestamp) { timeFormat.format(Date(message.timestamp)) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            // 사용자 버블: 시안-퍼플 보더, 투명 배경
            Text(
                text = message.content,
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(Accent.copy(alpha = 0.3f), AccentDim.copy(alpha = 0.2f))
                        ),
                        shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
                    )
                    .background(
                        Brush.horizontalGradient(
                            listOf(Accent.copy(alpha = 0.08f), AccentDim.copy(alpha = 0.06f))
                        ),
                        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )
        } else {
            // AI 버블: 글래스 배경 + 시안-퍼플 좌측 글로우 라인
            Row(
                modifier = Modifier
                    .background(SurfaceHigh.copy(alpha = 0.6f), RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                    .drawBehind {
                        drawLine(
                            brush = Brush.verticalGradient(
                                listOf(Accent, AccentDim)
                            ),
                            start = Offset(0f, 6.dp.toPx()),
                            end = Offset(0f, size.height - 6.dp.toPx()),
                            strokeWidth = 3.dp.toPx()
                        )
                    }
                    .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Small agent orb
                Canvas(modifier = Modifier.size(8.dp).padding(top = 2.dp)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Accent, AccentDim),
                            radius = size.minDimension / 2
                        ),
                        radius = size.minDimension / 2
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = message.content,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    // 설명가능성: 의사결정 근거를 "왜?"로 펼쳐 본다.
                    if (!message.explanation.isNullOrBlank()) {
                        var showWhy by remember { mutableStateOf(false) }
                        Text(
                            text = if (showWhy) "▾ 왜?" else "▸ 왜?",
                            style = MaterialTheme.typography.labelSmall,
                            color = Accent,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable { showWhy = !showWhy }
                        )
                        if (showWhy) {
                            Text(
                                text = message.explanation,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextDim,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
        // 타임스탬프 (Rajdhani via labelSmall)
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelSmall,
            color = TextDim,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}
