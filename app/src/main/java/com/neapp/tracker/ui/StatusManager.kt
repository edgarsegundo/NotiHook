package com.neapp.tracker.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TrackingStatus {
    ACTIVE,
    SLEEPING,
    NO_SIGNAL
}

object StatusManager {

    private val _status = MutableStateFlow(TrackingStatus.SLEEPING)
    val status: StateFlow<TrackingStatus> = _status.asStateFlow()

    fun setStatus(status: TrackingStatus) {
        _status.value = status
    }
}