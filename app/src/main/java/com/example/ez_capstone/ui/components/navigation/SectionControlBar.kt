package com.example.ez_capstone.ui.components.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ez_capstone.ui.theme.*

/**
 * 구간단속 진행 상태 바.
 * sectionProgress = 0f(시작) → 1f(종료)
 * remainingM: 구간 내 남은 거리 (미터)
 */
@Composable
fun SectionControlBar(
    sectionProgress: Float,         // 0f ~ 1f
    remainingM: Int,
    limitSpeed: Int,
    isOverspeed: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = sectionProgress.coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "section_progress"
    )
    val barColor = when {
        isOverspeed -> Error
        sectionProgress > 0.5f -> Warning
        else -> Warning.copy(alpha = 0.7f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCard)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "구간단속",
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                color = barColor,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "제한 ${limitSpeed}km/h  ·  ${remainingM}m 남음",
                fontSize = 9.sp,
                color = TextDim
            )
        }
        Spacer(Modifier.height(5.dp))
        // 프로그레스 트랙
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(SurfaceHigh)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
    }
}
