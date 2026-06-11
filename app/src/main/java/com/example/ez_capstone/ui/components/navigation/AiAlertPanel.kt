package com.example.ez_capstone.ui.components.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ez_capstone.ui.theme.*

data class AiAlert(
    val message: String,
    val type: AlertType = AlertType.INFO,
    val primaryAction: String? = null,
    val secondaryAction: String? = null,
    val timestamp: String = "방금"
)

enum class AlertType { INFO, WARNING, ROUTE_CHANGE, ARRIVAL }

/**
 * OBSIDIAN AI Alert Panel — gradient card + left accent border.
 *
 *   ● AI 알림                         방금
 *   전방 1.2km 사고 발생.
 *   대안 경로로 5분 단축 가능.
 *   [ 경로 변경 ]  [ 유지 ]
 */
@Composable
fun AiAlertPanel(
    alert: AiAlert?,
    onPrimaryAction: () -> Unit = {},
    onSecondaryAction: () -> Unit = {},
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = alert != null,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(300)
        ) + fadeIn(animationSpec = tween(300)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(200)
        ) + fadeOut(animationSpec = tween(200)),
        modifier = modifier.padding(horizontal = 12.dp)
    ) {
        alert?.let { a ->
            val alertColor = when (a.type) {
                AlertType.INFO -> Accent
                AlertType.WARNING -> Warning
                AlertType.ROUTE_CHANGE -> Warning
                AlertType.ARRIVAL -> Success
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(SurfaceHigh, SurfaceTop)))
                    .border(1.dp, alertColor.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            ) {
                // Left accent border
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .matchParentSize()
                        .background(alertColor)
                )

                Column(
                    modifier = Modifier.padding(
                        start = 16.dp, end = 16.dp,
                        top = 14.dp, bottom = 14.dp
                    )
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(alertColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "AI 알림",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = alertColor,
                                letterSpacing = 1.sp
                            )
                        }
                        Text(text = a.timestamp, fontSize = 9.sp, color = TextDim)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Message
                    Text(
                        text = a.message,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        lineHeight = 19.sp
                    )

                    // Actions
                    if (a.primaryAction != null || a.secondaryAction != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            a.primaryAction?.let { label ->
                                Button(
                                    onClick = onPrimaryAction,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = alertColor,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            a.secondaryAction?.let { label ->
                                OutlinedButton(
                                    onClick = onSecondaryAction,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                                ) {
                                    Text(
                                        label, fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
