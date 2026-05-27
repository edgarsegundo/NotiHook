package com.neapp.tracker.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.neapp.tracker.mqtt.MqttManager
import com.neapp.tracker.receiver.ShiftReceiver
import com.neapp.tracker.ui.StatusManager
import com.neapp.tracker.ui.TrackingStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LocationForegroundService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var intervalMs: Long = 15_000L
    private var driverId: String = ""

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "tracking_channel"
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        driverId = intent?.getStringExtra("driver_id") ?: ""
        intervalMs = intent?.getLongExtra("interval_ms", 15_000L) ?: 15_000L

        getSharedPreferences("tracker_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("turno_ativo", true)
            .apply()

        startForeground(NOTIFICATION_ID, buildNotification("Tracking active"))
        MqttManager.connect(this, driverId)
        startLocationUpdates()
        StatusManager.setStatus(TrackingStatus.ACTIVE)

        return START_STICKY
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            intervalMs
        ).setMinUpdateIntervalMillis(intervalMs / 2).build()

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
            StatusManager.setStatus(TrackingStatus.NO_SIGNAL)
            stopSelf()
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                if (MqttManager.isConnected()) {
                    StatusManager.setStatus(TrackingStatus.ACTIVE)
                    updateNotification("Tracking active — GPS ✓")
                } else {
                    StatusManager.setStatus(TrackingStatus.NO_SIGNAL)
                    updateNotification("No signal — reconnecting...")
                }
                serviceScope.launch {
                    MqttManager.publish(driverId, location)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Location Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active while tracking is running"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NeApp Tracker")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        StatusManager.setStatus(TrackingStatus.SLEEPING)
        MqttManager.disconnect()

        val prefs = getSharedPreferences("tracker_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("turno_ativo", false)) {
            val restartIntent = Intent(this, ShiftReceiver::class.java).apply {
                action = ShiftReceiver.ACTION_WAKE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                this, 99, restartIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            getSystemService(AlarmManager::class.java).setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 5_000L,
                pendingIntent
            )
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}