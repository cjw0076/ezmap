package com.example.ez_capstone.ui.screens

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ez_capstone.ui.components.GlassCard
import com.example.ez_capstone.ui.components.KakaoMapCompose
import com.example.ez_capstone.ui.theme.*

/**
 * OBSIDIAN Route Selection Screen.
 *
 * Layout:
 *   [← 경로 선택]
 *   [Map with route polyline + start/end markers]
 *   [Route option 1 — selectable]
 *   [Route option 2]
 *   [Route option 3]
 *   [ 안내 시작 ]  (gradient button)
 */
data class RouteOption(
    val label: String,        // "추천 경로", "최단 시간", "무료 도로"
    val durationMin: Int,
    val distanceKm: Float,
    val fare: Int,
    val tag: String,          // "최적", "고속", "무료"
    val color: Color,
    val routeJson: String     // for navigation start
)

@Composable
fun RouteSelectionScreen(
    originName: String,
    destName: String,
    routeOptions: List<RouteOption>,
    onRouteSelected: (RouteOption) -> Unit,
    onBack: () -> Unit,
    centerLat: Double = 35.5433,
    centerLng: Double = 129.2599,
    routeCoords: List<com.example.ez_capstone.server.models.Coord> = emptyList()
) {
    var selectedIndex by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // ── Top Bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로",
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.padding(start = 4.dp)) {
                Text(
                    text = "경로 선택",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "$originName → $destName",
                    fontSize = 11.sp,
                    color = TextDim
                )
            }
        }

        // ── Map Area ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(Surface)
        ) {
            KakaoMapCompose(
                modifier = Modifier.fillMaxSize(),
                routeCoords = routeCoords,
                centerLat = centerLat,
                centerLng = centerLng,
                zoomLevel = 13,
                trackUser = false,
                routeColor = if (routeOptions.isNotEmpty()) routeOptions[selectedIndex].color else Color(0xFF1976D2)
            )
        }

        // ── Route Options ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (routeOptions.isEmpty()) {
                // Fallback
                Text("경로를 계산 중입니다...", color = TextDim, fontSize = 13.sp)
            } else {
                routeOptions.forEachIndexed { index, route ->
                    RouteOptionCard(
                        route = route,
                        isSelected = index == selectedIndex,
                        onClick = { selectedIndex = index }
                    )
                }
            }
        }

        // ── Start Navigation Button ──
        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Button(
                onClick = {
                    if (routeOptions.isNotEmpty()) {
                        onRouteSelected(routeOptions[selectedIndex])
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                enabled = routeOptions.isNotEmpty()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.linearGradient(listOf(Accent, AccentDim)),
                            shape = RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "안내 시작",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteOptionCard(
    route: RouteOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) route.color.copy(alpha = 0.5f) else Border,
        label = "routeBorder"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) route.color.copy(alpha = 0.08f) else SurfaceCard,
        label = "routeBg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: color bar + info
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(route.color)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = route.label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${route.distanceKm}km · ₩${"%,d".format(route.fare)}",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }

        // Right: time + tag
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${route.durationMin}분",
                fontSize = 22.sp,
                fontFamily = Orbitron,
                fontWeight = FontWeight.Bold,
                color = route.color
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(route.color.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = route.tag,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = route.color
                )
            }
        }
    }
}
