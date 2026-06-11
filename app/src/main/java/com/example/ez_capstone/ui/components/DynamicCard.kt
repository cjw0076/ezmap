package com.example.ez_capstone.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ez_capstone.AgentUiState
import com.example.ez_capstone.CardType
import com.example.ez_capstone.ui.theme.Accent
import com.example.ez_capstone.ui.theme.AccentDim
import com.example.ez_capstone.ui.theme.Error
import com.example.ez_capstone.ui.theme.Success
import com.example.ez_capstone.ui.theme.Warning
import com.example.ez_capstone.ui.theme.TextPrimary
import com.example.ez_capstone.ui.theme.TextSecondary
import kotlinx.coroutines.delay

internal data class CardStyle(
    val color: Color,
    val icon: ImageVector
)

internal fun cardStyleFor(type: CardType): CardStyle = when (type) {
    CardType.ROUTE_SUMMARY -> CardStyle(Accent, Icons.Filled.Navigation)
    CardType.CONFIRMATION -> CardStyle(Success, Icons.Filled.Check)
    CardType.ALERT -> CardStyle(Error, Icons.Filled.Warning)
    CardType.INFO -> CardStyle(Accent, Icons.Filled.Info)
    CardType.THINKING -> CardStyle(AccentDim, Icons.Filled.Info)
    CardType.RECOMMENDATION -> CardStyle(Success, Icons.Filled.Star)
    CardType.SCHEDULE_POPUP -> CardStyle(Warning, Icons.Filled.CalendarMonth)
    CardType.WEATHER -> CardStyle(Accent, Icons.Filled.Cloud)
    CardType.DEPARTURE -> CardStyle(Warning, Icons.Filled.Schedule)
    CardType.PROACTIVE -> CardStyle(Accent, Icons.Filled.Notifications)
    CardType.SKILL_RESULT -> CardStyle(Success, Icons.Filled.TipsAndUpdates)
    CardType.SAFETY_ALERT -> CardStyle(Error, Icons.Filled.Shield)
}

@Composable
fun DynamicCard(
    cardState: AgentUiState.AgentCard,
    onAction: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onExpand: (() -> Unit)? = null
) {
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(cardState) {
        visible = true
        // Long.MAX_VALUE면 사용자 액션 전까지 유지 (CONFIRMATION/SAFETY_ALERT/THINKING)
        if (cardState.autoDismissMs < Long.MAX_VALUE) {
            delay(cardState.autoDismissMs)
            visible = false
            delay(300)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) +
                fadeIn(animationSpec = tween(300)) +
                scaleIn(initialScale = 0.9f, animationSpec = tween(300)),
        exit = slideOutVertically(targetOffsetY = { -it }) +
                fadeOut(animationSpec = tween(200)) +
                scaleOut(targetScale = 0.9f, animationSpec = tween(200))
    ) {
        val style = cardStyleFor(cardState.type)

        GlassCard(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = buildString {
                        append(cardState.type.name)
                        if (cardState.title.isNotBlank()) {
                            append(": ")
                            append(cardState.title)
                        }
                        if (cardState.body.isNotBlank()) {
                            append(". ")
                            append(cardState.body)
                        }
                    }
                }
                .then(
                    if (onExpand != null) Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onExpand
                    ) else Modifier
                ),
            glowColor = style.color,
            glowEnabled = true,
            contentPadding = 16.dp,
            holoEnabled = cardState.type == CardType.ROUTE_SUMMARY || cardState.type == CardType.RECOMMENDATION
        ) {
            Column {
                if (cardState.title.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = style.icon,
                            contentDescription = null,
                            tint = style.color,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = cardState.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = style.color
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = cardState.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )

                if (cardState.actions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))

                    if (cardState.type == CardType.RECOMMENDATION) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            cardState.actions.forEach { action ->
                                Button(
                                    onClick = { onAction(action) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = style.color.copy(alpha = 0.2f)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp, style.color.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Text(action, color = style.color, fontSize = 13.sp)
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            cardState.actions.forEach { action ->
                                Button(
                                    onClick = { onAction(action) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Accent.copy(alpha = 0.15f)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp, Accent.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Text(action, color = Accent)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProcessingCard(
    hint: String,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "dots")
    val scales = (0..2).map { i ->
        transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, delayMillis = i * 200),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot_$i"
        )
    }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        glowColor = Accent,
        glowEnabled = true,
        contentPadding = 16.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            // 뉴럴 펄스 Canvas (5노드 신경망 시각화)
            NeuralPulseCanvas()
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                scales.forEach { scale ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .scale(scale.value)
                            .background(Accent, CircleShape)
                    )
                }
            }
        }
    }
}

/**
 * AI 처리 중 뉴럴 네트워크 펄스 애니메이션 Canvas.
 *
 * 5개 노드(다이아몬드 레이아웃) + 6개 엣지.
 * - 노드: 스태거 scale pulse
 * - 엣지: 빛이 흐르는 animated gradient stroke
 */
@Composable
private fun NeuralPulseCanvas(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "neural")

    val pulse0 by transition.animateFloat(0.4f, 1f, infiniteRepeatable(tween(500, delayMillis = 0, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "n0")
    val pulse1 by transition.animateFloat(0.4f, 1f, infiniteRepeatable(tween(500, delayMillis = 150, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "n1")
    val pulse2 by transition.animateFloat(0.4f, 1f, infiniteRepeatable(tween(500, delayMillis = 300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "n2")
    val pulse3 by transition.animateFloat(0.4f, 1f, infiniteRepeatable(tween(500, delayMillis = 450, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "n3")
    val pulse4 by transition.animateFloat(0.4f, 1f, infiniteRepeatable(tween(500, delayMillis = 600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "n4")
    val edgePhase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "edge")

    Canvas(modifier.size(88.dp, 52.dp)) {
        val w = size.width; val h = size.height
        val nodes = listOf(
            Offset(w * 0.15f, h * 0.2f),   // 0: top-left
            Offset(w * 0.85f, h * 0.2f),   // 1: top-right
            Offset(w * 0.50f, h * 0.5f),   // 2: center
            Offset(w * 0.15f, h * 0.8f),   // 3: bottom-left
            Offset(w * 0.85f, h * 0.8f)    // 4: bottom-right
        )
        val nodePulses = listOf(pulse0, pulse1, pulse2, pulse3, pulse4)
        val edges = listOf(0 to 2, 1 to 2, 2 to 3, 2 to 4, 0 to 4, 1 to 3)

        // 엣지: 빛이 흐르는 gradient stroke
        edges.forEachIndexed { idx, (a, b) ->
            val phase = (edgePhase + idx * 0.165f) % 1f
            val t0 = (phase - 0.25f).coerceAtLeast(0f)
            val t1 = (phase + 0.25f).coerceAtMost(1f)
            val edgeStart = Offset(
                nodes[a].x + (nodes[b].x - nodes[a].x) * t0,
                nodes[a].y + (nodes[b].y - nodes[a].y) * t0
            )
            val edgeEnd = Offset(
                nodes[a].x + (nodes[b].x - nodes[a].x) * t1,
                nodes[a].y + (nodes[b].y - nodes[a].y) * t1
            )
            drawLine(
                brush = Brush.linearGradient(
                    listOf(Accent.copy(alpha = 0f), Accent.copy(alpha = 0.65f), Accent.copy(alpha = 0f)),
                    start = edgeStart,
                    end = edgeEnd
                ),
                start = nodes[a],
                end = nodes[b],
                strokeWidth = 1.dp.toPx()
            )
        }

        // 노드: pulse 원
        nodes.forEachIndexed { i, pos ->
            val p = nodePulses[i]
            drawCircle(Accent.copy(alpha = p * 0.85f), radius = 4.dp.toPx() * p, center = pos)
        }
    }
}
