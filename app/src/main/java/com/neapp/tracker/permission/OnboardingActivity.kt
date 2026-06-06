package com.neapp.tracker.permission

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.neapp.tracker.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    private val checker by lazy { PermissionChecker(this) }
    private var aguardandoBateria = false

    companion object {
        const val EXTRA_DRIVER_ID = "driver_id"
    }

    // Passo 1a — Fine location
    private val requestFine = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            solicitarBackgroundLocation()
        } else {
            mostrarAviso("Sem localização, o rastreamento não funcionará com a tela bloqueada.")
            irParaPasso2()
        }
    }

    // Passo 1b — Background location (só pedida após fine ser concedida)
    private val requestBackground = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Concedida ou não — segue sempre
        irParaPasso2()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        iniciarPasso1()
    }

    override fun onResume() {
        super.onResume()
        if (aguardandoBateria) {
            aguardandoBateria = false
            exibirResumo()
        } else if (binding.summaryCard.visibility == View.VISIBLE) {
            // Atualiza o resumo caso o usuário tenha concedido permissão nas configurações
            exibirResumo()
        }
    }

    // ----- Passo 1: Localização -----

    private fun iniciarPasso1() {
        mostrarExplicacao(
            titulo = "Localização em segundo plano",
            corpo = "Para enviar sua posição com a tela bloqueada, " +
                    "selecione \"Permitir o tempo todo\" na próxima tela.",
            botao = "Entendido"
        ) {
            requestFine.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun solicitarBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            irParaPasso2()
        }
    }

    private fun irParaPasso2() {
        iniciarPasso2()
    }

    // ----- Passo 2: Bateria -----

    private fun iniciarPasso2() {
        if (checker.estaExcluidoDeBateria()) {
            exibirResumo()
            return
        }

        mostrarExplicacao(
            titulo = "Desempenho em segundo plano",
            corpo = "Para que o rastreamento não seja interrompido pelo celular, " +
                    "permita que o app rode sem restrições de bateria.",
            botao = "Configurar"
        ) {
            aguardandoBateria = true
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            ).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
            // onResume() detecta o retorno e chama exibirResumo()
        }
    }

    // ----- Resumo -----

    private fun exibirResumo() {
        binding.stepCard.visibility = View.GONE
        binding.warningText.visibility = View.GONE
        binding.summaryCard.visibility = View.VISIBLE

        val status = checker.resumo()

        // Item 1 — Localização
        if (status.backgroundLocation) {
            binding.summaryLocStatus.text = "✓ Concedida"
            binding.summaryLocStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            binding.summaryLocWarning.visibility = View.GONE
            binding.btnFixLocation.visibility = View.GONE
        } else {
            binding.summaryLocStatus.text = "✗ Não concedida"
            binding.summaryLocStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            binding.summaryLocWarning.visibility = View.VISIBLE
            binding.btnFixLocation.visibility = View.VISIBLE
            binding.btnFixLocation.setOnClickListener { abrirConfiguracoesLocalizacao() }
        }

        // Item 2 — Bateria
        if (status.bateria) {
            binding.summaryBatStatus.text = "✓ Concedida"
            binding.summaryBatStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            binding.summaryBatWarning.visibility = View.GONE
            binding.btnFixBattery.visibility = View.GONE
        } else {
            binding.summaryBatStatus.text = "✗ Não concedida"
            binding.summaryBatStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            binding.summaryBatWarning.visibility = View.VISIBLE
            binding.btnFixBattery.visibility = View.VISIBLE
            binding.btnFixBattery.setOnClickListener { iniciarPasso2() }
        }

        binding.btnContinue.setOnClickListener {
            iniciarRastreamento()
        }
    }

    private fun iniciarRastreamento() {
        getSharedPreferences("tracker_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("onboarding_done", true)
            .apply()
        setResult(RESULT_OK)
        finish()
    }

    private fun abrirConfiguracoesLocalizacao() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    // ----- Helpers de UI -----

    private fun mostrarAviso(msg: String) {
        binding.warningText.text = msg
        binding.warningText.visibility = View.VISIBLE
    }

    private fun mostrarExplicacao(titulo: String, corpo: String, botao: String, acao: () -> Unit) {
        binding.summaryCard.visibility = View.GONE
        binding.stepCard.visibility = View.VISIBLE
        binding.stepTitle.text = titulo
        binding.stepBody.text = corpo
        binding.stepButton.text = botao
        binding.stepButton.setOnClickListener { acao() }
    }
}
