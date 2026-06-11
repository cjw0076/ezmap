package com.example.ez_capstone.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ez_capstone.ui.components.GlassCard
import com.example.ez_capstone.ui.theme.*

data class RouteDisplayItem(
    val summary: String,         // "경부고속도로 경유"
    val durationMin: Int,
    val distanceKm: Float,
    val tollFee: Int? = null,
    val isRecommended: Boolean = false
)

/**
 * ROUTE_CARD — 경로 1~3개 비교 카드.
 * 추천 경로: 액센트 보더 + "지금 출발" CTA
 * 대안 경로: 보더 없음, 약간 투명
 */
@Composable
fun RouteCard(
    routes: List<RouteDisplayItem>,
    reason: String? = null,
    onStartNavigation: (Int) -> Unit,
    onRouteSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        glowColor = Accent,
        glowEnabled = false,
        borderAlpha = 0.1f
    ) {
        Column {
            // 드래그 핸들
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 12.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TextDim)
            )

            // 헤더
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Navigation,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (routes.size > 1) "추천 경로" else "경로 안내",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            // 경로 리스트
            routes.forEachIndexed { index, route ->
                val isFirst = route.isRecommended || index == 0
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (index < routes.lastIndex) 8.dp else 0.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = buildString {
                                append("경로 ${index + 1}")
                                if (route.isRecommended) append(" (추천)")
                                append(": ${route.summary}")
                                append(", ${route.durationMin}분")
                                append(", ${String.format("%.1f", route.distanceKm)}km")
                                route.tollFee?.let { append(", 톨비 ${String.format("%,d", it)}원") }
                            }
                        }
                        .clickable { onRouteSelect(index) },
                    glowColor = if (isFirst) Accent else Border,
                    glowEnabled = isFirst,
                    borderAlpha = if (isFirst) 0.25f else 0.08f,
                    backgroundAlpha = if (isFirst) 0.95f else 0.7f,
                    contentPadding = 12.dp
                ) {
                    Column {
                        Text(
                            text = route.summary,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "${route.durationMin}분",
                                color = Accent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = String.format("%.1fkm", route.distanceKm),
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                            route.tollFee?.let {
                                Text(
                                    text = "톨비 ${String.format("%,d", it)}원",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        if (isFirst) {
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { onStartNavigation(index) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Accent
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                modifier = Modifier.semantics {
                                    contentDescription = "경로 ${index + 1} 지금 출발"
                                }
                            ) {
                                Text("⚡ 지금 출발", color = Background, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "선택 →",
                                color = Accent.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .semantics { contentDescription = "경로 ${index + 1} 선택" }
                                    .clickable { onStartNavigation(index) }
                            )
                        }
                    }
                }
            }

            // 판단 이유
            if (reason != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "ⓘ $reason",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}
