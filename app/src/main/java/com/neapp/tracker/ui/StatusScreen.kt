package com.neapp.tracker.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

enum class TrackingStatus {
    ACTIVE,
    SLEEPING,
    NO_SIGNAL
}

object StatusManager {

    private var currentStatus: TrackingStatus = TrackingStatus.SLEEPING
    private val listeners = mutableListOf<(TrackingStatus) -> Unit>()

    fun getStatus(): TrackingStatus = currentStatus

    fun setStatus(status: TrackingStatus) {
        currentStatus = status
        listeners.forEach { it(status) }
    }

    fun addListener(listener: (TrackingStatus) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (TrackingStatus) -> Unit) {
        listeners.remove(listener)
    }
}