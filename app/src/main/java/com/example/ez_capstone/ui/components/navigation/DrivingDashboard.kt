package com.example.ez_capstone.ui.components.navigation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ez_capstone.ui.components.MicFAB
import com.example.ez_capstone.ui.components.MicState
import com.example.ez_capstone.ui.theme.*

@Composable
fun BottomNavBar(
    speed: Int,
    remainDistance: Int,
    remainTime: Int,
    speedLimit: Int? = null,
    voiceState: MicState,
    onVoiceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedSpeed by animateIntAsState(targetValue = speed, animationSpec = tween(400), label = "speedAnim")
    val isOverspeed = speedLimit != null && speed > speedLimit

    val infiniteTransition = rememberInfiniteTransition(label = "overspeed")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.26f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "pulseAlpha"
    )
    val barBg = if (isOverspeed) Error.copy(alpha = pulseAlpha) else Surface.copy(alpha = 0.92f)

    val speedColor = when {
        speedLimit == null     -> HudSpeed
        speed <= speedLimit    -> TextPrimary
        speed <= speedLimit + 10 -> Warning
        else                   -> Error
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(barBg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1.2f)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceCard)
                .border(1.dp, Border, RoundedCornerShape(12.dp))
                .padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (speedLimit != null) {
                    SpeedLimitBadge(limit = speedLimit, isOverspeed = isOverspeed)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = animatedSpeed.toString(),
                        fontFamily = Orbitron,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        color = speedColor
                    )
                    Text(text = "km/h", fontFamily = Rajdhani, fontSize = 10.sp, color = TextDim)
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(2.0f)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceCard)
                .border(1.dp, Border, RoundedCornerShape(12.dp))
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = formatEta(remainTime),
                fontFamily = Orbitron,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = HudEta
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontFamily = Orbitron, fontWeight = FontWeight.Bold,
                        fontSize = 14.sp, color = HudDistance)) {
                        append(formatDistance(remainDistance))
                    }
                    withStyle(SpanStyle(fontFamily = Rajdhani, fontSize = 10.sp, color = TextDim)) {
                        append(if (remainDistance >= 1000) " km" else " m")
                    }
                }
            )
        }

        // 마이크 슬롯 — 고정 64dp로 웨이크링이 옆 ETA 카드를 침범하지 않게 가둔다.
        Box(
            modifier = Modifier.size(64.dp),
            contentAlignment = Alignment.Center
        ) {
            if (voiceState == MicState.LISTENING) {
                val wakeTransition = rememberInfiniteTransition(label = "wakeRing")
                val ringScale by wakeTransition.animateFloat(
                    initialValue = 1f, targetValue = 1.2f,
                    animationSpec = infiniteRepeatable(tween(800), RepeatMode.Restart),
                    label = "ringScale"
                )
                val ringAlpha by wakeTransition.animateFloat(
                    initialValue = 0.5f, targetValue = 0f,
                    animationSpec = infiniteRepeatable(tween(800), RepeatMode.Restart),
                    label = "ringAlpha"
                )
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .scale(ringScale)
                        .clip(CircleShape)
                        .background(Accent.copy(alpha = ringAlpha))
                )
            }
            MicFAB(
                state = voiceState,
                onClick = onVoiceClick,
                modifier = Modifier.size(52.dp)
            )
        }
    }
}

@Composable
fun SpeedLimitBadge(limit: Int, isOverspeed: Boolean, modifier: Modifier = Modifier) {
    val borderColor = if (isOverspeed) Error else Error.copy(alpha = 0.7f)
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(if (isOverspeed) 3.dp else 2.5.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = limit.toString(),
            fontFamily = Orbitron,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (limit >= 100) 9.sp else 11.sp,
            color = if (isOverspeed) Error else TextPrimary
        )
    }
}

private fun formatEta(seconds: Int): String {
    val m = seconds / 60
    return if (m < 60) "${m}분" else "${m / 60}h${m % 60}m"
}

private fun formatDistance(meters: Int): String =
    if (meters >= 1000) "%.1f".format(meters / 1000.0) else "$meters"
