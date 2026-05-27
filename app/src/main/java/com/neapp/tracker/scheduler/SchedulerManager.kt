package com.neapp.tracker.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.neapp.tracker.model.DeliveryConfig
import com.neapp.tracker.receiver.ShiftReceiver
import java.time.LocalTime
import java.time.temporal.ChronoUnit

object SchedulerManager {

    private const val TAG = "NeAppTracker"
    private const val REQUEST_WAKE = 0
    private const val REQUEST_SLEEP = 1
    private const val REQUEST_POLLING = 2
    private const val POLLING_INTERVAL_MS = 5 * 60 * 1000L

    fun schedule(context: Context, config: DeliveryConfig) {
        val now = LocalTime.now()
        val start = LocalTime.parse(config.shiftStart)
        val end = LocalTime.parse(config.shiftEnd)

        Log.d(TAG, "schedule() — now=$now  start=${config.shiftStart}  end=${config.shiftEnd}")

        when {
            now.isBefore(start) -> {
                Log.d(TAG, "schedule() → BEFORE shift, scheduling wake")
                scheduleWake(context, start)
                schedulePolling(context)
            }
            now.isAfter(start) && now.isBefore(end) -> {
                Log.d(TAG, "schedule() → INSIDE shift, starting service")
                startTrackingService(context, config)
                scheduleSleep(context, end)
                schedulePolling(context)
            }
            else -> {
                Log.d(TAG, "schedule() → AFTER shift, scheduling wake for tomorrow")
                scheduleWake(context, start)
                schedulePolling(context)
            }
        }
    }

    private fun scheduleWake(context: Context, time: LocalTime) {
        val intent = Intent(context, ShiftReceiver::class.java).apply {
            action = ShiftReceiver.Companion.ACTION_WAKE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_WAKE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + millisUntil(time),
            pendingIntent
        )
    }

    private fun scheduleSleep(context: Context, time: LocalTime) {
        val intent = Intent(context, ShiftReceiver::class.java).apply {
            action = ShiftReceiver.Companion.ACTION_SLEEP
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_SLEEP, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + millisUntil(time),
            pendingIntent
        )
    }

    fun schedulePolling(context: Context) {
        val intent = Intent(context, ShiftReceiver::class.java).apply {
            action = ShiftReceiver.Companion.ACTION_CHECK_CONFIG
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_POLLING, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + POLLING_INTERVAL_MS,
            pendingIntent
        )
    }

    fun startTrackingService(context: Context, config: DeliveryConfig) {
        val driverId = context.getSharedPreferences("tracker_prefs", Context.MODE_PRIVATE)
            .getString("driver_id", "") ?: return

        val intent = Intent(
            context,
            Class.forName("com.neapp.tracker.service.LocationForegroundService")
        ).apply {
            putExtra("driver_id", driverId)
            putExtra("interval_ms", config.movingIntervalSeconds * 1000L)
        }
        context.startForegroundService(intent)
    }

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        listOf(REQUEST_WAKE, REQUEST_SLEEP, REQUEST_POLLING).forEach { requestCode ->
            val intent = Intent(context, ShiftReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let { alarmManager.cancel(it) }
        }
    }

    private fun millisUntil(target: LocalTime): Long {
        val now = LocalTime.now()
        val secondsUntil = if (target.isAfter(now)) {
            now.until(target, ChronoUnit.SECONDS)
        } else {
            now.until(target, ChronoUnit.SECONDS) + 86_400
        }
        return secondsUntil * 1000L
    }
}