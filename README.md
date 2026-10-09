# NotiHook

App Android que captura as notificações do WhatsApp Business, grava tudo
localmente e permite inspecionar na UI **todas** as informações que a
notificação traz.

Os dados **nunca saem do aparelho**: o app não declara a permissão
`INTERNET`, e `allowBackup` está desligado.

## Instalação

Requisitos: JDK 21 e o Android SDK (compileSdk 35). O wrapper do Gradle já
está no repositório.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
./gradlew installDebug      # com o aparelho conectado
```

Rodar os testes unitários:

```bash
./gradlew testDebugUnitTest
```

## Permissões (passo obrigatório)

O app depende de um `NotificationListenerService`, cuja permissão só pode ser
concedida à mão:

1. Abra o NotiHook → botão **"Ativar acesso a notificações"**.
2. Na lista do sistema, ative o **NotiHook**.
3. Volte ao app: o card de status deve mostrar *Acesso: Ativo* e
   *Serviço: Conectado*.

### Armadilhas

- **Instalação fora da Play Store.** O Android 13+ bloqueia o acesso a
  notificações para apps instalados por sideload. Se o botão não resolver,
  vá em **Configurações > Apps > NotiHook > ⋮ > Permitir configurações
  restritas** e só então ative o acesso.
- **Bateria.** Em muitos OEMs (Xiaomi, Samsung, Huawei) a otimização de
  bateria mata o listener. Use o botão **"Ajustar bateria"** e marque o
  NotiHook como "sem restrições". O app abre a *lista* de otimização
  (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`) de propósito: a variante
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` exige permissão e é motivo de
  rejeição na Play Store.
- **Serviço não conecta.** Desative e reative o acesso a notificações; o
  app também chama `requestRebind` ao detectar a desconexão.
- **Importar ativas** só funciona com o serviço conectado — é o próprio
  sistema que fornece `getActiveNotifications()`.

## Como usar

- **Início** — card de status e duas abas:
  - **Eventos**: um registro por evento de notificação (`POSTED`,
    `REMOVED`, `SNAPSHOT`), com busca e filtros (tipo, ongoing, resumos,
    canal, pacote).
  - **Mensagens**: as mensagens individuais extraídas, agrupadas por
    conversa; o toque leva ao evento de origem.
- **Detalhe do evento** — o objetivo principal do app. Oito seções
  expansíveis com todo o conteúdo selecionável, incluindo a árvore completa
  de `extras` e o JSON bruto. Os botões anterior/próximo navegam entre os
  eventos da mesma `sbnKey`, para comparar como a notificação evoluiu.
- **Configurações** — pacotes monitorados (padrão `com.whatsapp.w4b`;
  adicione `com.whatsapp` para testar), retenção em dias (aplicada ao abrir o app), pausa da captura,
  exportação em JSON e apagar tudo.

## Modelo de dados

Duas tabelas Room (`notihook.db`):

- **`captured_event`** — um registro por evento, **sem dedupe**: cada
  atualização que o WhatsApp faz na notificação de uma conversa gera uma
  nova linha. Guarda os metadados do `StatusBarNotification` e da
  `Notification`, os textos, as mensagens/ações serializadas e o
  `extrasJson` com o **dump completo** dos extras.
- **`parsed_message`** — mensagens individuais, **deduplicadas** por
  `dedupeKey = "$sbnKey|$timestamp|$text"` com `OnConflictStrategy.IGNORE`,
  de modo que reenvios da mesma notificação (e as
  `EXTRA_HISTORIC_MESSAGES`) não geram linhas repetidas.

### Migrações

A fase 1 usa `fallbackToDestructiveMigration()`: **qualquer mudança de
schema apaga os dados locais**. Exporte antes de atualizar o app se os
dados importarem.

## Robustez

- O serviço **nunca crasha**: todo o parsing está em `try/catch` e, em caso
  de erro, grava um evento com `extrasJson = {"parse_error": "..."}`, para
  não perder o registro da notificação.
- O `bundleToJson` é defensivo: limite de profundidade 5, truncamento de
  strings em 20.000 caracteres (marcando `truncated`), `toString()` para
  tipos desconhecidos e `{"value": "<não serializável>"}` para `Icon`,
  `PendingIntent`, `Bitmap` e `RemoteViews`.
- O trabalho de banco roda num escopo de IO próprio do serviço, cancelado em
  `onDestroy`; os callbacks nunca bloqueiam.
- Logs com conteúdo de mensagem só em builds debug (`BuildConfig.DEBUG`).
- As ações das notificações são **apenas registradas**, nunca disparadas.

## Fora do escopo (fase 1)

Webhook, envio pela rede, resposta via `RemoteInput`, download de mídia,
login, backend e analytics.

## Decisões e desvios da spec

A spec partia do princípio de que este repositório continha um template
"Hello World". Na prática ele continha o app **NeApp Tracker** (rastreamento
de GPS por MQTT), que foi substituído a pedido. Registro aqui o que isso
implicou:

| Ponto | Spec | Implementado | Motivo |
| --- | --- | --- | --- |
| Base do projeto | Limpar um template Hello World | Remoção do app NeApp Tracker | O repositório não tinha um template. O estado anterior está na tag git `neapp-before-notihook`. |
| Toolkit de UI | Manter Compose se o template usar | Compose + Material 3 adicionados | O projeto era XML/Views (`viewBinding`). Compose foi escolhido por ser o pedido da seção 3 e por reduzir muito o código da árvore de extras (8.4). |
| `minSdk` | 26 | 26 | O projeto usava 27; baixado para seguir a spec. |
| `docs/` | — | Mantido | Guarda as especificações e o material de negócio do NeApp, alheios a esta spec; apagar não era parte da limpeza pedida. |
| Paging | Paging 3 se passar de alguns milhares | `LIMIT` configurável (padrão 1000) | A spec permite as duas opções e pede a mais simples. |
| Limpeza periódica | `WorkManager` a cada 24h (ou só ao abrir) | Só ao abrir o app | O WorkManager injeta `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED` e `FOREGROUND_SERVICE` no manifest. Para um app cujo argumento é privilégio mínimo, é um preço alto por uma limpeza diária — e a spec já autoriza "apenas ao abrir, se preferir simplicidade". |
| Testes do dump | Testes unitários | JUnit + Robolectric | `Bundle` e `Person` são classes do framework. O Robolectric 4.13 só suporta até a SDK 34, daí o `app/src/test/resources/robolectric.properties` com `sdk=34`. |
| `applicationId` | `com.seudominio.notihook` | `com.seudominio.notihook` | Mantido o valor da spec — **troque pelo seu domínio** antes de distribuir. |

Outros pontos que valem registro:

- O `gradle-wrapper.jar` estava ausente do repositório (engolido pela regra
  `*.jar` do `.gitignore`); foi restaurado e excetuado. O `.gitignore`
  também passou a ignorar `.gradle/` e `build/`, que estavam versionados.
- `Notification.MessagingStyle.Message.getMessagesFromBundleArray`, indicado
  pela spec, só existe na API 30+. Em versões anteriores o parser lê os
  `Bundle` das mensagens à mão, cobrindo o mesmo conteúdo (marcado com
  `"raw_bundle": true` no JSON).
- O app pede **uma única permissão**:
  `BIND_NOTIFICATION_LISTENER_SERVICE` (declarada pelo serviço). Não há
  nenhuma permissão de runtime. O `android.permission.DUMP` que aparece no
  manifest final não é pedido pelo app: é a *restrição* de quem pode chamar
  o `ProfileInstallReceiver` do androidx.
