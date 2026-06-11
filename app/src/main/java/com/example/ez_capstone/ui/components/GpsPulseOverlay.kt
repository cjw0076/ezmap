package com.example.ez_capstone.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.ez_capstone.ui.theme.Accent

/**
 * 현재 위치 마커 위에 소나(SONAR) 동심원이 확산되는 GPS 펄스 오버레이.
 * ConversationScreen의 KakaoMapCompose 위에 Box 레이어로 배치.
 * 3개 링이 650ms 간격으로 순차 확산 → 페이드 아웃.
 */
@Composable
fun GpsPulseOverlay(
    modifier: Modifier = Modifier,
    color: Color = Accent
) {
    val transition = rememberInfiniteTransition(label = "gpsPulse")

    val scale0 by transition.animateFloat(
        initialValue = 1f, targetValue = 3.2f,
        animationSpec = infiniteRepeatable(
            tween(2000, delayMillis = 0, easing = FastOutSlowInEasing),
            RepeatMode.Restart
        ), label = "s0"
    )
    val scale1 by transition.animateFloat(
        initialValue = 1f, targetValue = 3.2f,
        animationSpec = infiniteRepeatable(
            tween(2000, delayMillis = 650, easing = FastOutSlowInEasing),
            RepeatMode.Restart
        ), label = "s1"
    )
    val scale2 by transition.animateFloat(
        initialValue = 1f, targetValue = 3.2f,
        animationSpec = infiniteRepeatable(
            tween(2000, delayMillis = 1300, easing = FastOutSlowInEasing),
            RepeatMode.Restart
        ), label = "s2"
    )

    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = 22.dp.toPx()

        listOf(scale0, scale1, scale2).forEach { scale ->
            val alpha = ((3.2f - scale) / 2.2f).coerceIn(0f, 0.55f)
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = baseRadius * scale,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
        // 중심 고정 점
        drawCircle(color = color.copy(alpha = 0.9f), radius = baseRadius * 0.4f, center = center)
    }
}
