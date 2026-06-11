package com.example.ez_capstone.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import kotlin.math.sin
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ez_capstone.ui.theme.*

enum class MicState { IDLE, LISTENING, PROCESSING, SPEAKING }

/**
 * OBSIDIAN v2 Mic FAB — ui_flow.md §5 MicFAB 상세 기준.
 *
 * 56dp, cornerRadius 16dp, 4상태 그래디언트:
 * - IDLE:       #6C8EFF→#8B5CF6 + 호흡 글로우 (3초 주기)
 * - LISTENING:  #F87171→#EF4444 + 펄스 링 + 스케일 1.15
 * - PROCESSING: #8B5CF6→#6C8EFF + 회전 아크
 * - SPEAKING:   액센트 + 스피커 펄스
 */
@Composable
fun MicFAB(
    state: MicState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false  // NavigationScreen에서 48dp 축소 버전
) {
    val fabSize = if (compact) EzMicSize.compactCore else EzMicSize.normalCore
    val iconSize = if (compact) EzMicSize.compactIcon else EzMicSize.normalIcon
    val outerSize = if (compact) EzMicSize.compactTouchTarget else EzMicSize.normalTouchTarget
    val cornerPx = EzSpacing.controlRadius.value

    // 8.6.1: 탭 시 일회성 pulse ring (IDLE에서 탭할 때)
    var tapPulseKey by remember { mutableStateOf(0) }
    val tapRingScale = remember { Animatable(0f) }
    LaunchedEffect(tapPulseKey) {
        if (tapPulseKey > 0) {
            tapRingScale.snapTo(0.6f)
            tapRingScale.animateTo(1.4f, animationSpec = tween(EzMotion.tapPulseMs))
        }
    }

    val transition = rememberInfiniteTransition(label = "mic")

    // 호흡 글로우 (IDLE: 3초 주기, opacity 0.2↔0.4)
    val breathGlow by transition.animateFloat(
        initialValue = 0.2f, targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(EzMotion.idleBreathMs), RepeatMode.Reverse),
        label = "breath"
    )

    // 펄스 링 (LISTENING: 확장 + 페이드)
    val ripple1 by transition.animateFloat(
        initialValue = 0.6f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(EzMotion.voiceRippleMs), RepeatMode.Restart),
        label = "ripple1"
    )
    val ripple2 by transition.animateFloat(
        initialValue = 0.6f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(EzMotion.voiceRippleMs, delayMillis = 400), RepeatMode.Restart),
        label = "ripple2"
    )

    // 회전 아크 (PROCESSING)
    val rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(EzMotion.processingSpinMs, easing = LinearEasing), RepeatMode.Restart),
        label = "rotate"
    )

    // 웨이브폼 위상 (LISTENING: sin 파형 바)
    val wavePhase by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(EzMotion.waveformMs, easing = LinearEasing), RepeatMode.Restart),
        label = "wave"
    )

    // TTS 펄스 (SPEAKING)
    val speakPulse by transition.animateFloat(
        initialValue = 1f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(EzMotion.speakingPulseMs), RepeatMode.Reverse),
        label = "speak"
    )

    val stateDescription = when (state) {
        MicState.IDLE -> "마이크 버튼. 탭하면 음성 입력 시작"
        MicState.LISTENING -> "음성 입력 중. 탭하면 중지"
        MicState.PROCESSING -> "AI가 처리 중입니다"
        MicState.SPEAKING -> "AI 응답 중. 탭하면 중지"
    }

    Box(
        modifier = modifier
            .size(outerSize)
            .semantics {
                role = Role.Button
                this.contentDescription = stateDescription
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    tapPulseKey++
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(outerSize)) {
            val center = this.center
            val coreSize = fabSize.toPx()
            val cornerRadiusPx = cornerPx * density

            // ── 탭 pulse ring (IDLE 탭 시 일회성) ──
            if (tapRingScale.value > 0.6f) {
                val tapAlpha = (1f - (tapRingScale.value - 0.6f) / 0.8f).coerceIn(0f, 0.35f)
                val tapRingSize = coreSize * tapRingScale.value
                drawRoundRect(
                    color = Accent.copy(alpha = tapAlpha),
                    topLeft = Offset(center.x - tapRingSize / 2, center.y - tapRingSize / 2),
                    size = Size(tapRingSize, tapRingSize),
                    cornerRadius = CornerRadius(cornerRadiusPx * tapRingScale.value),
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // ── 펄스 링 (LISTENING) ──
            if (state == MicState.LISTENING) {
                listOf(ripple1, ripple2).forEach { scale ->
                    val alpha = (1f - ((scale - 0.6f) / 0.6f)).coerceIn(0f, 0.35f)
                    val ringSize = coreSize * scale
                    drawRoundRect(
                        color = MicListenStart.copy(alpha = alpha),
                        topLeft = Offset(center.x - ringSize / 2, center.y - ringSize / 2),
                        size = Size(ringSize, ringSize),
                        cornerRadius = CornerRadius(cornerRadiusPx * scale),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }

                // ── 웨이브폼 바 7개 (LISTENING: FAB 하단 파형) ──
                val barCount = 7
                val barWidth = 3.dp.toPx()
                val barSpacing = 4.dp.toPx()
                val totalBarWidth = barCount * barWidth + (barCount - 1) * barSpacing
                val startX = center.x - totalBarWidth / 2f
                val baselineY = center.y + coreSize * 0.38f

                repeat(barCount) { i ->
                    val height = (sin(wavePhase + i * 0.9f) * 0.5f + 0.5f) * coreSize * 0.28f + 3.dp.toPx()
                    val x = startX + i * (barWidth + barSpacing)
                    drawRoundRect(
                        color = MicListenStart.copy(alpha = 0.75f),
                        topLeft = Offset(x, baselineY - height),
                        size = Size(barWidth, height),
                        cornerRadius = CornerRadius(1.5.dp.toPx())
                    )
                }
            }

            // ── 회전 아크 (PROCESSING) ──
            if (state == MicState.PROCESSING) {
                val arcSize = coreSize * 1.15f
                rotate(rotation, pivot = center) {
                    drawArc(
                        color = MicProcessStart.copy(alpha = 0.5f),
                        startAngle = 0f, sweepAngle = 260f,
                        useCenter = false,
                        style = Stroke(width = 2.dp.toPx()),
                        topLeft = Offset(center.x - arcSize / 2, center.y - arcSize / 2),
                        size = Size(arcSize, arcSize)
                    )
                }
            }

            // ── 글로우 ──
            val glowAlpha = when (state) {
                MicState.IDLE -> breathGlow * 0.3f
                MicState.LISTENING -> 0.25f
                MicState.PROCESSING -> 0.15f
                MicState.SPEAKING -> 0.2f * speakPulse
            }
            val glowColor = when (state) {
                MicState.IDLE -> Accent
                MicState.LISTENING -> MicListenStart
                MicState.PROCESSING -> MicProcessStart
                MicState.SPEAKING -> Accent
            }
            val glowSize = coreSize * 1.5f
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(glowColor.copy(alpha = glowAlpha), Color.Transparent),
                    center = center,
                    radius = glowSize * 0.6f
                ),
                topLeft = Offset(center.x - glowSize / 2, center.y - glowSize / 2),
                size = Size(glowSize, glowSize),
                cornerRadius = CornerRadius(cornerRadiusPx * 1.5f)
            )

            // ── 코어 (그래디언트 라운드사각) ──
            val coreScale = if (state == MicState.SPEAKING) speakPulse
                            else if (state == MicState.LISTENING) 1.15f
                            else 1f
            val scaledSize = coreSize * coreScale
            val (gradStart, gradEnd) = when (state) {
                MicState.IDLE -> MicIdleStart to MicIdleEnd
                MicState.LISTENING -> MicListenStart to MicListenEnd
                MicState.PROCESSING -> MicProcessStart to MicProcessEnd
                MicState.SPEAKING -> Accent to AccentPurple
            }
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(gradStart, gradEnd),
                    start = Offset(center.x - scaledSize / 2, center.y - scaledSize / 2),
                    end = Offset(center.x + scaledSize / 2, center.y + scaledSize / 2)
                ),
                topLeft = Offset(center.x - scaledSize / 2, center.y - scaledSize / 2),
                size = Size(scaledSize, scaledSize),
                cornerRadius = CornerRadius(cornerRadiusPx)
            )
        }

        // ── 아이콘 ──
        Icon(
            imageVector = when (state) {
                MicState.IDLE -> Icons.Filled.MicNone
                MicState.LISTENING -> Icons.Filled.Mic
                MicState.PROCESSING -> Icons.Filled.Sync
                MicState.SPEAKING -> Icons.Filled.VolumeUp
            },
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(iconSize)
        )
    }
}
