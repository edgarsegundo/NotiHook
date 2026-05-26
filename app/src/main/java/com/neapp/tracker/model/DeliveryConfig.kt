package com.neapp.tracker.model

import com.google.gson.annotations.SerializedName

data class DeliveryConfig(
    @SerializedName("shift_start") val shiftStart: String,
    @SerializedName("shift_end") val shiftEnd: String,
    @SerializedName("moving_interval_seconds") val movingIntervalSeconds: Int,
    @SerializedName("idle_interval_seconds") val idleIntervalSeconds: Int
)