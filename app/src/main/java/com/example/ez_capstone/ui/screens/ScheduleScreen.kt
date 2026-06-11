package com.example.ez_capstone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ez_capstone.db.entity.ScheduleEntity
import com.example.ez_capstone.ui.theme.AccentEnd
import com.example.ez_capstone.ui.theme.Background
import com.example.ez_capstone.ui.theme.CardBackground
import com.example.ez_capstone.ui.theme.Secondary
import com.example.ez_capstone.ui.theme.TextPrimary
import com.example.ez_capstone.ui.theme.TextSecondary
import com.example.ez_capstone.viewmodel.ScheduleViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ScheduleScreen(
    onBackClick: () -> Unit,
    onNavigateToPlace: (String) -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Top bar
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = TextPrimary)
                }
                Text(
                    text = "일정",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Calendar
            CalendarView(
                currentMonth = state.currentMonth,
                selectedDate = state.selectedDate,
                schedules = state.schedules,
                onDateSelected = { viewModel.selectDate(it) },
                onNavigateMonth = { viewModel.navigateMonth(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Selected date header
            Text(
                text = "${state.selectedDate.monthValue}월 ${state.selectedDate.dayOfMonth}일 일정",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Secondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Schedule list
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Secondary)
                }
            } else {
                val daySchedules = viewModel.getSchedulesForDate(state.selectedDate)
                if (daySchedules.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = TextSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "아직 일정이 없어요",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                        Text(
                            text = "AI가 일정에 맞춰 최적 경로를 추천해드려요",
                            fontSize = 12.sp,
                            color = TextSecondary.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = { viewModel.showAddDialog() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentEnd.copy(alpha = 0.8f)
                            ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("일정 추가하기", fontSize = 13.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(daySchedules) { schedule ->
                            ScheduleEntityCard(
                                schedule = schedule,
                                onNavigate = {
                                    schedule.destinationName?.let { name ->
                                        onNavigateToPlace(name)
                                    }
                                },
                                onDelete = { viewModel.deleteSchedule(schedule.id) }
                            )
                        }
                    }
                }
            }

            // Error
            state.error?.let { error ->
                Text(
                    text = error,
                    fontSize = 12.sp,
                    color = com.example.ez_capstone.ui.theme.Alert,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // FAB
        FloatingActionButton(
            onClick = { viewModel.showAddDialog() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = AccentEnd
        ) {
            Icon(Icons.Filled.Add, contentDescription = "일정 추가", tint = TextPrimary)
        }

        // Add dialog
        if (state.showAddDialog) {
            AddScheduleDialog(
                onDismiss = { viewModel.hideAddDialog() },
                onConfirm = { title, hour, minute ->
                    viewModel.createSchedule(title, hour, minute)
                }
            )
        }
    }
}

@Composable
private fun CalendarView(
    currentMonth: LocalDate,
    selectedDate: LocalDate,
    schedules: List<ScheduleEntity>,
    onDateSelected: (LocalDate) -> Unit,
    onNavigateMonth: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground.copy(alpha = 0.6f))
            .border(1.dp, Secondary.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        // Month navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onNavigateMonth(-1) }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "이전 달", tint = TextPrimary)
            }
            Text(
                text = "${currentMonth.year}년 ${currentMonth.monthValue}월",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            IconButton(onClick = { onNavigateMonth(1) }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "다음 달", tint = TextPrimary)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Day of week headers
        Row(modifier = Modifier.fillMaxWidth()) {
            val daysOfWeek = listOf("일", "월", "화", "수", "목", "금", "토")
            daysOfWeek.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Calendar grid
        val firstDay = currentMonth.withDayOfMonth(1)
        val lastDay = currentMonth.withDayOfMonth(currentMonth.lengthOfMonth())
        // Sunday = 0 offset
        val startOffset = (firstDay.dayOfWeek.value % 7)

        val totalCells = startOffset + currentMonth.lengthOfMonth()
        val rows = (totalCells + 6) / 7

        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0..6) {
                    val cellIndex = row * 7 + col
                    val dayNum = cellIndex - startOffset + 1

                    if (dayNum in 1..currentMonth.lengthOfMonth()) {
                        val date = currentMonth.withDayOfMonth(dayNum)
                        val isSelected = date == selectedDate
                        val isToday = date == LocalDate.now()
                        val hasSchedule = schedules.any { it.isActive && it.date == date.toString() }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .then(
                                    if (isSelected) Modifier.background(AccentEnd.copy(alpha = 0.3f))
                                    else Modifier
                                )
                                .clickable { onDateSelected(date) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$dayNum",
                                    fontSize = 14.sp,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isSelected -> Secondary
                                        isToday -> AccentEnd
                                        else -> TextPrimary
                                    }
                                )
                                if (hasSchedule) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(Secondary)
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleEntityCard(
    schedule: ScheduleEntity,
    onNavigate: () -> Unit,
    onDelete: () -> Unit
) {
    // Parse time from cron_expression: "minute hour * * *"
    val parts = (schedule.cronExpression ?: "").split(" ")
    val timeText = if (parts.size >= 2) {
        "%02d:%02d".format(parts[1].toIntOrNull() ?: 0, parts[0].toIntOrNull() ?: 0)
    } else ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground.copy(alpha = 0.6f))
            .border(1.dp, Secondary.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Time
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(56.dp)
        ) {
            Icon(Icons.Filled.Schedule, contentDescription = null, tint = Secondary, modifier = Modifier.size(16.dp))
            Text(text = timeText, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Secondary)
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title + location
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = schedule.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            schedule.destinationName?.let { name ->
                Text(
                    text = name,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        // Navigate button
        if (schedule.destinationName != null) {
            IconButton(onClick = onNavigate) {
                Icon(Icons.Filled.Navigation, contentDescription = "안내", tint = AccentEnd, modifier = Modifier.size(20.dp))
            }
        }

        // Delete button
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "삭제", tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun AddScheduleDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, hour: Int, minute: Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var hour by remember { mutableIntStateOf(9) }
    var minute by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBackground,
        title = { Text("일정 추가", color = TextPrimary) },
        text = {
            Column {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("일정 제목", color = TextSecondary) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Background,
                        unfocusedContainerColor = Background,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Secondary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("시간", fontSize = 14.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Hour picker
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { hour = (hour + 1) % 24 }) {
                            Text("▲", color = Secondary)
                        }
                        Text(
                            text = "%02d".format(hour),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        IconButton(onClick = { hour = if (hour == 0) 23 else hour - 1 }) {
                            Text("▼", color = Secondary)
                        }
                    }

                    Text(":", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp))

                    // Minute picker
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { minute = (minute + 5) % 60 }) {
                            Text("▲", color = Secondary)
                        }
                        Text(
                            text = "%02d".format(minute),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        IconButton(onClick = { minute = if (minute < 5) 55 else minute - 5 }) {
                            Text("▼", color = Secondary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (title.isNotBlank()) onConfirm(title, hour, minute) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentEnd),
                enabled = title.isNotBlank()
            ) {
                Text("추가", color = TextPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", color = TextSecondary)
            }
        }
    )
}
