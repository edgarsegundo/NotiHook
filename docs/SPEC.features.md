# Spec Técnica — Onboarding e Permissões
**Versão:** 1.1
**Referência:** App Android (Kotlin) · POC v0.5
**Objetivo:** Solicitar e monitorar as duas permissões essenciais para rastreamento contínuo em background, com auto-reinício do Service se o SO o matar.

---

## 1. As duas permissões que importam

| # | Permissão | Sem ela |
|---|---|---|
| 1 | `ACCESS_BACKGROUND_LOCATION` | GPS para com a tela bloqueada |
| 2 | Exclusão de otimização de bateria | Service pode ser morto pelo SO em qualquer fabricante |

Autostart por fabricante (MIUI, OneUI, etc.) foi avaliado e descartado — implementação não confiável, não verificável programaticamente, retorno incerto para a POC. O auto-reinício via `AlarmManager` cobre esse cenário de forma mais simples e robusta.

---

## 2. Fluxo de onboarding

Acontece uma única vez, logo após o entregador confirmar o ID pela primeira vez.

```
Entregador confirma ID
  → Passo 1: tela explicativa → solicita ACCESS_FINE_LOCATION
      → Se concedida: solicita ACCESS_BACKGROUND_LOCATION
      → Se negada: registra, exibe aviso, segue
  → Passo 2: tela explicativa → solicita exclusão de bateria
      → Diálogo nativo do Android
      → Concedida ou não: registra, segue
  → Tela de resumo: mostra status das duas permissões
  → Inicia rastreamento
```

O entregador nunca é bloqueado — avisa sobre as consequências e segue.

---

## 3. Implementação

### 3.1 `PermissionChecker`

Classe única que centraliza toda verificação de permissão. Nenhuma outra parte do app verifica permissão diretamente.

```kotlin
class PermissionChecker(private val context: Context) {

    fun temBackgroundLocation(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Android < 10: background já implícito no ACCESS_FINE_LOCATION
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

data class PermissionStatus(
    val backgroundLocation: Boolean,
    val bateria: Boolean
) {
    val tudoOk get() = backgroundLocation && bateria
}
```

---

### 3.2 `OnboardingActivity` — localização

O Android exige que `ACCESS_FINE_LOCATION` seja concedida antes de `ACCESS_BACKGROUND_LOCATION`. As duas não podem ser pedidas juntas — o sistema rejeita silenciosamente. A ordem é obrigatória.

```kotlin
class OnboardingActivity : AppCompatActivity() {

    private val checker by lazy { PermissionChecker(this) }
    private var aguardandoBateria = false

    // Step 1a — Fine location
    private val requestFine = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedida ->
        if (concedida) {
            solicitarBackgroundLocation()
        } else {
            // Avisa consequência mas não bloqueia
            mostrarAviso("Sem localização, o rastreamento não funcionará com a tela bloqueada.")
            irParaPasso2()
        }
    }

    // Step 1b — Background location
    private val requestBackground = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Concedida ou não — segue sempre
        irParaPasso2()
    }

    fun iniciarPasso1() {
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
            // Sem tela explicativa extra aqui — o diálogo do sistema
            // já deixa claro o que está sendo pedido
            requestBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            irParaPasso2()
        }
    }
}
```

---

### 3.3 `OnboardingActivity` — bateria

Essa permissão abre um diálogo nativo do Android — igual em todos os fabricantes, verificável, confiável. O usuário toca "Permitir" uma vez e está feito.

O resultado não vem via callback — o usuário sai para o diálogo do sistema e volta. A verificação acontece no `onResume`.

```kotlin
fun iniciarPasso2() {
    if (checker.estaExcluidoDeBateria()) {
        // Já concedida — pula direto para o resumo
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
        // onResume() vai detectar o retorno
    }
}

override fun onResume() {
    super.onResume()
    if (aguardandoBateria) {
        aguardandoBateria = false
        // Verifica se concedeu — concedida ou não, segue
        exibirResumo()
    }
}
```

---

### 3.4 Tela de resumo

Exibida ao final do onboarding e acessível a qualquer momento na tela principal. Cada item com problema tem um botão "Corrigir" que leva diretamente à configuração — sem repetir o onboarding inteiro.

```kotlin
fun exibirResumo() {
    val status = checker.resumo()

    // Item 1 — Localização
    renderizarItem(
        label = "Localização em segundo plano",
        ok = status.backgroundLocation,
        mensagemProblema = "O rastreamento para com a tela bloqueada.",
        acaoCorrigir = { abrirConfiguracoesLocalizacao() }
    )

    // Item 2 — Bateria
    renderizarItem(
        label = "Sem restrição de bateria",
        ok = status.bateria,
        mensagemProblema = "O rastreamento pode ser interrompido pelo celular.",
        acaoCorrigir = { iniciarPasso2() }
    )

    // Botão de continuar — sempre habilitado
    btnContinuar.setOnClickListener {
        iniciarRastreamento()
    }
}

private fun abrirConfiguracoesLocalizacao() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:$packageName")
    }
    startActivity(intent)
}
```

---

### 3.5 Verificação em aberturas subsequentes

Na `MainActivity`, `onResume` verifica silenciosamente se alguma permissão foi revogada desde a última vez. Exibe um banner discreto — não interrompe o fluxo.

```kotlin
// MainActivity.kt
override fun onResume() {
    super.onResume()

    val status = PermissionChecker(this).resumo()

    if (!status.tudoOk) {
        bannerAviso.visibility = View.VISIBLE
        bannerAviso.text = "Permissões incompletas podem afetar o rastreamento."
        bannerAviso.setOnClickListener {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
    } else {
        bannerAviso.visibility = View.GONE
    }
}
```

---

### 3.6 Auto-reinício do Service

Independente de qualquer permissão de fabricante, este mecanismo garante que se o SO matar o Service durante um turno ativo, ele tenta se reinicar em 5 segundos.

A flag `turno_ativo` em `SharedPreferences` controla se o reinício deve acontecer — evita reinício fora do horário de trabalho.

```kotlin
// LocationForegroundService.kt
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // Marca turno como ativo
    getSharedPreferences("tracker_prefs", MODE_PRIVATE)
        .edit()
        .putBoolean("turno_ativo", true)
        .apply()

    startForeground(NOTIFICATION_ID, buildNotification())
    // ... resto da inicialização
    return START_STICKY
}

override fun onDestroy() {
    val prefs = getSharedPreferences("tracker_prefs", MODE_PRIVATE)
    val turnoAtivo = prefs.getBoolean("turno_ativo", false)

    if (turnoAtivo) {
        // Agenda reinício em 5 segundos
        val intent = Intent(this, TurnoReceiver::class.java).apply {
            action = "ACTION_ACORDAR"
        }
        val pi = PendingIntent.getBroadcast(
            this, 99, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        getSystemService(AlarmManager::class.java).setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 5_000L,
            pi
        )
    }

    super.onDestroy()
}
```

Quando o turno encerra normalmente via `TurnoReceiver ACTION_DORMIR`, limpa a flag:

```kotlin
// TurnoReceiver.kt — dentro de ACTION_DORMIR
context.getSharedPreferences("tracker_prefs", MODE_PRIVATE)
    .edit()
    .putBoolean("turno_ativo", false)
    .apply()
context.stopService(Intent(context, LocationForegroundService::class.java))
```

---

## 4. AndroidManifest — permissões desta spec

```xml
<!-- Já existentes na spec 0.5 -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION"/>
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>

<!-- Nova nesta spec -->
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>

<application>
    <activity android:name=".permission.OnboardingActivity"/>
    <!-- demais declarações da spec 0.5 -->
</application>
```

---

## 5. Estrutura de pastas — arquivos novos

```
app/src/main/
├── java/com/seuapp/tracker/
│   ├── permission/
│   │   ├── PermissionChecker.kt
│   │   ├── PermissionStatus.kt
│   │   └── OnboardingActivity.kt
│   └── ... (demais arquivos da spec 0.5)
```

---

## 6. Comportamento por cenário

| Cenário | Comportamento |
|---|---|
| Todas as permissões concedidas | Sem avisos, rastreamento normal |
| Background location negada | Aviso na tela de resumo, botão corrigir, rastreamento continua |
| Bateria não excluída | Aviso na tela de resumo, botão corrigir, rastreamento continua |
| Permissão revogada depois | `onResume` detecta e exibe banner com botão para corrigir |
| SO mata o Service durante turno | Auto-reinício em 5s via AlarmManager |
| Entregador força parada manual | Sem reinício automático — precisa abrir o app manualmente |