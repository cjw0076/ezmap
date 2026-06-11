package com.example.ez_capstone.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ez_capstone.ui.theme.Accent
import com.example.ez_capstone.ui.theme.AccentDim
import com.example.ez_capstone.ui.theme.Background
import com.example.ez_capstone.ui.theme.Border
import com.example.ez_capstone.ui.theme.Orbitron
import com.example.ez_capstone.ui.theme.Purple
import com.example.ez_capstone.ui.theme.Rajdhani
import com.example.ez_capstone.ui.theme.TextDim
import com.example.ez_capstone.ui.theme.TextPrimary
import com.example.ez_capstone.ui.theme.TextSecondary
import java.util.Calendar
import kotlin.random.Random

/**
 * LandingScreen — AI 비서 시스템 진입 UI.
 *
 * 기존 마케팅 페이지에서 "AI 비서 활성화" 경험으로 전환.
 * 레이더 스캔 링 + 시스템 상태 + 시간 기반 인사말.
 */
@Composable
fun LandingScreen(
    onStartClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "landing")

    // 배경 파티클 드리프트
    val particles = remember {
        List(20) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 1.5f + 0.5f) }
    }
    val drift by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift"
    )

    // 3개 레이더 링 (위상 차이로 순차 방출)
    val ring1 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "ring1"
    )
    val ring2 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, 800, easing = LinearEasing), RepeatMode.Restart),
        label = "ring2"
    )
    val ring3 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, 1600, easing = LinearEasing), RepeatMode.Restart),
        label = "ring3"
    )

    // 내부 링 맥박
    val innerPulse by transition.animateFloat(
        initialValue = 0.85f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "innerPulse"
    )

    // 시간 기반 인사말
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 6  -> "야심한 밤이네요"
            hour < 12 -> "좋은 아침이에요"
            hour < 18 -> "좋은 오후에요"
            else      -> "좋은 저녁이에요"
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Background)) {

        // ── 배경 파티클 ──
        Canvas(modifier = Modifier.fillMaxSize()) {
            particles.forEach { (xR, yR, spd) ->
                val y = ((yR + drift * spd) % 1f) * size.height
                val x = xR * size.width
                val alpha = (1f - y / size.height) * 0.18f
                drawCircle(
                    color = Accent.copy(alpha = alpha.coerceIn(0f, 0.18f)),
                    radius = if (xR.hashCode() % 3 == 0) 2.2f else 1.0f,
                    center = Offset(x, y)
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.12f))

            // ── 레이더 스캔 링 + "EZ" 텍스트 ──
            Box(
                modifier = Modifier.size(240.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val maxR = size.minDimension / 2

                    // 방사형 그라디언트 배경 글로우
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Accent.copy(alpha = 0.08f), Color.Transparent),
                            center = center, radius = maxR
                        ),
                        radius = maxR
                    )

                    // 정적 내부 링
                    drawCircle(
                        color = Accent.copy(alpha = 0.25f * innerPulse),
                        radius = maxR * 0.30f,
                        style = Stroke(width = 1.5f)
                    )
                    drawCircle(
                        color = Accent.copy(alpha = 0.12f),
                        radius = maxR * 0.55f,
                        style = Stroke(width = 1f)
                    )

                    // 동적 확장 링
                    listOf(ring1, ring2, ring3).forEach { progress ->
                        val r = maxR * (0.28f + progress * 0.72f)
                        val alpha = (1f - progress) * 0.55f
                        drawCircle(
                            color = Accent.copy(alpha = alpha),
                            radius = r,
                            style = Stroke(width = 2.5f)
                        )
                    }
                }

                // "EZ" 텍스트
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "EZ",
                        fontSize = 72.sp,
                        fontFamily = Orbitron,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 0.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── 서브타이틀 ──
            Text(
                text = "AI MOBILITY AGENT",
                fontFamily = Rajdhani,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = TextDim,
                letterSpacing = 4.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── 시스템 상태 표시 ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Accent.copy(alpha = 0.05f))
                    .border(1.dp, Accent.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SystemStatus("GEMINI", Accent)
                Box(modifier = Modifier.size(width = 1.dp, height = 20.dp).background(Border))
                SystemStatus("카카오 맵", Accent)
                Box(modifier = Modifier.size(width = 1.dp, height = 20.dp).background(Border))
                SystemStatus("음성 인식", Accent)
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── 인사말 ──
            Text(
                text = greeting,
                fontSize = 15.sp,
                fontFamily = Rajdhani,
                color = TextSecondary,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "이지(EZ)가 준비됐어요",
                fontSize = 12.sp,
                color = TextDim,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── CTA — 글로우 탭 버튼 ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .background(
                        brush = Brush.linearGradient(listOf(Accent, Purple.copy(alpha = 0.8f)))
                    )
                    .clickable { onStartClick() },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.9f))
                    )
                    Text(
                        text = "시스템 접속",
                        fontSize = 15.sp,
                        fontFamily = Orbitron,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun SystemStatus(label: String, dotColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = label,
            fontFamily = Rajdhani,
            fontSize = 12.sp,
            color = TextSecondary,
            letterSpacing = 0.5.sp
        )
    }
}
