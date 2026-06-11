package com.example.ez_capstone.ui.components.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ez_capstone.navi.DrivingLiveScore
import com.example.ez_capstone.navi.DrivingSummary
import com.example.ez_capstone.ui.components.GlassCard
import com.example.ez_capstone.ui.theme.Accent
import com.example.ez_capstone.ui.theme.AccentDim
import com.example.ez_capstone.ui.theme.Error
import com.example.ez_capstone.ui.theme.GradientAccent
import com.example.ez_capstone.ui.theme.Orbitron
import com.example.ez_capstone.ui.theme.Success
import com.example.ez_capstone.ui.theme.SurfaceHigh
import com.example.ez_capstone.ui.theme.Rajdhani
import com.example.ez_capstone.ui.theme.TextDim
import com.example.ez_capstone.ui.theme.TextPrimary
import com.example.ez_capstone.ui.theme.Warning

@Composable
fun ArrivalOverlay(
    summary: DrivingSummary,
    drivingScore: DrivingLiveScore? = null,
    nearbyParking: String? = null,  // "강남 공영주차장 · 도보 3분 · 3,000원/시간"
    onFeedbackSubmit: ((rating: Int, comment: String) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSaveRoute: (() -> Unit)? = null,
    onParkingNavigate: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            glowColor = Accent,
            glowEnabled = true,
            backgroundAlpha = 0.8f,
            cornerRadius = 20.dp,
            contentPadding = 24.dp
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(text = "\uD83C\uDFC1", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "목적지 도착",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = Orbitron,
                        color = TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SummaryItem(formatDistance(summary.totalDistance), if (summary.totalDistance >= 1000) "km" else "m", Success)
                    SummaryItem(formatTime(summary.totalTime), "min", AccentDim)
                    SummaryItem(summary.avgSpeed.toString(), "km/h", Accent)
                }

                // 운전점수 섹션
                if (drivingScore != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    GlassCard(
                        glowEnabled = false,
                        borderAlpha = 0.1f,
                        contentPadding = 12.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "이번 주행 점수",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val scoreColor = when {
                                    drivingScore.current >= 90 -> Success
                                    drivingScore.current >= 70 -> Warning
                                    else -> Error
                                }
                                Text(
                                    "${drivingScore.current}점",
                                    fontFamily = Orbitron,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = scoreColor
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(SurfaceHigh)
                            ) {
                                val scoreBarColor = when {
                                    drivingScore.current >= 90 -> Success
                                    drivingScore.current >= 70 -> Warning
                                    else -> Error
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(drivingScore.current / 100f)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(scoreBarColor)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            val details = buildString {
                                if (drivingScore.speedingCount == 0) append("속도 위반 없음")
                                else append("속도 위반 ${drivingScore.speedingCount}회")
                                append("  ·  ")
                                if (drivingScore.hardBrakeCount == 0) append("급제동 없음")
                                else append("급제동 ${drivingScore.hardBrakeCount}회")
                                if (drivingScore.sharpTurnCount > 0) append("  ·  급회전 ${drivingScore.sharpTurnCount}회")
                            }
                            Text(details, color = TextDim, fontSize = 11.sp)
                        }
                    }
                }

                // 근처 주차장 (자차일 때)
                if (nearbyParking != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    GlassCard(
                        glowEnabled = false,
                        borderAlpha = 0.1f,
                        contentPadding = 12.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text("📍 근처 주차장", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(nearbyParking, color = TextDim, fontSize = 12.sp)
                            if (onParkingNavigate != null) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "주차장 안내 →",
                                    color = Accent,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }

                // 피드백 섹션
                if (onFeedbackSubmit != null) {
                    var selectedRating by remember { mutableIntStateOf(0) }
                    var feedbackComment by remember { mutableStateOf("") }
                    var feedbackSubmitted by remember { mutableStateOf(false) }

                    Spacer(Modifier.height(16.dp))
                    if (!feedbackSubmitted) {
                        GlassCard(
                            glowEnabled = false,
                            borderAlpha = 0.1f,
                            contentPadding = 12.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(
                                    "이번 경로 어떠셨나요?",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    (1..5).forEach { star ->
                                        IconButton(
                                            onClick = { selectedRating = star },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (star <= selectedRating) Icons.Filled.Star else Icons.Filled.StarBorder,
                                                contentDescription = "${star}점",
                                                tint = if (star <= selectedRating) Warning else TextDim,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                                if (selectedRating > 0) {
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = feedbackComment,
                                        onValueChange = { feedbackComment = it },
                                        placeholder = { Text("추가 의견 (선택)", fontSize = 12.sp, color = TextDim) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Accent,
                                            unfocusedBorderColor = TextDim.copy(0.3f),
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            onFeedbackSubmit(selectedRating, feedbackComment)
                                            feedbackSubmitted = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Accent.copy(0.15f)),
                                        border = BorderStroke(1.dp, Accent.copy(0.4f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("제출", color = Accent, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            "피드백 감사합니다 ★",
                            color = Warning,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onSaveRoute != null) {
                        Button(
                            onClick = onSaveRoute,
                            colors = ButtonDefaults.buttonColors(containerColor = Accent.copy(alpha = 0.1f)),
                            border = BorderStroke(1.dp, Accent.copy(alpha = 0.3f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("경로 저장", color = Accent, fontSize = 14.sp)
                        }
                    }
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, Brush.horizontalGradient(GradientAccent)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "종료",
                            color = Accent,
                            fontFamily = Orbitron,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryItem(value: String, unit: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = Orbitron,
                fontWeight = FontWeight.Bold,
                color = color
            )
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = Rajdhani,
                color = TextDim
            )
        )
    }
}

private fun formatDistance(meters: Int): String {
    return if (meters >= 1000) "%.1f".format(meters / 1000.0) else "$meters"
}

private fun formatTime(seconds: Int): String {
    val min = seconds / 60
    return if (min < 60) "$min" else "${min / 60}h${min % 60}"
}
