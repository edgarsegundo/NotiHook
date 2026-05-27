package com.neapp.tracker.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

class PermissionChecker(private val context: Context) {

    fun temBackgroundLocation(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android < 10: background location já implícito no ACCESS_FINE_LOCATION
            true
        }
    }

    fun estaExcluidoDeBateria(): Boolean {
        val pm = context.getSystemService(PowerManager::class.java)
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun resumo(): PermissionStatus {
        return PermissionStatus(
            backgroundLocation = temBackgroundLocation(),
            bateria = estaExcluidoDeBateria()
        )
    }
}
