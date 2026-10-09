# NotiHook – Spec de implementação (fase 1)

Spec para um agente (ou para você) transformar o template Hello World Android/Kotlin no app **NotiHook**: um app que captura as notificações do WhatsApp Business, grava tudo localmente e permite inspecionar na UI todas as informações que a notificação traz.

## Variáveis a ajustar antes de começar

| Variável | Valor padrão |
| --- | --- |
| Nome do app | `NotiHook` |
| applicationId / package | `com.seudominio.notihook` (troque pelo seu domínio) |
| Pacote monitorado (padrão) | `com.whatsapp.w4b` (WhatsApp Business) |
| minSdk | 26 |
| targetSdk / compileSdk | o mais recente que o template já usa |

## 1. Objetivo e escopo

**Dentro do escopo (fase 1):**

- Receber notificações via `NotificationListenerService`.
- Gravar cada evento (postada, atualizada, removida) em banco local, incluindo um dump completo de `extras`.
- UI para ver a lista de eventos, abrir o detalhe de cada um com todos os campos, buscar, filtrar, exportar e limpar.
- Tela de status com permissão de acesso a notificações e orientações de bateria.

**Fora do escopo (não implementar):**

- Webhook, envio de dados pela rede, qualquer permissão `INTERNET`.
- Responder mensagens via `RemoteInput`.
- Download de mídia, login, backend, analytics.

## 2. Passo 0 – Limpar o template e renomear

Objetivo: partir de um projeto que compila e contém só o necessário.

1. Inspecione o template e identifique o toolkit de UI. **Se for Jetpack Compose, mantenha Compose.** Se for XML/Views, implemente a UI equivalente com Views + RecyclerView (o restante da spec não muda).
2. Renomeie o package em todos os arquivos Kotlin e nos diretórios (`src/main`, `src/test`, `src/androidTest`) para o novo `applicationId`. Atualize `namespace` e `applicationId` no `build.gradle(.kts)` do módulo `app`.
3. Ajuste `rootProject.name` em `settings.gradle(.kts)` para `NotiHook` e `app_name` em `strings.xml` para `NotiHook`.
4. Remova o que não será usado: textos e composables/layouts de exemplo ("Hello World", `Greeting`), testes de exemplo (`ExampleUnitTest`, `ExampleInstrumentedTest`), ícones/recursos não referenciados e dependências sem uso.
5. Mantenha: Gradle wrapper, version catalog (se existir), tema do template, ícone launcher (pode trocar depois).
6. Defina `android:allowBackup="false"` (as mensagens são sensíveis) e remova `dataExtractionRules`/`fullBackupContent` se existirem, ou configure para excluir o banco.
7. **Critério:** `./gradlew assembleDebug` passa e o app abre com uma tela vazia antes de seguir.

Não invente versões de bibliotecas: use as que o template já define e, para novas dependências, use versões estáveis compatíveis com o AGP/Kotlin do projeto.

## 3. Stack

- Kotlin, Coroutines + Flow.
- Room (com KSP) para persistência.
- DataStore (Preferences) para configurações.
- Jetpack Compose + Material 3, ViewModel, `collectAsStateWithLifecycle`.
- Navegação: Navigation Compose simples (ou estado local) com 3 destinos: Início, Detalhe, Configurações.
- JSON: `org.json` do próprio Android (sem libs extras) para serializar extras/mensagens/ações.

## 4. Estrutura de pacotes

```
com.seudominio.notihook
├── NotiHookApp.kt              (Application: instancia DB e repositório)
├── capture/
│   ├── WaListenerService.kt    (NotificationListenerService)
│   └── NotificationParser.kt   (sbn -> entidade; dump de extras)
├── data/
│   ├── AppDatabase.kt
│   ├── CapturedEvent.kt        (entity)
│   ├── ParsedMessage.kt        (entity)
│   ├── EventDao.kt
│   ├── Repository.kt
│   └── SettingsStore.kt        (DataStore)
├── ui/
│   ├── MainActivity.kt
│   ├── HomeScreen.kt
│   ├── EventDetailScreen.kt
│   ├── SettingsScreen.kt
│   └── theme/
└── util/
    ├── Permissions.kt          (checar/abrir acesso a notificações, bateria)
    └── Export.kt               (JSON -> arquivo/share)
```

## 5. Manifest

```xml
<service
    android:name=".capture.WaListenerService"
    android:exported="true"
    android:label="NotiHook"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

- Declare `android:name=".NotiHookApp"` na tag `<application>`.
- **Sem** `INTERNET`. **Sem** `POST_NOTIFICATIONS` (o app não posta notificações).

## 6. Modelo de dados

### 6.1 Tabela `captured_event` (um registro por evento, sem dedupe)

Cada vez que o WhatsApp atualiza a notificação de uma conversa, grave um novo registro. Isso permite ver como a notificação evolui.

| Campo | Tipo | Origem |
| --- | --- | --- |
| `id` | Long PK auto |  |
| `capturedAt` | Long | `System.currentTimeMillis()` |
| `eventType` | String | `POSTED`, `REMOVED`, `SNAPSHOT` (importada das ativas) |
| `removalReason` | Int? | `rankingMap`/`reason` do `onNotificationRemoved` (quando disponível) |
| `sbnKey` | String | `sbn.key` |
| `packageName` | String | `sbn.packageName` |
| `notifId` | Int | `sbn.id` |
| `tag` | String? | `sbn.tag` |
| `postTime` | Long | `sbn.postTime` |
| `groupKey` | String? | `sbn.groupKey` |
| `userHandle` | String | `sbn.user.toString()` |
| `isOngoing`, `isClearable` | Boolean | `sbn` |
| `flags` | Int | `notification.flags` |
| `category` | String? | `notification.category` |
| `channelId` | String? | `notification.channelId` |
| `group`, `sortKey` | String? | `notification` |
| `shortcutId` | String? | `notification.shortcutId` |
| `whenTime` | Long | `notification.`when\`\` |
| `title`, `text`, `bigText`, `subText`, `summaryText`, `infoText` | String? | `EXTRA_*` correspondentes |
| `textLines` | String? | `EXTRA_TEXT_LINES` como JSON array |
| `template` | String? | `EXTRA_TEMPLATE` |
| `conversationTitle` | String? | `EXTRA_CONVERSATION_TITLE` |
| `isGroupConversation` | Boolean? | `EXTRA_IS_GROUP_CONVERSATION` |
| `messagesJson` | String? | `EXTRA_MESSAGES` expandido (ver 7.3) |
| `historicMessagesJson` | String? | `EXTRA_HISTORIC_MESSAGES` expandido |
| `actionsJson` | String? | lista de ações (ver 7.4) |
| `hasLargeIcon`, `hasSmallIcon` | Boolean | presença apenas |
| `extrasJson` | String | **dump completo de todos os extras** (ver 7.2) |
| `isSummary` | Boolean | `flags and FLAG_GROUP_SUMMARY != 0` |

Índices: `capturedAt`, `packageName`, `sbnKey`.

### 6.2 Tabela `parsed_message` (mensagens individuais, deduplicadas)

| Campo | Tipo |
| --- | --- |
| `id` | Long PK auto |
| `eventId` | Long (FK lógica para `captured_event`) |
| `sbnKey` | String |
| `conversation` | String |
| `isGroup` | Boolean |
| `senderName` | String? |
| `senderKey` | String? |
| `senderUri` | String? |
| `text` | String |
| `timestamp` | Long |
| `mimeType`, `dataUri` | String? |
| `dedupeKey` | String, **UNIQUE** = `"$sbnKey\|$timestamp\|$text"` |

Inserir com `OnConflictStrategy.IGNORE`. Mensagens de `EXTRA_HISTORIC_MESSAGES` entram só se ainda não existirem (mesmo dedupe).

## 7. Captura – `WaListenerService`

### 7.1 Comportamento

- `onNotificationPosted(sbn)`: se o pacote estiver na lista monitorada (DataStore, padrão `com.whatsapp.w4b`), parseie e grave `captured_event` (POSTED) e as `parsed_message`.
- `onNotificationRemoved(sbn)`: grave um evento `REMOVED` com os metadados básicos.
- `onListenerConnected()`: marque o serviço como conectado (estado em memória/Flow para a UI).
- `onListenerDisconnected()`: chame `requestRebind(ComponentName(this, WaListenerService::class.java))`.
- Trabalho de banco em um `CoroutineScope(SupervisorJob() + Dispatchers.IO)` próprio do serviço, cancelado em `onDestroy`. Nunca bloqueie o callback.
- **O serviço nunca pode crashar**: envolva todo o parsing em `try/catch` e, em caso de erro, grave um evento com `extrasJson` contendo `{"parse_error": "..."}`.
- Capture **tudo** do pacote monitorado, inclusive notificações `ongoing`, resumos de grupo e canais de ruído. Um filtro "ocultar ongoing/resumos" existe apenas na UI.

### 7.2 Dump completo de `extras`

Implemente `fun bundleToJson(b: Bundle, depth: Int = 0): JSONObject`:

- Para cada chave de `b.keySet()`, gere `{ "type": "<classe do valor>", "value": ... }`.
- Tipos primitivos, `String`, `CharSequence`: valor como string.
- Arrays e listas de primitivos/strings: JSON array.
- `Bundle`: recursão.
- `Array<Parcelable>` / `Parcelable[]` cujos elementos sejam `Bundle`: array de objetos via recursão (é o caso de `EXTRA_MESSAGES`).
- `Person`: `{name, key, uri, isBot, isImportant}`.
- `Icon`, `PendingIntent`, `Bitmap`, `RemoteViews`: apenas `{"type": "...", "value": "<não serializável>"}`.
- Qualquer outro tipo: `toString()` dentro de try/catch.
- Limite de profundidade 5 e truncamento de strings em 20.000 caracteres (marcar `"truncated": true`).
- Use `@Suppress("DEPRECATION")` onde precisar de `bundle.get(key)`.

### 7.3 Mensagens estruturadas

Para `EXTRA_MESSAGES` e `EXTRA_HISTORIC_MESSAGES`:

```kotlin
val raw = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
val msgs = Notification.MessagingStyle.Message.getMessagesFromBundleArray(raw)
```

Serialize cada mensagem como `{text, timestamp, senderName, senderKey, senderUri, dataMimeType, dataUri}`. Se `EXTRA_MESSAGES` for nulo, crie uma mensagem de fallback com `EXTRA_TITLE` + `EXTRA_TEXT` e marque `"fallback": true`.

### 7.4 Ações

Para cada `notification.actions`: `{title, hasRemoteInput, remoteInputKeys: [resultKey], semanticAction, isContextual}`. **Apenas registrar**, nunca disparar.

### 7.5 Snapshot das ativas

Expor no repositório/serviço um método `importActive()` que chama `getActiveNotifications()` e grava cada uma como evento `SNAPSHOT` usando o mesmo parser. A UI chama esse método por botão (o serviço precisa estar conectado; senão mostrar aviso).

## 8. UI

### 8.1 Início (`HomeScreen`)

- **Card de status**: permissão de acesso a notificações (ativa/inativa), serviço conectado (sim/não), total de eventos, total de mensagens, data do último evento. Reavalie ao voltar para a tela (`ON_RESUME`).
- Botão **"Ativar acesso a notificações"** → `startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))`. Verificação: `NotificationManagerCompat.getEnabledListenerPackages(context).contains(packageName)`.
- Aviso quando a permissão estiver inativa: *"Se o app foi instalado fora da Play Store, abra Configurações > Apps > NotiHook > ⋮ > Permitir configurações restritas e depois ative o acesso."*
- Botão **"Ajustar bateria"** → `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (abre a lista; não use `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
- Abas: **Eventos** | **Mensagens**.

### 8.2 Aba Eventos

- `LazyColumn` ordenada por `capturedAt` desc, alimentada por Flow do Room (use Paging 3 se a lista passar de alguns milhares; caso contrário, `LIMIT` configurável).
- Cada item: hora, badge do tipo (`POSTED`/`REMOVED`/`SNAPSHOT`), `title`, trecho de `text`, `channelId`, ícone se `isSummary`/`isOngoing`.
- Barra de busca (LIKE em title, text, conversationTitle, extrasJson).
- Filtros (chips): tipo de evento, ocultar ongoing, ocultar resumos, por `channelId`, por pacote.
- Toque abre o detalhe.

### 8.3 Aba Mensagens

Lista de `parsed_message` agrupada por `conversation`, mostrando remetente, texto e horário. Toque leva ao evento de origem (`eventId`).

### 8.4 Detalhe do evento (`EventDetailScreen`)

Objetivo principal do app: **ver tudo que a notificação traz**. Seções expansíveis, todas com texto selecionável:

1. **Sistema (StatusBarNotification)**: key, packageName, id, tag, postTime formatado, groupKey, user, isOngoing, isClearable.
2. **Notification**: flags (valor e nomes decodificados, ex.: `GROUP_SUMMARY`), category, channelId, group, sortKey, shortcutId, when, hasLargeIcon/hasSmallIcon.
3. **Textos**: title, text, bigText, subText, summaryText, infoText, textLines.
4. **Conversa**: template, conversationTitle, isGroup.
5. **Mensagens**: lista de `messagesJson` e `historicMessagesJson` com todos os campos.
6. **Ações**: lista com título e indicação de RemoteInput.
7. **Extras (dump completo)**: árvore chave → tipo → valor, com recuo para bundles aninhados.
8. **JSON bruto**: monoespaçado, com rolagem horizontal.

Barra superior com **Copiar JSON**, **Compartilhar** e **Apagar este evento**. Botões "anterior/próximo" para navegar entre eventos da mesma `sbnKey`, para comparar como a notificação mudou.

### 8.5 Configurações

- Lista editável de **pacotes monitorados** (padrão `com.whatsapp.w4b`; permitir adicionar `com.whatsapp` para testes).
- **Retenção** em dias (padrão 30; 0 = infinito). Limpeza automática ao abrir o app e a cada 24h via `WorkManager` periódico (ou apenas ao abrir, se preferir simplicidade).
- **Exportar tudo** (JSON via `ACTION_CREATE_DOCUMENT` ou share sheet).
- **Apagar todos os dados** com confirmação.
- Switch **"Pausar captura"** (o serviço continua ligado, mas ignora eventos).

## 9. Privacidade e robustez

- Dados ficam só no aparelho: sem `INTERNET`, `allowBackup=false`.
- Logs de conteúdo (`Log.d` com title/text) somente em builds debug (`BuildConfig.DEBUG`).
- Banco: uma única instância, criada em `NotiHookApp`.
- Migrações: `fallbackToDestructiveMigration()` é aceitável na fase 1, mas documente isso no README.
- Strings da UI em português (BR), em `strings.xml`.

## 10. Ordem de implementação (um commit por etapa, build verde em cada uma)

1. **Limpeza e rename** (seção 2).
2. **Dependências + Room + entidades + DAO + repositório** (seção 6).
3. **Parser** (`NotificationParser` + `bundleToJson`) com testes unitários para o dump (bundle aninhado, truncamento, tipos desconhecidos).
4. **Serviço + manifest + DataStore de pacotes monitorados** (seções 5 e 7).
5. **Tela de status e permissões** (8.1).
6. **Lista de eventos com busca e filtros** (8.2).
7. **Detalhe do evento** (8.4).
8. **Aba Mensagens** (8.3).
9. **Configurações, exportar, limpar, retenção** (8.5).
10. **README** com instruções de instalação, permissões e armadilhas.

## 11. Critérios de aceitação (teste manual no aparelho)

1. Instala, abre e mostra "acesso a notificações inativo"; após ativar, mostra "serviço conectado".
2. Uma mensagem de contato salvo no WhatsApp Business gera um evento `POSTED` com title, text, channelId e `messagesJson` preenchidos.
3. Segunda mensagem do mesmo contato gera **novo evento** com a mesma `sbnKey` e uma nova linha em `parsed_message` (sem duplicar a primeira).
4. Mensagem de grupo mostra `isGroupConversation = true`, `conversationTitle` do grupo e `senderName` de quem falou.
5. Contato não salvo: o title mostra o número (ou o formato que o app usar) e isso é visível no detalhe.
6. Áudio, foto e documento aparecem como texto indicativo, sem crash.
7. Ao abrir a conversa, o evento `REMOVED` é registrado.
8. O detalhe mostra **todas** as chaves de `extras` (compare com o Logcat de um dump de referência).
9. Matar o app e reiniciar o celular: o serviço volta e continua capturando; os dados antigos continuam lá.
10. Exportar gera um JSON válido com todos os eventos; "Apagar tudo" zera as duas tabelas.
11. Nenhum crash com extras inesperados (teste com notificações de outro app adicionado em Configurações).

## 12. Regras para o agente que executar esta spec

- Siga a ordem da seção 10 e confirme que o projeto compila em cada etapa.
- Não adicione funcionalidades fora do escopo (rede, respostas automáticas, mídia).
- Se algo do template conflitar com a spec (toolkit de UI, versões do AGP/Kotlin), adapte com o mínimo de mudanças e registre a decisão no README.
- Quando houver ambiguidade, escolha a opção mais simples que atenda os critérios de aceitação e anote a premissa.
