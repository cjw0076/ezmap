package com.example.ez_capstone.ui.components.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ez_capstone.ui.components.GlassCard
import com.example.ez_capstone.ui.theme.Accent

@Composable
fun QuickActions(
    onScheduleClick: () -> Unit,
    onSearchClick: () -> Unit,
    onMessageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier,
        glowEnabled = false,
        backgroundAlpha = 0.5f,
        cornerRadius = 12.dp,
        contentPadding = 4.dp
    ) {
        Row {
            IconButton(onClick = onScheduleClick, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = "일정", tint = Accent)
            }
            IconButton(onClick = onSearchClick, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Search, contentDescription = "검색", tint = Accent)
            }
            IconButton(onClick = onMessageClick, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "메시지", tint = Accent)
            }
        }
    }
}
