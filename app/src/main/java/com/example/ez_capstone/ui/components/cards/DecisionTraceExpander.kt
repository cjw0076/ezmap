package com.example.ez_capstone.ui.components.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ez_capstone.ui.theme.*

/**
 * DecisionTrace "왜?" 접이식 패널.
 * 모든 응답 카드 하단에 포함. 탭하면 판단 과정 표시.
 */
@Composable
fun DecisionTraceExpander(
    summary: String?,
    modifier: Modifier = Modifier
) {
    if (summary.isNullOrBlank()) return

    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.padding(top = 10.dp)) {
        Text(
            text = if (expanded) "ⓘ 판단 과정 접기" else "ⓘ 왜 이렇게 판단했나요?",
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.clickable { expanded = !expanded }
        )

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(top = 6.dp)) {
                // 요약을 " → " 기준으로 분리해서 step별로 표시
                val steps = summary.split(" → ", "→")
                if (steps.size > 1) {
                    steps.forEachIndexed { idx, step ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(
                                text = "${idx + 1}.",
                                color = Accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(16.dp)
                            )
                            Text(
                                text = step.trim(),
                                color = TextDim,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                } else {
                    Text(
                        text = summary,
                        color = TextDim,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
