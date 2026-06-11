package com.example.ez_capstone.server

import com.example.ez_capstone.server.models.ScheduleCreateRequest
import com.example.ez_capstone.server.models.ScheduleItem
import javax.inject.Inject

class ScheduleRepository @Inject constructor(
    private val scheduleApi: ScheduleApi
) {
    suspend fun getSchedules(): List<ScheduleItem> {
        return scheduleApi.getSchedules().schedules
    }

    suspend fun createSchedule(title: String, cronExpression: String): ScheduleItem {
        return scheduleApi.createSchedule(
            ScheduleCreateRequest(
                title = title,
                cron_expression = cronExpression
            )
        ).schedule
    }

    suspend fun deleteSchedule(id: Int): Boolean {
        return scheduleApi.deleteSchedule(id).ok
    }
}
