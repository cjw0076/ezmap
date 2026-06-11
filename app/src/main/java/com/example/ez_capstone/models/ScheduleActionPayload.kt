package com.example.ez_capstone.models

import com.google.gson.annotations.SerializedName

data class ScheduleActionPayload(
    @SerializedName("type") val type: String,
    @SerializedName("title") val title: String?,
    @SerializedName("time") val time: String?
)
