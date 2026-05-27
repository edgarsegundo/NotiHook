package com.neapp.tracker

import android.Manifest
import android.app.AlarmManager
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.neapp.tracker.api.ApiService
import com.neapp.tracker.databinding.ActivityMainBinding
import com.neapp.tracker.delivery.DeliveryOfferManager
import com.neapp.tracker.model.DeliveryOffer
import com.neapp.tracker.permission.OnboardingActivity
import com.neapp.tracker.permission.PermissionChecker
import com.neapp.tracker.scheduler.SchedulerManager
import com.neapp.tracker.ui.StatusManager
import com.neapp.tracker.ui.TrackingStatus
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val prefs by lazy {
        getSharedPreferences("tracker_prefs", MODE_PRIVATE)
    }

    private var lastShownOfferEventId: String? = null

    private val onboardingLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
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

        // Observa o StatusFlow — atualiza a UI automaticamente
        lifecycleScope.launch {
            StatusManager.status.collect { status ->
                updateStatusUI(status)
            }
        }

        lifecycleScope.launch {
            DeliveryOfferManager.latestOffer.collect { offer ->
                if (offer != null) {
                    showOfferDialogIfNeeded(offer)
                }
            }
        }

        // Se já tem ID salvo e onboarding feito, busca config e agenda automaticamente
        val onboardingDone = prefs.getBoolean("onboarding_done", false)
        if (!savedId.isNullOrEmpty() && onboardingDone) {
            confirmDriverId()
        }

        binding.btnConfirm.text = "Atualizar"
        binding.btnConfirm.setOnClickListener {
            val id = binding.editDriverId.text.toString().trim()
            if (id.isEmpty()) {
                Toast.makeText(this, "Enter your driver ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putString("driver_id", id).apply()
            val onboardingDone = prefs.getBoolean("onboarding_done", false)
            if (!onboardingDone) {
                onboardingLauncher.launch(Intent(this, OnboardingActivity::class.java))
            } else {
                confirmDriverId()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val onboardingDone = prefs.getBoolean("onboarding_done", false)
        if (!onboardingDone) return

        val status = PermissionChecker(this).resumo()
        if (!status.tudoOk) {
            binding.bannerAviso.visibility = View.VISIBLE
            binding.bannerAviso.text = "Permissões incompletas podem afetar o rastreamento."
            binding.bannerAviso.setOnClickListener {
                startActivity(Intent(this, OnboardingActivity::class.java))
            }
        } else {
            binding.bannerAviso.visibility = View.GONE
        }
    }

    private fun updateStatusUI(status: TrackingStatus) {
        when (status) {
            TrackingStatus.ACTIVE -> {
                binding.statusText.text = "● Tracking active"
                binding.statusText.setTextColor(getColor(android.R.color.holo_green_dark))
            }
            TrackingStatus.NO_SIGNAL -> {
                binding.statusText.text = "● No signal"
                binding.statusText.setTextColor(getColor(android.R.color.holo_red_dark))
            }
            TrackingStatus.SLEEPING -> {
                binding.statusText.text = "● Sleeping"
                binding.statusText.setTextColor(getColor(android.R.color.darker_gray))
            }
        }
    }

    private fun showOfferDialogIfNeeded(offer: DeliveryOffer) {
        if (lastShownOfferEventId == offer.eventId || isFinishing || isDestroyed) return
        lastShownOfferEventId = offer.eventId

        val body = buildString {
            append(offer.message ?: "Nova entrega disponivel")
            offer.pickup?.label?.let { append("\nOrigem: $it") }
            offer.dropoff?.label?.let { append("\nDestino: $it") }
            offer.distanceKm?.let { append("\nDistancia: ${String.format("%.1f", it)} km") }
            offer.payout?.let { append("\nValor: R$ ${String.format("%.2f", it)}") }
        }

        AlertDialog.Builder(this)
            .setTitle("Nova entrega")
            .setMessage(body)
            .setPositiveButton("Ver oferta") { dialog, _ -> dialog.dismiss() }
            .setNegativeButton("Fechar") { dialog, _ -> dialog.dismiss() }
            .show()
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