package com.example.ez_capstone.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.example.ez_capstone.ui.theme.Accent
import kotlinx.coroutines.delay
import kotlin.math.sqrt
import kotlin.random.Random

private data class RouteParticle(
    val progress: Float,
    val speed: Float,
    val alpha: Float,
    val size: Float
)

/**
 * 경로 위에 파티클이 목적지 방향으로 흐르는 Canvas 오버레이.
 *
 * @param routePoints 화면 좌표계(px)로 변환된 경로 점 목록.
 *                    점이 2개 미만이면 렌더링하지 않는다.
 * @param particleCount 동시 표시 파티클 수 (기본 18).
 */
@Composable
fun RouteParticleOverlay(
    routePoints: List<Offset>,
    modifier: Modifier = Modifier,
    particleCount: Int = 18
) {
    if (routePoints.size < 2) return

    var particles by remember(routePoints) {
        mutableStateOf(
            List(particleCount) { i ->
                RouteParticle(
                    progress = i.toFloat() / particleCount,
                    speed = 0.003f + Random.nextFloat() * 0.003f,
                    alpha = 0.4f + Random.nextFloat() * 0.5f,
                    size = 2f + Random.nextFloat() * 3f
                )
            }
        )
    }

    LaunchedEffect(routePoints) {
        while (true) {
            delay(16L) // ~60fps
            particles = particles.map { p ->
                p.copy(progress = (p.progress + p.speed) % 1f)
            }
        }
    }

    Canvas(modifier) {
        particles.forEach { p ->
            val pos = interpolateOnPath(routePoints, p.progress)
            drawCircle(
                color = Accent.copy(alpha = p.alpha),
                radius = p.size.dp.toPx(),
                center = pos
            )
        }
    }
}

/**
 * 경로 위의 progress(0~1) 위치에 해당하는 Offset을 선형 보간으로 반환.
 */
private fun interpolateOnPath(points: List<Offset>, progress: Float): Offset {
    if (points.size < 2) return points.firstOrNull() ?: Offset.Zero

    val lengths = FloatArray(points.size - 1) { i ->
        val dx = points[i + 1].x - points[i].x
        val dy = points[i + 1].y - points[i].y
        sqrt(dx * dx + dy * dy)
    }
    val totalLength = lengths.sum()
    if (totalLength == 0f) return points.first()

    val target = progress * totalLength
    var accumulated = 0f

    lengths.forEachIndexed { i, segLen ->
        if (accumulated + segLen >= target) {
            val t = if (segLen == 0f) 0f else (target - accumulated) / segLen
            return Offset(
                points[i].x + (points[i + 1].x - points[i].x) * t,
                points[i].y + (points[i + 1].y - points[i].y) * t
            )
        }
        accumulated += segLen
    }
    return points.last()
}
