package com.neapp.tracker.model

import com.google.gson.annotations.SerializedName

data class DeliveryOffer(
    @SerializedName("event") val event: String = "new_delivery",
    @SerializedName("event_id") val eventId: String,
    @SerializedName("delivery_id") val deliveryId: String,
    @SerializedName("order_id") val orderId: String,
    @SerializedName("expires_at") val expiresAt: String,
    @SerializedName("pickup") val pickup: OfferPoint? = null,
    @SerializedName("dropoff") val dropoff: OfferPoint? = null,
    @SerializedName("distance_km") val distanceKm: Double? = null,
    @SerializedName("payout") val payout: Double? = null,
    @SerializedName("message") val message: String? = null
)

data class OfferPoint(
    @SerializedName("label") val label: String? = null,
    @SerializedName("lat") val lat: Double? = null,
    @SerializedName("lng") val lng: Double? = null
)
