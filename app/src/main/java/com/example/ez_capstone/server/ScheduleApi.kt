package com.example.ez_capstone.server

import com.example.ez_capstone.server.models.DeleteResponse
import com.example.ez_capstone.server.models.ScheduleCreateRequest
import com.example.ez_capstone.server.models.ScheduleListResponse
import com.example.ez_capstone.server.models.ScheduleResponse
import com.example.ez_capstone.server.models.ScheduleUpdateRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface ScheduleApi {
    @GET("schedule/list")
    suspend fun getSchedules(): ScheduleListResponse

    @POST("schedule/create")
    suspend fun createSchedule(@Body request: ScheduleCreateRequest): ScheduleResponse

    @PUT("schedule/{id}")
    suspend fun updateSchedule(
        @Path("id") id: Int,
        @Body request: ScheduleUpdateRequest
    ): ScheduleResponse

    @DELETE("schedule/{id}")
    suspend fun deleteSchedule(@Path("id") id: Int): DeleteResponse
}
