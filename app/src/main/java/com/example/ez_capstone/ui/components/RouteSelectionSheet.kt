package com.example.ez_capstone.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ez_capstone.ui.theme.*

/**
 * 경로 비교 바텀시트 — 지도 위에 오버레이.
 * ui_flow.md §5: "Conversation → RouteSelection: 바텀시트 올라옴 (지도 유지)"
 *
 * 기존 RouteSelectionScreen의 전체화면 버전과 달리,
 * ModalBottomSheet로 지도를 가리지 않고 경로를 비교한다.
 */

data class SheetRouteOption(
    val label: String,        // "경부고속도로 경유"
    val durationMin: Int,
    val distanceKm: Float,
    val tollFee: Int = 0,
    val isRecommended: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteSelectionSheet(
    routes: List<SheetRouteOption>,
    originName: String = "현재 위치",
    destName: String = "목적지",
    onRouteStart: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Background,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TextDim)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // 헤더
            Text(
                text = "경로 선택",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$originName → $destName",
                color = TextDim,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(16.dp))

            // 경로 옵션
            routes.forEachIndexed { index, route ->
                val isSelected = index == selectedIndex
                val borderColor by animateColorAsState(
                    targetValue = if (isSelected) Accent.copy(alpha = 0.5f) else Border,
                    label = "border"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) Accent.copy(alpha = 0.06f) else SurfaceHigh)
                        .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
                        .clickable { selectedIndex = index }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (route.isRecommended) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Accent.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("추천", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(route.label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "%.1fkm · 톨비 %,d원".format(route.distanceKm, route.tollFee),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Text(
                        text = "${route.durationMin}분",
                        color = if (isSelected) Accent else TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // CTA
            Button(
                onClick = { onRouteStart(selectedIndex) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.linearGradient(listOf(Accent, AccentPurple)),
                            shape = RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⚡ 안내 시작",
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
