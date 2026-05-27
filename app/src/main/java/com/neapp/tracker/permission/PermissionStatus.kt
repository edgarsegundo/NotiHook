package com.neapp.tracker.permission

data class PermissionStatus(
    val backgroundLocation: Boolean,
    val bateria: Boolean
) {
    val tudoOk get() = backgroundLocation && bateria
}
