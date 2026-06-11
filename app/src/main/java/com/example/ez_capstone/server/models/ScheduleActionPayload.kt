package com.example.ez_capstone.server.models

import com.google.gson.annotations.SerializedName

data class ScheduleActionPayload(
    @SerializedName("type") val type: String,   // "add" | "view"
    @SerializedName("title") val title: String?,
    @SerializedName("time") val time: String?
)
