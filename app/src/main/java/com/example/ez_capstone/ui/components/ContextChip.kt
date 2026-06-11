package com.example.ez_capstone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
 * 컨텍스트 칩 — 지도 하단 수평 스크롤.
 * ui_flow.md §5 컨텍스트 칩 상세.
 * 위치 · 날씨 · 일정 수 · 배터리(10% 이하만)
 */

data class ChipData(
    val icon: String,      // "◎", "◈", "◉", "⚡"
    val label: String,     // "삼산동", "14°C", "2 일정"
    val sublabel: String?, // "맑음", "오늘", "충전 중"
    val onClick: () -> Unit = {}
)

@Composable
fun ContextChipRow(
    chips: List<ChipData>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chips.forEach { chip ->
            ContextChip(chip)
        }
    }
}

@Composable
private fun ContextChip(data: ChipData) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(shape)
            .background(Surface.copy(alpha = 0.85f))
            .border(1.dp, Border, shape)
            .clickable(onClick = data.onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = data.icon,
            fontSize = 12.sp,
            color = Accent
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = data.label,
            fontSize = 12.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Medium
        )
        if (data.sublabel != null) {
            Spacer(Modifier.width(3.dp))
            Text(
                text = data.sublabel,
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
    }
}
