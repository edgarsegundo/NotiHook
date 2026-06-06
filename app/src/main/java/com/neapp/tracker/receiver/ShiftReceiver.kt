package com.neapp.tracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.neapp.tracker.api.ApiService
import com.neapp.tracker.model.DeliveryConfig
import com.neapp.tracker.service.LocationForegroundService
import com.neapp.tracker.scheduler.SchedulerManager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime

class ShiftReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NeAppTracker"
        const val ACTION_WAKE = "com.neapp.tracker.ACTION_WAKE"
        const val ACTION_SLEEP = "com.neapp.tracker.ACTION_SLEEP"
        const val ACTION_CHECK_CONFIG = "com.neapp.tracker.ACTION_CHECK_CONFIG"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("tracker_prefs", Context.MODE_PRIVATE)
        val driverId = prefs.getString("driver_id", "") ?: return

        when (intent.action) {
            ACTION_WAKE -> {
                Log.d(TAG, "ShiftReceiver → ACTION_WAKE")
                fetchConfigAndStart(context, driverId)
            }
            ACTION_SLEEP -> {
                Log.d(TAG, "ShiftReceiver → ACTION_SLEEP")
                context.getSharedPreferences("tracker_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("turno_ativo", false)
                    .apply()
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
                Log.d(TAG, "ShiftReceiver → ACTION_CHECK_CONFIG")
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
                val savedMoving = prefs.getInt("moving_interval_seconds", -1)

                Log.d(TAG, "CHECK_CONFIG — server: start=${config.shiftStart} end=${config.shiftEnd} interval=${config.movingIntervalSeconds}")
                Log.d(TAG, "CHECK_CONFIG — local:  start=$savedStart end=$savedEnd interval=$savedMoving")
                Log.d(TAG, "CHECK_CONFIG — device time: ${LocalTime.now()}")

                val scheduleChanged = config.shiftStart != savedStart || config.shiftEnd != savedEnd
                val intervalChanged = config.movingIntervalSeconds != savedMoving

                if (scheduleChanged || intervalChanged) {
                    Log.d(TAG, "CHECK_CONFIG → config changed, rescheduling")
                    saveConfig(context, config)
                    SchedulerManager.cancelAll(context)

                    // Para o service se o horário atual ficou fora da janela do turno
                    val now = LocalTime.now()
                    val start = LocalTime.parse(config.shiftStart)
                    val end = LocalTime.parse(config.shiftEnd)
                    val outsideShift = now.isBefore(start) || !now.isBefore(end)
                    if (outsideShift) {
                        Log.d(TAG, "CHECK_CONFIG → now ($now) fora da janela [$start, $end), parando service")
                        prefs.edit().putBoolean("turno_ativo", false).apply()
                        context.stopService(Intent(context, LocationForegroundService::class.java))
                    }

                    SchedulerManager.schedule(context, config)
                } else {
                    Log.d(TAG, "CHECK_CONFIG → sem mudancas")
                }
            } catch (e: Exception) {
                Log.e(TAG, "CHECK_CONFIG → erro na API: ${e.message}")
                e.printStackTrace()
            } finally {
                // Encadeia o próximo polling independente de sucesso ou erro
                SchedulerManager.schedulePolling(context)
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