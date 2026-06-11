package com.example.ez_capstone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ez_capstone.server.models.ScheduleEvent
import com.example.ez_capstone.ui.theme.AccentEnd
import com.example.ez_capstone.ui.theme.CardBackground
import com.example.ez_capstone.ui.theme.Secondary
import com.example.ez_capstone.ui.theme.TextPrimary
import com.example.ez_capstone.ui.theme.TextSecondary

@Composable
fun SchedulePopup(
    events: List<ScheduleEvent>,
    message: String,
    onNavigateToEvent: (ScheduleEvent) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground.copy(alpha = 0.95f))
            .padding(16.dp)
    ) {
        Text(
            text = "일정",
            style = MaterialTheme.typography.titleLarge,
            color = Secondary,
            fontWeight = FontWeight.Bold
        )

        if (message.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                fontSize = 13.sp,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        events.forEachIndexed { index, event ->
            ScheduleEventRow(
                event = event,
                onNavigate = { onNavigateToEvent(event) }
            )
            if (index < events.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = Secondary.copy(alpha = 0.2f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = AccentEnd.copy(alpha = 0.3f)),
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("닫기", color = TextPrimary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ScheduleEventRow(
    event: ScheduleEvent,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )

            if (event.start_time.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.AccessTime,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.height(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = event.start_time,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            if (event.location.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.height(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = event.location,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        if (event.location.isNotBlank()) {
            IconButton(onClick = onNavigate) {
                Icon(
                    Icons.Filled.Navigation,
                    contentDescription = "안내 시작",
                    tint = AccentEnd
                )
            }
        }
    }
}
