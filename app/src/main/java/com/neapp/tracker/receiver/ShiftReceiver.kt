package com.neapp.tracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.neapp.tracker.api.ApiService
import com.neapp.tracker.model.DeliveryConfig
import com.neapp.tracker.service.LocationForegroundService
import com.neapp.tracker.scheduler.SchedulerManager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ShiftReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_WAKE = "com.neapp.tracker.ACTION_WAKE"
        const val ACTION_SLEEP = "com.neapp.tracker.ACTION_SLEEP"
        const val ACTION_CHECK_CONFIG = "com.neapp.tracker.ACTION_CHECK_CONFIG"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("tracker_prefs", Context.MODE_PRIVATE)
        val driverId = prefs.getString("driver_id", "") ?: return

        when (intent.action) {
            ACTION_WAKE -> {
                fetchConfigAndStart(context, driverId)
            }
            ACTION_SLEEP -> {
                context.stopService(
                    Intent(context, LocationForegroundService::class.java)
                )
                SchedulerManager.cancelAll(context)
                val savedStart = prefs.getString("shift_start", "08:00") ?: "08:00"
                val savedEnd = prefs.getString("shift_end", "18:00") ?: "18:00"
                val savedMoving = prefs.getInt("moving_interval_seconds", 15)
                val savedIdle = prefs.getInt("idle_interval_seconds", 30)
                val config = DeliveryConfig(savedStart, savedEnd, savedMoving, savedIdle)
                SchedulerManager.schedule(context, config)
            }
            ACTION_CHECK_CONFIG -> {
                fetchConfigAndRescheduleIfChanged(context, driverId)
            }
        }
    }

    private fun fetchConfigAndStart(context: Context, driverId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val config = ApiService.Companion.create().getConfig(driverId)
                saveConfig(context, config)
                SchedulerManager.schedule(context, config)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun fetchConfigAndRescheduleIfChanged(context: Context, driverId: String) {
        val prefs = context.getSharedPreferences("tracker_prefs", Context.MODE_PRIVATE)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val config = ApiService.Companion.create().getConfig(driverId)
                val savedStart = prefs.getString("shift_start", "") ?: ""
                val savedEnd = prefs.getString("shift_end", "") ?: ""

                val changed = config.shiftStart != savedStart || config.shiftEnd != savedEnd

                if (changed) {
                    saveConfig(context, config)
                    SchedulerManager.cancelAll(context)
                    SchedulerManager.schedule(context, config)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveConfig(context: Context, config: DeliveryConfig) {
        context.getSharedPreferences("tracker_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("shift_start", config.shiftStart)
            .putString("shift_end", config.shiftEnd)
            .putInt("moving_interval_seconds", config.movingIntervalSeconds)
            .putInt("idle_interval_seconds", config.idleIntervalSeconds)
            .apply()
    }
}