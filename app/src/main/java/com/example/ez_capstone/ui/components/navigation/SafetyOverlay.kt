package com.example.ez_capstone.ui.components.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ez_capstone.ui.theme.*

/**
 * OBSIDIAN Safety Overlay — speed camera & safety zone alerts.
 *
 *   ┌──────────────────────┐
 *   │ 과속 카메라            │
 *   │ 60 km/h      500m   │
 *   └──────────────────────┘
 */
@Composable
fun SafetyOverlay(
    visible: Boolean,
    type: String,
    speedLimit: Int,
    remainDistance: Int,
    currentSpeed: Int = 0,
    modifier: Modifier = Modifier
) {
    val isOverspeed = currentSpeed > speedLimit
    val alertColor = if (isOverspeed) Error else Warning

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(alertColor.copy(alpha = 0.12f))
                .border(1.dp, alertColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = when (type) {
                        "SPEED_CAMERA" -> "과속 카메라"
                        "SIGNAL_CAMERA" -> "신호 카메라"
                        "SECTION_START" -> "구간단속 시작"
                        "SECTION_END" -> "구간단속 종료"
                        else -> "안전 구간"
                    },
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = alertColor,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$speedLimit",
                    fontSize = 16.sp,
                    fontFamily = Orbitron,
                    fontWeight = FontWeight.Bold,
                    color = alertColor
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "km/h",
                fontSize = 10.sp,
                fontFamily = Rajdhani,
                color = alertColor.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "${remainDistance}m",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextDim
            )
        }
    }
}
