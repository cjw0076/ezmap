package com.example.ez_capstone.ui.components.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ez_capstone.navi.TurnDirectionUi
import com.example.ez_capstone.ui.components.GlassCard
import com.example.ez_capstone.ui.theme.*

@Composable
fun TurnInstructionCard(
    turn: TurnDirectionUi,
    nextTurn: TurnDirectionUi? = null,
    distanceM: Int = 999,
    modifier: Modifier = Modifier
) {
    val urgencyColor by animateColorAsState(
        targetValue = when {
            distanceM <= 50  -> Error
            distanceM <= 100 -> Warning
            else             -> Accent
        },
        animationSpec = tween(300),
        label = "urgencyColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseFraction by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "pulseFraction"
    )
    val iconScale = if (distanceM <= 50) 1f + pulseFraction * 0.08f else 1f

    val glowEnabled = distanceM <= 100

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .padding(horizontal = 12.dp)
            .semantics {
                contentDescription = buildString {
                    append("다음 안내: ${turn.distanceText} 앞에서 ${turn.dirName}")
                    if (turn.roadName.isNotBlank()) append(", ${turn.roadName}")
                    nextTurn?.let { append(". 그 다음: ${it.distanceText} 앞에서 ${it.dirName}") }
                }
                liveRegion = LiveRegionMode.Polite
            },
        glowColor = urgencyColor,
        glowEnabled = glowEnabled,
        cornerRadius = 16.dp,
        contentPadding = 12.dp,
        borderAlpha = if (distanceM <= 50) 0.4f else if (distanceM <= 100) 0.25f else 0.12f
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .scale(iconScale)
                        .clip(RoundedCornerShape(16.dp))
                        .background(urgencyColor.copy(alpha = 0.14f))
                        .border(1.5.dp, urgencyColor.copy(alpha = 0.32f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    ManeuverIcon(
                        directionCode = turn.directionCode,
                        tint = urgencyColor,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = turn.dirName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (turn.roadName.isNotBlank()) {
                        Text(
                            text = turn.roadName,
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                    nextTurn?.let { next ->
                        Text(
                            text = "그 다음 ${next.icon} ${next.dirName} · ${next.distanceText}",
                            fontSize = 11.sp,
                            color = TextDim
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = turn.distanceText.replace(Regex("[^0-9.]"), ""),
                    fontFamily = Orbitron,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (distanceM <= 100) 28.sp else 24.sp,
                    color = urgencyColor
                )
                Text(
                    text = turn.distanceText.replace(Regex("[0-9.,]"), "").trim().ifBlank { "m" },
                    fontSize = 11.sp,
                    color = TextDim,
                    fontFamily = Rajdhani
                )
            }
        }
    }
}
