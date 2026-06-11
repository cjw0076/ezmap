package com.example.ez_capstone.ui.components.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ez_capstone.ui.components.GlassCard
import com.example.ez_capstone.ui.theme.*

/**
 * PROACTIVE_SUGGESTION — 에이전트가 먼저 띄우는 제안 카드.
 * 상단에서 슬라이드 다운. [실행] [무시] 버튼.
 */
@Composable
fun ProactiveSuggestionCard(
    message: String,
    actionLabel: String = "출발하기",
    onAction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        glowColor = Accent,
        glowEnabled = true,
        borderAlpha = 0.2f
    ) {
        Column {
            // AI 추천 뱃지
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .padding(end = 0.dp)
                ) // spacer for dot
                Text(
                    text = "◆ AI 추천",
                    color = Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = message,
                color = TextPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(actionLabel, color = Background, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text("무시", fontSize = 13.sp)
                }
            }
        }
    }
}
