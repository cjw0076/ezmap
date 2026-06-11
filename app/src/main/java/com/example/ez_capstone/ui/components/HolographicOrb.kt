package com.example.ez_capstone.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ez_capstone.ui.theme.Accent
import com.example.ez_capstone.ui.theme.AccentDim

@Composable
fun HolographicOrb(
    modifier: Modifier = Modifier,
    size: Dp = 200.dp
) {
    val transition = rememberInfiniteTransition(label = "orb")

    val scale by transition.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse),
        label = "orbScale"
    )

    val glowAlpha by transition.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Reverse),
        label = "orbGlow"
    )

    val ring1Rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
        label = "ring1"
    )
    val ring2Rotation by transition.animateFloat(
        initialValue = 0f, targetValue = -360f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Restart),
        label = "ring2"
    )
    val ring3Rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart),
        label = "ring3"
    )

    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2, this.size.height / 2)
        val baseRadius = this.size.minDimension / 2

        // Outer glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Accent.copy(alpha = glowAlpha * 0.3f),
                    AccentDim.copy(alpha = glowAlpha * 0.15f),
                    Color.Transparent
                ),
                center = center,
                radius = baseRadius * scale * 1.5f
            ),
            radius = baseRadius * scale * 1.5f,
            center = center
        )

        // Holographic ring 1 (outer, cyan, slow)
        rotate(ring1Rotation, pivot = center) {
            drawCircle(
                color = Accent.copy(alpha = glowAlpha * 0.2f),
                radius = baseRadius * 0.95f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // Holographic ring 2 (middle, purple, reverse)
        rotate(ring2Rotation, pivot = center) {
            drawCircle(
                color = AccentDim.copy(alpha = glowAlpha * 0.25f),
                radius = baseRadius * 0.78f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // Holographic ring 3 (inner, cyan, fastest)
        rotate(ring3Rotation, pivot = center) {
            drawCircle(
                color = Accent.copy(alpha = glowAlpha * 0.3f),
                radius = baseRadius * 0.6f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // Core orb — radial gradient cyan→purple→dark
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Accent.copy(alpha = 0.9f),
                    AccentDim.copy(alpha = 0.6f),
                    Color(0xFF1A0533).copy(alpha = 0.8f)
                ),
                center = center,
                radius = baseRadius * scale * 0.4f
            ),
            radius = baseRadius * scale * 0.4f,
            center = center
        )

        // Inner bright spot (highlight)
        drawCircle(
            color = Color.White.copy(alpha = glowAlpha * 0.4f),
            radius = baseRadius * scale * 0.1f,
            center = Offset(
                center.x - baseRadius * 0.08f,
                center.y - baseRadius * 0.08f
            )
        )
    }
}
