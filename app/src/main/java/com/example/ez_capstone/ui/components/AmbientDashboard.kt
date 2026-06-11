package com.example.ez_capstone.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ez_capstone.ui.theme.Accent
import com.example.ez_capstone.ui.theme.Border
import com.example.ez_capstone.ui.theme.Orbitron
import com.example.ez_capstone.ui.theme.Purple
import com.example.ez_capstone.ui.theme.Rajdhani
import com.example.ez_capstone.ui.theme.SurfaceCard
import com.example.ez_capstone.ui.theme.TextDim
import com.example.ez_capstone.ui.theme.TextPrimary
import com.example.ez_capstone.ui.theme.TextSecondary
import com.example.ez_capstone.viewmodel.ConversationViewModel
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * Ambient Intelligence Dashboard — ConversationScreen의 기본(Idle) 상태 UI.
 *
 * 지도 대신 AI 비서의 현재 인식 상태를 보여준다:
 * - 현재 시간 / 날짜
 * - AI 인사말
 * - 다음 일정 (있으면)
 * - 빠른 목적지 칩 (집, 회사, 최근)
 * - 웨이크워드 힌트
 */
@Composable
fun AmbientDashboard(
    ambientData: ConversationViewModel.AmbientData,
    quickDestinations: List<ConversationViewModel.QuickDestination>,
    onQuickDestination: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 실시간 시계 (30초마다 갱신)
    var timeText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }
    var greeting by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val cal = Calendar.getInstance()
            val h = cal.get(Calendar.HOUR_OF_DAY)
            val m = cal.get(Calendar.MINUTE)
            timeText = "%02d:%02d".format(h, m)

            val days = listOf("일", "월", "화", "수", "목", "금", "토")
            val dow = days[cal.get(Calendar.DAY_OF_WEEK) - 1]
            val mon = cal.get(Calendar.MONTH) + 1
            val day = cal.get(Calendar.DAY_OF_MONTH)
            dateText = "${dow}요일 · ${mon}월 ${day}일"

            greeting = when {
                h < 6  -> "야심한 밤이에요"
                h < 12 -> "좋은 아침이에요"
                h < 18 -> "좋은 오후예요"
                else   -> "좋은 저녁이에요"
            }
            delay(30_000L)
        }
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(500)),
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 72.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {

            // ── 시계 블록 ──
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = timeText,
                    fontSize = 60.sp,
                    fontFamily = Orbitron,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = 0.sp
                )
                Text(
                    text = dateText,
                    fontSize = 14.sp,
                    fontFamily = Rajdhani,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── AI 인사말 ──
            Text(
                text = greeting,
                fontSize = 16.sp,
                fontFamily = Rajdhani,
                fontWeight = FontWeight.SemiBold,
                color = Accent,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── 다음 일정 카드 (있을 때만) ──
            if (ambientData.nextEventTitle != null) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    glowColor = Purple,
                    glowEnabled = false
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Purple.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CalendarMonth,
                                contentDescription = null,
                                tint = Purple,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "다음 일정",
                                fontSize = 11.sp,
                                color = TextDim,
                                fontFamily = Rajdhani
                            )
                            Text(
                                text = ambientData.nextEventTitle,
                                fontSize = 14.sp,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ── 빠른 목적지 ──
            if (quickDestinations.isNotEmpty()) {
                Text(
                    text = "빠른 이동",
                    fontSize = 11.sp,
                    color = TextDim,
                    fontFamily = Rajdhani,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quickDestinations.take(3).forEach { dest ->
                        val icon = when {
                            dest.label.contains("집") -> Icons.Filled.Home
                            dest.label.contains("회사") -> Icons.Filled.Work
                            else -> Icons.Filled.LocationOn
                        }
                        QuickDestChip(
                            label = dest.label,
                            icon = icon,
                            onClick = { onQuickDestination(dest.address) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── AI 준비 상태 + 힌트 ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 156.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Accent)
                    )
                    Text(
                        text = "이지(EZ) 대기 중",
                        fontSize = 12.sp,
                        fontFamily = Rajdhani,
                        color = Accent,
                        letterSpacing = 0.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "마이크를 탭하거나 메시지를 입력하세요",
                    fontSize = 11.sp,
                    color = TextDim,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun QuickDestChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCard)
            .border(1.dp, Border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label.replace("🏠 ", "").replace("🏢 ", ""),
            fontSize = 13.sp,
            fontFamily = Rajdhani,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
    }
}
