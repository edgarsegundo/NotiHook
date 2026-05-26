package com.neapp.tracker.model

import kotlinx.serialization.Serializable

@Serializable
data class LocationPayload(
    val id: String,
    val lat: Double,
    val lng: Double,
    val ts: Long,
    val speed: Float
)