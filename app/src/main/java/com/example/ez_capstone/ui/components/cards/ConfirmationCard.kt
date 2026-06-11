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
 * CONFIRMATION — "출발할까요?" 확인 카드.
 * 음성 "응"/"출발" → 출발, "아니"/"취소" → dismiss.
 */
@Composable
fun ConfirmationCard(
    message: String,
    details: List<String> = emptyList(), // ["자차 52분", "톨비 4,800원"]
    confirmLabel: String = "출발",
    cancelLabel: String = "취소",
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        glowColor = Accent,
        glowEnabled = true,
        borderAlpha = 0.2f
    ) {
        Column {
            // 헤더
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(8.dp)
                )
                Text(
                    text = "◆ 확인",
                    color = Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = message,
                color = TextPrimary,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )

            // 상세 정보
            if (details.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                details.forEach { detail ->
                    Text(
                        text = detail,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // 버튼
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(confirmLabel, color = Background, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(0.7f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Text(cancelLabel)
                }
            }
        }
    }
}
