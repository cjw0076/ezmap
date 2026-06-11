package com.example.ez_capstone.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ez_capstone.server.models.RouteItem
import com.example.ez_capstone.ui.theme.Accent
import com.example.ez_capstone.ui.theme.AccentDim
import com.example.ez_capstone.ui.theme.GradientAccent
import com.example.ez_capstone.ui.theme.Orbitron
import com.example.ez_capstone.ui.theme.Rajdhani
import com.example.ez_capstone.ui.theme.TextDim
import com.example.ez_capstone.ui.theme.TextPrimary
import com.example.ez_capstone.ui.theme.TextSecondary

@Composable
fun RouteCarousel(
    routes: List<RouteItem>,
    selectedIndex: Int,
    onRouteSelected: (Int) -> Unit,
    onStartNavigation: (RouteItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(routes) { index, route ->
                RouteCard(
                    route = route,
                    index = index,
                    isSelected = index == selectedIndex,
                    onClick = { onRouteSelected(index) }
                )
            }
        }

        if (routes.isNotEmpty() && selectedIndex in routes.indices) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onStartNavigation(routes[selectedIndex]) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent.copy(alpha = 0.15f)
                ),
                border = BorderStroke(1.dp, Brush.horizontalGradient(GradientAccent))
            ) {
                Text(
                    "안내 시작",
                    color = Accent,
                    fontFamily = Orbitron,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun RouteCard(
    route: RouteItem,
    index: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1f,
        label = "route_scale"
    )

    GlassCard(
        modifier = Modifier
            .width(200.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(onClick = onClick),
        glowColor = if (isSelected) Accent else Accent.copy(alpha = 0.3f),
        glowEnabled = isSelected,
        borderAlpha = if (isSelected) 0.35f else 0.1f,
        contentPadding = 14.dp
    ) {
        Column {
            // Route badge
            Text(
                text = "경로 ${index + 1}",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = Rajdhani,
                    color = if (isSelected) Accent else TextSecondary,
                    letterSpacing = 1.sp
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Duration in Orbitron
                Text(
                    text = formatDuration(route.duration_s),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = Orbitron,
                        fontWeight = FontWeight.Bold,
                        color = Accent,
                        fontSize = 20.sp
                    )
                )
                Text(
                    text = formatDistance(route.distance_m),
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextDim)
                )
            }
            route.waypoints?.let { wps ->
                if (wps.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "경유: ${wps.joinToString(" → ") { it.name }}",
                        style = MaterialTheme.typography.labelMedium.copy(color = TextDim),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun formatDistance(meters: Int): String = when {
    meters >= 1000 -> String.format("%.1f km", meters / 1000.0)
    else -> "${meters}m"
}

private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}분"
}
