package com.neapp.tracker

import android.Manifest
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.neapp.tracker.api.ApiService
import com.neapp.tracker.databinding.ActivityMainBinding
import com.neapp.tracker.scheduler.SchedulerManager
import com.neapp.tracker.service.LocationForegroundService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val prefs by lazy {
        getSharedPreferences("tracker_prefs", MODE_PRIVATE)
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getStringExtra(LocationForegroundService.EXTRA_STATUS)
            android.util.Log.d("StatusReceiver", "Received status: $status")
            updateStatusUI(status)
        }
    }

    private val backgroundLocationRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            confirmDriverId()
        } else {
            Toast.makeText(
                this,
                "Background location is required for tracking",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val fineLocationRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true

        if (!fineGranted) {
            Toast.makeText(this, "Location permission is required", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val backgroundGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!backgroundGranted) {
                Toast.makeText(
                    this,
                    "Please allow location access 'All the time' on the next screen",
                    Toast.LENGTH_LONG
                ).show()
                backgroundLocationRequest.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                confirmDriverId()
            }
        } else {
            confirmDriverId()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val savedId = prefs.getString("driver_id", "")
        binding.editDriverId.setText(savedId)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                registerForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        binding.btnConfirm.setOnClickListener {
            val id = binding.editDriverId.text.toString().trim()
            if (id.isEmpty()) {
                Toast.makeText(this, "Enter your driver ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putString("driver_id", id).apply()
            requestPermissionsIfNeeded()
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(LocationForegroundService.ACTION_STATUS_UPDATE)
        ContextCompat.registerReceiver(
            this,
            statusReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(statusReceiver)
    }

    private fun updateStatusUI(status: String?) {
        when (status) {
            LocationForegroundService.STATUS_ACTIVE -> {
                binding.statusText.text = "● Tracking active"
                binding.statusText.setTextColor(getColor(android.R.color.holo_green_dark))
            }
            LocationForegroundService.STATUS_NO_SIGNAL -> {
                binding.statusText.text = "● No signal"
                binding.statusText.setTextColor(getColor(android.R.color.holo_red_dark))
            }
            else -> {
                binding.statusText.text = "● Sleeping"
                binding.statusText.setTextColor(getColor(android.R.color.darker_gray))
            }
        }
    }

    private fun requestPermissionsIfNeeded() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted) {
            fineLocationRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val backgroundGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!backgroundGranted) {
                Toast.makeText(
                    this,
                    "Please allow location access 'All the time' on the next screen",
                    Toast.LENGTH_LONG
                ).show()
                backgroundLocationRequest.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                return
            }
        }

        confirmDriverId()
    }

    private fun confirmDriverId() {
        val id = prefs.getString("driver_id", "") ?: return
        binding.btnConfirm.isEnabled = false
        binding.statusText.text = "Fetching config..."

        lifecycleScope.launch {
            try {
                val config = ApiService.create().getConfig(id)
                prefs.edit()
                    .putString("shift_start", config.shiftStart)
                    .putString("shift_end", config.shiftEnd)
                    .putInt("moving_interval_seconds", config.movingIntervalSeconds)
                    .putInt("idle_interval_seconds", config.idleIntervalSeconds)
                    .apply()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val alarmManager = getSystemService(AlarmManager::class.java)
                    if (!alarmManager.canScheduleExactAlarms()) {
                        binding.statusText.text = "Shift: ${config.shiftStart} → ${config.shiftEnd}"
                        binding.btnConfirm.isEnabled = true
                        Toast.makeText(
                            this@MainActivity,
                            "Please allow 'Alarms & Reminders' permission in Settings",
                            Toast.LENGTH_LONG
                        ).show()
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                        )
                        startActivity(intent)
                        return@launch
                    }
                }

                SchedulerManager.schedule(applicationContext, config)
                binding.statusText.text = "Shift: ${config.shiftStart} → ${config.shiftEnd}"
                Toast.makeText(
                    this@MainActivity,
                    "Scheduled successfully",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                binding.statusText.text = "Error fetching config"
                Toast.makeText(
                    this@MainActivity,
                    "Could not reach server: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                binding.btnConfirm.isEnabled = true
            }
        }
    }
}