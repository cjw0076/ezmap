package com.example.ez_capstone.models

data class ScheduleListResponse(
    val schedules: List<ScheduleItem>
)

data class ScheduleItem(
    val id: Int,
    val title: String,
    val cron_expression: String,
    val route_config: RouteConfig? = null,
    val push_enabled: Boolean,
    val is_active: Boolean,
    val created_at: String,
    val updated_at: String
)

data class RouteConfig(
    val origin: LocationPoint? = null,
    val destination: LocationPoint? = null
)

data class ScheduleCreateRequest(
    val title: String,
    val cron_expression: String,
    val route_config: RouteConfig? = null,
    val push_enabled: Boolean = true
)

data class ScheduleUpdateRequest(
    val title: String? = null,
    val cron_expression: String? = null,
    val route_config: RouteConfig? = null,
    val push_enabled: Boolean? = null,
    val is_active: Boolean? = null
)

data class ScheduleResponse(
    val schedule: ScheduleItem
)

data class DeleteResponse(
    val ok: Boolean
)
