package com.neapp.tracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.neapp.tracker.model.DeliveryConfig
import com.neapp.tracker.scheduler.SchedulerManager

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences("tracker_prefs", Context.MODE_PRIVATE)

        val driverId = prefs.getString("driver_id", "") ?: return
        if (driverId.isEmpty()) return

        val shiftStart = prefs.getString("shift_start", "") ?: return
        val shiftEnd = prefs.getString("shift_end", "") ?: return

        if (shiftStart.isEmpty() || shiftEnd.isEmpty()) return

        val config = DeliveryConfig(
            shiftStart = shiftStart,
            shiftEnd = shiftEnd,
            movingIntervalSeconds = prefs.getInt("moving_interval_seconds", 15),
            idleIntervalSeconds = prefs.getInt("idle_interval_seconds", 30)
        )

        SchedulerManager.schedule(context, config)
    }
}