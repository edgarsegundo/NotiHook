# NeApp — Mapa do Projeto

## Visão geral

Sistema de rastreamento de entregadores em tempo real composto por:
- App Android (Kotlin) que coleta GPS e publica via MQTT
- Backend Django que serve config, recebe GPS e armazena no Redis GEO
- Broker EMQX que recebe as mensagens MQTT
- Dashboard web para monitoramento (a implementar — ver DASHBOARD_SPEC.md)

---

## Repositórios

| Repo | Path na VPS | Função |
|---|---|---|
| `neapp/` | `~/Repos/neapp` | App Android Kotlin |
| `microservicesadm/` | `~/Repos/microservicesadm` | Backend Django |
| `reverse-proxy-config/` | `~/Repos/reverse-proxy-config` | Nginx + Docker |

---

## App Android — `neapp/`

```
app/src/main/
├── java/com/neapp/tracker/
│   ├── MainActivity.kt
│   │   — Tela principal. Campo de driver ID, consulta API, agenda turno.
│   │     Ao confirmar ID pela primeira vez: lança OnboardingActivity.
│   │     onResume: verifica permissões via PermissionChecker e exibe
│   │     bannerAviso (laranja) se alguma foi revogada, com link para
│   │     OnboardingActivity.
│   │
│   ├── permission/                          ← NOVO (spec 1.1)
│   │   ├── PermissionStatus.kt
│   │   │   — Data class: backgroundLocation, bateria, tudoOk.
│   │   ├── PermissionChecker.kt
│   │   │   — Centraliza toda verificação de permissão.
│   │   │     temBackgroundLocation(): false se API >= Q e não concedida.
│   │   │     estaExcluidoDeBateria(): PowerManager.isIgnoringBatteryOptimizations.
│   │   │     resumo(): retorna PermissionStatus.
│   │   └── OnboardingActivity.kt
│   │       — Fluxo único pós-confirmação de ID.
│   │         Passo 1: explica → pede ACCESS_FINE_LOCATION →
│   │           se concedida: pede ACCESS_BACKGROUND_LOCATION.
│   │           se negada: mostra aviso amarelo, segue.
│   │         Passo 2: explica → abre diálogo nativo de bateria
│   │           (Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).
│   │           onResume detecta retorno e exibe resumo.
│   │         Resumo: mostra status de cada permissão com botão
│   │           "Corrigir" individual. Botão "Iniciar" sempre habilitado.
│   │           Ao continuar: grava onboarding_done=true, retorna RESULT_OK.
│   │
│   ├── model/
│   │   ├── LocationPayload.kt
│   │   │   — Data class serializada para JSON enviado via MQTT.
│   │   │     Campos: id, lat, lng, ts, speed.
│   │   └── DeliveryConfig.kt
│   │       — Data class com config do turno retornada pela API.
│   │         Campos: shiftStart, shiftEnd, movingIntervalSeconds, idleIntervalSeconds.
│   │
│   ├── api/
│   │   └── ApiService.kt
│   │       — Interface Retrofit. Endpoint GET /api/tracker/drivers/{id}/config/.
│   │         Envia header X-API-Key em toda requisição.
│   │         BASE_URL aponta para https://sys.fastvistos.com.br/
│   │
│   ├── mqtt/
│   │   └── MqttManager.kt
│   │       — Singleton. Conecta no EMQX via TLS porta 8883.
│   │         Publica LocationPayload no tópico drivers/{id}/location.
│   │         Reconexão automática com backoff 1s→30s.
│   │
│   ├── scheduler/
│   │   └── SchedulerManager.kt
│   │       — Singleton. Avalia horário atual vs turno configurado.
│   │         Agenda AlarmManager para acordar/dormir nos horários certos.
│   │         Agenda polling de config a cada 1 hora.
│   │
│   ├── receiver/
│   │   ├── ShiftReceiver.kt
│   │   │   — BroadcastReceiver. Recebe ACTION_WAKE, ACTION_SLEEP,
│   │   │     ACTION_CHECK_CONFIG do AlarmManager.
│   │   │     No WAKE: busca config na API e inicia LocationForegroundService.
│   │   │     No SLEEP: grava turno_ativo=false, para o service,
│   │   │       reagenda para o dia seguinte.
│   │   │     No CHECK_CONFIG: compara config do servidor com a local,
│   │   │       reagenda se mudou.
│   │   └── BootReceiver.kt
│   │       — BroadcastReceiver. Escuta BOOT_COMPLETED.
│   │         Ao reiniciar o celular, lê config salva em SharedPreferences
│   │         e reagenda o AlarmManager automaticamente.
│   │
│   ├── service/
│   │   └── LocationForegroundService.kt
│   │       — ForegroundService. Roda durante o turno.
│   │         onStartCommand: grava turno_ativo=true em SharedPreferences.
│   │         onDestroy: se turno_ativo=true, agenda reinício em 5s via
│   │           AlarmManager (ACTION_WAKE no ShiftReceiver) — auto-reinício
│   │           caso o SO mate o service durante o turno.
│   │         Coleta GPS via FusedLocationProviderClient a cada intervalMs.
│   │         Publica via MqttManager a cada update.
│   │
│   └── ui/
│       └── StatusManager.kt
│           — Singleton com StateFlow<TrackingStatus>.
│             Estados: ACTIVE (verde), SLEEPING (cinza), NO_SIGNAL (vermelho).
│             MainActivity observa esse flow para atualizar a UI.
│
├── res/layout/
│   ├── activity_main.xml
│   │   — Layout: bannerAviso (TextView laranja, gone por padrão),
│   │     EditText (driver ID), Button (Confirm), TextView (statusText).
│   └── activity_onboarding.xml      ← NOVO (spec 1.1)
│       — stepCard: título + corpo + botão (reutilizado nos passos 1 e 2).
│         summaryCard: dois itens (localização, bateria) cada um com
│         status colorido, mensagem de problema e botão "Corrigir";
│         botão "Iniciar rastreamento" sempre habilitado.
│
└── AndroidManifest.xml
    — Permissões: ACCESS_FINE_LOCATION, ACCESS_BACKGROUND_LOCATION,
      FOREGROUND_SERVICE, FOREGROUND_SERVICE_LOCATION, INTERNET,
      RECEIVE_BOOT_COMPLETED, SCHEDULE_EXACT_ALARM, POST_NOTIFICATIONS,
      REQUEST_IGNORE_BATTERY_OPTIMIZATIONS.            ← NOVA (spec 1.1)
      Declara LocationForegroundService, ShiftReceiver, BootReceiver,
      OnboardingActivity.                              ← NOVA (spec 1.1)
```

**Arquivos de build:**
```
gradle/libs.versions.toml   — Version catalog com todas as dependências
app/build.gradle.kts        — Config do módulo app. BuildConfig com API_KEY hardcoded.
                              viewBinding=true, buildConfig=true.
```

**Dependências principais:**
- `com.google.android.gms:play-services-location` — FusedLocationProviderClient
- `com.hivemq:hivemq-mqtt-client` — MQTT com TLS nativo
- `com.squareup.retrofit2:retrofit` — HTTP para polling de config
- `org.jetbrains.kotlinx:kotlinx-serialization-json` — serialização do payload MQTT
- `org.jetbrains.kotlinx:kotlinx-coroutines-android` — coroutines

---

## Backend Django — `microservicesadm/tracker/`

```
tracker/
├── models.py
│   — Model Driver(BusinessModel).
│     Campos: driver_code (unique), full_name, phone, is_active,
│     shift_start, shift_end, moving_interval_seconds, idle_interval_seconds.
│     driver_code é o ID que o app Android usa.
│     BusinessModel fornece: business (FK), created, modified.
│
├── views.py
│   — DriverConfigView: GET /api/tracker/drivers/{driver_code}/config/
│     Retorna JSON com shift_start, shift_end, moving_interval_seconds,
│     idle_interval_seconds. Protegida pelo ApiKeyMiddleware.
│   — NearbyDriversView: GET /api/tracker/drivers/nearby/
│     Recebe lat, lng, radius_km. Faz GEOSEARCH no Redis.
│     Retorna lista de entregadores próximos com distância e coordenadas.
│
├── urls.py
│   — Rotas da API do tracker registradas sob /api/ no urls.py principal.
│
├── tasks.py
│   — process_location(payload): recebe {id, lat, lng, ts, speed},
│     faz GEOADD no Redis GEO (chave 'drivers', db 1),
│     grava driver:{id}:last_seen com TTL 300s.
│   — find_nearby_drivers(lng, lat, radius_km): GEOSEARCH no Redis.
│     Ambas na fila Celery 'tracker'.
│
├── mqtt_consumer.py
│   — Conecta no EMQX via emqx:1883 (interno Docker, sem TLS).
│     Subscribe em drivers/+/location.
│     Ao receber mensagem: process_location.delay(payload).
│     Loop com retry automático e backoff exponencial.
│
├── admin.py
│   — DriverAdmin com Unfold. list_display, search, fieldsets.
│
└── management/commands/
    └── start_mqtt_consumer.py
        — Management command Django que inicia o mqtt_consumer.
          Rodado pelo container worker_tracker_consumer.
```

**Settings relevantes (`microservicesadm/settings.py`):**
```python
TRACKER_REDIS_URL = 'redis://redis_tracker:6379/1'  # db 1
TRACKER_MAP_CENTER_LAT = -22.9068   # Campinas default
TRACKER_MAP_CENTER_LNG = -47.0626   # Campinas default
TRACKER_DRIVER_TTL_SECONDS = 300    # 5 minutos
API_KEY = os.environ.get('API_KEY') # validado pelo ApiKeyMiddleware
```

**Settings do worker (`microservicesadm/settings_worker_tracker.py`):**
```python
# Herda settings.py principal
# INSTALLED_APPS mínimo: contenttypes, auth, business, tracker
# ROOT_URLCONF vazio
# Sem UNFOLD, sem MIDDLEWARE
```

---

## Infraestrutura — VPS

### Docker Compose (`microservicesadm/docker-compose.yml`)

| Container | Função | Porta externa |
|---|---|---|
| `microservicesadm` | Django + Gunicorn | 8000 |
| `emqx` | Broker MQTT | 1883, 18083 (dashboard) |
| `redis_tracker` | Redis do tracker | 6381 |
| `worker_tracker_consumer` | MQTT consumer | — |
| `worker_tracker_celery` | Celery fila tracker | — |
| `redis_ds160` | Redis de outro app | 6380 |
| `worker_ds160` | Celery outro app | — |

Todos na rede Docker `proxy-network` (nginx-proxy-network).

### Nginx (`reverse-proxy-config/`)

| Arquivo | Função |
|---|---|
| `nginx.conf` | Config principal. Bloco `stream {}` para proxy TCP MQTT. |
| `sites/010-microservicesadm.conf` | Proxy HTTP/HTTPS para Django. |
| `stream.d/mqtt.conf` | Proxy TCP TLS porta 8883 → emqx:1883. |

**Rotas expostas:**
- `https://sys.fastvistos.com.br/api/` → Django (protegida por X-API-Key)
- `https://sys.fastvistos.com.br/admin/` → Django admin
- `ssl://sys.fastvistos.com.br:8883` → EMQX via TLS

### Redis GEO — estrutura de dados

```
# DB 1 (TRACKER_REDIS_URL)

# Posição atual — sobrescrita a cada update GPS
GEOADD drivers <lng> <lat> <driver_code>

# Online/offline — expira em 5 minutos sem update
SET driver:<driver_code>:last_seen 1 EX 300

# Queries úteis:
GEOPOS drivers edgarsegundo                          # posição atual
ZRANGE drivers 0 -1                                  # todos os members
EXISTS driver:edgarsegundo:last_seen                 # está online?
GEOSEARCH drivers FROMLONLAT <lng> <lat> \
  BYRADIUS 3 km ASC WITHCOORD WITHDIST               # busca por raio
```

---

## Arquivos de contexto e spec

| Arquivo | Conteúdo |
|---|---|
| `CONTEXT.md` | Contexto técnico resumido para passar a outra IA |
| `DASHBOARD_SPEC.md` | Spec completa do dashboard de monitoramento (próximo a implementar) |

---

## O que ainda falta implementar

Ver `DASHBOARD_SPEC.md` para detalhes completos.

1. **Dashboard web** (`/tracker/`) — mapa Leaflet com markers dos entregadores em tempo real
2. **Intervalo dinâmico por velocidade** — em movimento 10s, parado 30s (código já comentado no `LocationForegroundService`)
3. **FCM** — para acordar o app imediatamente quando horário muda no servidor
4. **Teste de 4 horas contínuas** — critério 7 da spec original ainda não validado

---

## Spec implementadas

| Spec | Versão | Descrição |
|---|---|---|
| `SPEC.md` | 0.5 | Arquitetura base: GPS + MQTT + AlarmManager + polling |
| `SPEC.features.md` | 1.1 | Onboarding de permissões + auto-reinício do service |
