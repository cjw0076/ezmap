package com.example.ez_capstone.ui.components.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ez_capstone.ui.components.GlassCard
import com.example.ez_capstone.ui.theme.*
import kotlinx.coroutines.delay

/**
 * DEPARTURE_COUNTDOWN — 출발 카운트다운 + CTA.
 * 5분 남으면 Warning 보더, 1분 남으면 Error.
 */
@Composable
fun DepartureCountdownCard(
    departureTime: String,       // "15:23"
    minutesLeft: Int,
    destination: String,
    eventTitle: String?,
    arrivalTime: String?,        // "16:00"
    travelMode: String,          // "자차 37분"
    onStartNow: () -> Unit,
    onSnooze: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        minutesLeft <= 1 -> Error
        minutesLeft <= 5 -> Warning
        else -> Accent
    }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        glowColor = borderColor,
        glowEnabled = minutesLeft <= 5,
        borderAlpha = if (minutesLeft <= 5) 0.4f else 0.15f
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 출발 시간 (큰 HUD 스타일)
            Text(
                text = "$departureTime 출발",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "── ${minutesLeft}분 후 ──",
                color = borderColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(16.dp))

            // 정보
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                eventTitle?.let {
                    Row {
                        Text("📍 ", fontSize = 14.sp)
                        Text(it, color = TextPrimary, fontSize = 14.sp)
                    }
                }
                arrivalTime?.let {
                    Row {
                        Text("🕐 ", fontSize = 14.sp)
                        Text("$it 도착 예상", color = TextSecondary, fontSize = 14.sp)
                    }
                }
                Row {
                    Text("🚗 ", fontSize = 14.sp)
                    Text(travelMode, color = TextSecondary, fontSize = 14.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            // CTA
            Button(
                onClick = onStartNow,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("지금 출발하기", color = Background, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(Modifier.height(8.dp))

            // 보조 액션
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = onSnooze) {
                    Text("5분 뒤 알려줘", color = TextSecondary, fontSize = 12.sp)
                }
                TextButton(onClick = onCancel) {
                    Text("취소", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}
