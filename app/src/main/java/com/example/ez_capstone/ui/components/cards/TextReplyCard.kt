package com.example.ez_capstone.ui.components.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ez_capstone.ui.components.GlassCard
import com.example.ez_capstone.ui.theme.*

/**
 * TEXT_REPLY 카드 — 에이전트 텍스트 응답.
 * 하단 바텀시트 형태, 드래그 핸들 + "왜?" 접이식 패널.
 */
@Composable
fun TextReplyCard(
    text: String,
    traceSummary: String? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var traceExpanded by remember { mutableStateOf(false) }

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

            // 에이전트 인디케이터 + 텍스트
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp, end = 8.dp)
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Accent)
                )
                Text(
                    text = text,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }

            // "왜?" 접이식 패널
            if (traceSummary != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "ⓘ 왜 이렇게 판단했나요?",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { traceExpanded = !traceExpanded }
                )
                AnimatedVisibility(
                    visible = traceExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Text(
                        text = traceSummary,
                        color = TextDim,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}
