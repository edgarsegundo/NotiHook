# neapp

# Spec Técnica — App Android (Kotlin) · POC Rastreamento de Entregadores

**Versão:** 0.2 · POC  
**Plataforma:** Android (API 26+, Android 8.0 Oreo em diante)  
**Linguagem:** Kotlin  
**Objetivo:** Rastrear localização GPS dos entregadores em background e transmitir ao servidor central via MQTT.

---

## 1. Visão geral

O app é intencionalmente mínimo. Faz uma coisa: **coleta GPS e transmite enquanto o entregador estiver em turno**, mesmo com tela bloqueada, bateria fraca ou sinal ruim. Menos camadas entre o código e o sistema operacional significa mais confiabilidade — por isso Kotlin nativo, não React Native ou Flutter.

---

## 2. Funcionalidades do escopo da POC

| # | Funcionalidade | Prioridade |
|---|---|---|
| 1 | Login por nome ou ID simples (sem auth complexa) | Must |
| 2 | Botão "Iniciar turno" — ativa rastreamento | Must |
| 3 | Botão "Encerrar turno" — para rastreamento | Must |
| 4 | GPS em background com notificação persistente | Must |
| 5 | Envio de coordenadas via MQTT a cada 10s | Must |
| 6 | Reconexão automática em caso de queda de rede | Must |
| 7 | Indicador visual de status (conectado / sem sinal) | Should |
| 8 | Funcionamento offline com retry ao reconectar | Should |

**Fora do escopo da POC:** mapa no app, histórico de rotas, navegação, notificações de corrida, pagamento, reinício automático após reboot.

---

## 3. Por que MQTT e não HTTP (Retrofit/OkHttp)

Essa decisão foi tomada explicitamente e merece registro na spec.

**Retrofit e OkHttp** são excelentes para chamadas HTTP request-response — APIs REST, upload de dados, consultas pontuais. Para a POC seriam funcionais. Mas apresentam desvantagens concretas para rastreamento contínuo:

- Cada envio abre e fecha uma conexão TCP (overhead)
- Sem reconexão automática nativa
- Payload maior (headers HTTP em cada requisição)
- Bateria: cada request acorda o rádio do celular do zero

**MQTT** foi projetado exatamente para o cenário de vocês: dispositivos móveis, conexão instável, envio frequente de dados pequenos. A conexão é persistente, o rádio fica aquecido com custo mínimo, e o broker gerencia reconexão. É o protocolo que Uber, iFood e similares usam em produção para rastreamento de frota. O SDK nativo para Android é o HiveMQ MQTT Client — sem wrappers, sem intermediários.

A decisão: **MQTT com HiveMQ SDK nativo. Retrofit descartado para esse caso de uso.**

---

## 4. Arquitetura de componentes

### 4.1 `MainActivity`
Ponto de entrada. Responsável por:
- Tela de login (campo de nome/ID do entregador)
- Botões de iniciar/encerrar turno
- Solicitar permissões de localização em runtime
- Iniciar/parar o `LocationForegroundService` via `startForegroundService()`

### 4.2 `LocationForegroundService`
O coração do app. É um `Service` Android com `foregroundServiceType="location"`. A decisão crítica aqui é usar `FusedLocationProviderClient` — não o `LocationManager` legado.

**Por que não `LocationManager`:** é a API antiga do Android, sem otimização de bateria, sem fusão de fontes (GPS + Wi-Fi + rede celular) e com comportamento inconsistente entre versões do Android. O `FusedLocationProviderClient` do Google Play Services resolve tudo isso nativamente.

```kotlin
class LocationForegroundService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mqttManager: MqttManager

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mqttManager = MqttManager.getInstance()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        startLocationUpdates()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10_000L
        ).setMinUpdateIntervalMillis(5_000L).build()

        fusedLocationClient.requestLocationUpdates(
            request,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                // Delega para o MqttManager via coroutine
                ServiceScope.launch {
                    mqttManager.publish(location)
                }
            }
        }
    }

    override fun onDestroy() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        mqttManager.disconnect()
        super.onDestroy()
    }
}
```

**Por que `START_STICKY`:** se o Android matar o Service por pressão de memória, o sistema o reinicia automaticamente assim que possível.

### 4.3 Coroutines — o loop de rastreamento sem callback hell

Kotlin Coroutines são centrais para manter o código limpo nesse tipo de operação contínua. A alternativa em Java seria threads manuais e encadeamento de callbacks — funcional mas frágil de manter.

```kotlin
// Dentro do MqttManager — loop de publicação
fun startPublishing(locationFlow: Flow<Location>) {
    serviceScope.launch {
        locationFlow.collect { location ->
            try {
                val payload = LocationPayload(
                    id = entregadorId,
                    lat = location.latitude,
                    lng = location.longitude,
                    ts = System.currentTimeMillis() / 1000,
                    speed = location.speed
                )
                mqttClient.publish(
                    topic = "entregadores/$entregadorId/location",
                    payload = Json.encodeToString(payload).toByteArray(),
                    qos = MqttQos.AT_LEAST_ONCE
                )
            } catch (e: Exception) {
                // MqttManager trata reconexão — não deixa vazar exceção aqui
            }
        }
    }
}
```

O `serviceScope` é um `CoroutineScope` vinculado ao ciclo de vida do Service — quando o Service é destruído, todas as coroutines são canceladas automaticamente, sem memory leak.

### 4.4 `MqttManager`
Singleton responsável por toda a comunicação com o broker. Usa o **HiveMQ MQTT Client SDK** — escolhido por ser o SDK mais ativo para Android/JVM, com suporte nativo a TLS, reconexão automática e API moderna baseada em coroutines.

```kotlin
object MqttManager {

    private lateinit var client: Mqtt3AsyncClient

    fun connect(host: String, port: Int, clientId: String) {
        client = MqttClient.builder()
            .useMqttVersion3()
            .serverHost(host)
            .serverPort(port)
            .identifier(clientId)
            .sslWithDefaultConfig() // TLS automático
            .automaticReconnect()
                .initialDelay(1, TimeUnit.SECONDS)
                .maxDelay(30, TimeUnit.SECONDS)
                .applyAutomaticReconnect()
            .buildAsync()

        client.connectWith()
            .keepAlive(60)
            .send()
    }

    suspend fun publish(location: Location) { ... }
    fun disconnect() { client.disconnect() }
}
```

**Reconexão com backoff exponencial:** começa tentando reconectar em 1s, dobra o intervalo a cada falha até o máximo de 30s. Evita bombardear o broker em caso de queda de rede.

### 4.5 `LocationPayload`
Modelo de dados do payload enviado ao broker:

```kotlin
@Serializable
data class LocationPayload(
    val id: String,
    val lat: Double,
    val lng: Double,
    val ts: Long,        // Unix timestamp em segundos
    val speed: Float,    // m/s — útil para detectar entregador parado
    val stale: Boolean = false  // true se GPS sem sinal
)
```

Payload JSON resultante por envio — aproximadamente 120 bytes:

```json
{
  "id": "motoboy_42",
  "lat": -23.5505,
  "lng": -46.6333,
  "ts": 1718200000,
  "speed": 8.3,
  "stale": false
}
```

### 4.6 `AndroidManifest.xml`
Permissões necessárias — declaradas no manifest e solicitadas em runtime:

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION"/>
<uses-permission android:name="android.permission.INTERNET"/>

<application>
    <service
        android:name=".service.LocationForegroundService"
        android:foregroundServiceType="location"
        android:exported="false"/>
</application>
```

`ACCESS_BACKGROUND_LOCATION` exige fluxo especial desde Android 11: o usuário precisa ir em Configurações e selecionar "Sempre permitir". O app deve ter uma tela explicativa guiando esse passo — sem ela, a maioria dos usuários não sabe o que fazer.

---

## 5. Dependências (Gradle)

```kotlin
dependencies {
    // GPS — FusedLocationProviderClient (Google Play Services)
    implementation("com.google.android.gms:play-services-location:21.2.0")

    // MQTT — HiveMQ SDK nativo Android/JVM
    // Decisão: descarta Retrofit/OkHttp para esse caso de uso
    // Motivo: conexão persistente, menor consumo de bateria, reconexão nativa
    implementation("com.hivemq:hivemq-mqtt-client:1.3.3")

    // Coroutines — async sem callback hell
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Serialização do payload JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Lifecycle — vincula o CoroutineScope ao ciclo de vida do Service
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
}
```

---

## 6. Integração com o backend — como o Redis usa os dados

O backend recebe cada payload MQTT e atualiza a posição do entregador no Redis usando `GEOADD`:

```bash
# Atualiza posição do entregador (substitui coordenada anterior)
GEOADD entregadores -46.6333 -23.5505 "motoboy_42"

# Quando um pedido é criado — busca entregadores num raio de 3km, ordenados por distância
GEOSEARCH entregadores FROMMEMBER "loja_centro" BYRADIUS 3 km ASC
```

Isso retorna os entregadores mais próximos em menos de 1ms, independente de quantos estejam cadastrados. É exatamente como o iFood resolve o problema de despacho. O app Kotlin não precisa saber disso — ele só publica. O backend cuida do resto.

---

## 7. Fluxo completo

```
Usuário abre o app
  → Digita nome/ID do entregador
  → Toca "Iniciar turno"
      → App solicita permissão ACCESS_FINE_LOCATION (runtime)
      → App solicita ACCESS_BACKGROUND_LOCATION (tela explicativa)
      → startForegroundService(LocationForegroundService)
          → Service cria notificação persistente obrigatória
          → FusedLocationProviderClient registra LocationCallback
          → MqttManager.connect() → broker EMQX via TLS
          → A cada 10s: LocationCallback → coroutine → MqttManager.publish()
              → Broker recebe → Backend Node.js → Redis GEOADD
              → Dashboard web atualiza mapa via WebSocket
  → Usuário toca "Encerrar turno"
      → FusedLocationProviderClient.removeLocationUpdates()
      → MqttManager.disconnect()
      → stopForeground() → notificação removida
```

---

## 8. Tratamento de estados críticos

| Situação | Comportamento |
|---|---|
| App vai para background | Service continua — Foreground Service não pode ser morto pelo SO |
| Tela bloqueada | GPS continua sendo coletado normalmente |
| Perda de rede | MqttManager aguarda reconexão com backoff exponencial (1s → 30s) |
| GPS sem sinal (túnel, garagem) | Último ponto conhecido mantido; `stale: true` no payload |
| Android mata o Service por memória | `START_STICKY` garante reinício automático pelo SO |
| Celular reiniciado | App não reinicia — limitação conhecida da POC (fase 2: `RECEIVE_BOOT_COMPLETED`) |
| ROMs customizadas (MIUI, OneUI) | Podem matar processos agressivamente — documentar como risco operacional da POC |

---

## 9. Estrutura de pastas

```
app/src/main/
├── java/com/seuapp/tracker/
│   ├── MainActivity.kt
│   ├── service/
│   │   └── LocationForegroundService.kt
│   ├── mqtt/
│   │   └── MqttManager.kt
│   ├── model/
│   │   └── LocationPayload.kt
│   └── ui/
│       ├── LoginScreen.kt
│       └── StatusScreen.kt
└── AndroidManifest.xml
```

---

## 10. Configuração do broker MQTT (POC)

| Parâmetro | Valor |
|---|---|
| Broker | EMQX Cloud — plano gratuito (até 1.000 conexões) |
| Host | `seu-cluster.emqxsl.com` |
| Porta | `8883` (TLS obrigatório) |
| Protocolo | MQTT 3.1.1 |
| Topic | `entregadores/{id}/location` |
| QoS | 1 — at least once (garante entrega sem duplicação excessiva) |
| Keep-alive | 60s |
| Reconexão | Backoff exponencial 1s → 30s |

---

## 11. Critérios de sucesso da POC

A POC está validada quando:

1. APK instalado em pelo menos 5 dispositivos Android diferentes funciona sem configuração manual
2. Coordenadas chegam ao Redis em menos de 15 segundos em condições normais de rede
3. `GEOSEARCH` retorna entregadores corretos num raio configurável a partir da loja
4. Rastreamento sobrevive a pelo menos 4 horas contínuas sem interrupção do Service
5. Reconexão após queda de rede ocorre automaticamente sem intervenção do entregador

---

## 12. O que vem depois — fase 2

Itens deliberadamente fora da POC que entram na próxima fase:

- Reinício automático após reboot (`RECEIVE_BOOT_COMPLETED`)
- Batching de coordenadas offline com `WorkManager`
- Notificação de corrida disponível recebida pelo app (subscribe MQTT bidirecional)
- Aceite de corrida dentro do app (elimina o WhatsApp)
- Score e reputação do entregador
- Otimização de bateria com intervalo dinâmico (10s em movimento, 30s parado)
