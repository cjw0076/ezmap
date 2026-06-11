package com.example.ez_capstone.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ez_capstone.ui.theme.Secondary
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private data class OrbitItem(
    val icon: ImageVector,
    val label: String
)

private val orbitItems = listOf(
    OrbitItem(Icons.Filled.Navigation, "내비게이션"),
    OrbitItem(Icons.Filled.CalendarMonth, "일정"),
    OrbitItem(Icons.Filled.Mic, "음성"),
    OrbitItem(Icons.Filled.Search, "검색"),
    OrbitItem(Icons.Filled.Person, "프로필")
)

@Composable
fun OrbitIcons(
    modifier: Modifier = Modifier,
    orbitRadius: Dp = 130.dp
) {
    val rotationAngle = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Auto-rotation when not dragging
    LaunchedEffect(isDragging) {
        if (!isDragging) {
            while (true) {
                val remaining = 360f - (rotationAngle.value % 360f)
                rotationAngle.animateTo(
                    targetValue = rotationAngle.value + remaining,
                    animationSpec = tween(
                        durationMillis = (remaining / 360f * 30000).toInt(),
                        easing = LinearEasing
                    )
                )
            }
        }
    }

    val density = LocalDensity.current
    val radiusPx = with(density) { orbitRadius.toPx() }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val angleDelta = dragAmount.x * 0.5f
                        scope.launch {
                            rotationAngle.snapTo(rotationAngle.value + angleDelta)
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                    },
                    onDragCancel = {
                        isDragging = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        orbitItems.forEachIndexed { index, item ->
            val baseAngle = (360f / orbitItems.size) * index
            val angle = Math.toRadians((baseAngle + rotationAngle.value).toDouble())

            val offsetXPx = (cos(angle) * radiusPx).toFloat()
            val offsetYPx = (sin(angle) * radiusPx).toFloat()

            val offsetX = with(density) { offsetXPx.toDp() }
            val offsetY = with(density) { offsetYPx.toDp() }

            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = Secondary.copy(alpha = 0.8f),
                modifier = Modifier
                    .offset(x = offsetX, y = offsetY)
                    .size(28.dp)
            )
        }
    }
}
