# Spec V1 — Dispatch realtime somente online, usando MQTT

**Versão:** 0.1  
**Objetivo:** entregar ofertas de nova entrega em tempo real somente para entregadores online no app, reutilizando o broker MQTT já existente.  
**Fora do escopo desta versão:** FCM, entrega confiável com app morto, retry cross-device, aceite concorrente distribuído, chat, tracking da corrida.

---

## 1. Decisão técnica da V1

Nesta versão, **não usar FCM** e **não criar um WebSocket separado**.

O app já possui:

* broker EMQX em produção
* conexão MQTT ativa durante o turno
* reconexão automática
* `ForegroundService` mantendo o processo mais estável

Portanto, a solução mais simples e coerente é:

* app online/no turno → recebe oferta via MQTT
* app offline, fechado ou não conectado → não recebe nesta V1

Essa limitação é intencional e aceita para a primeira versão.

---

## 2. Fluxo funcional

```text
Backend identifica entregadores elegíveis para um pedido
        ↓
Para cada entregador elegível:
    publica oferta no tópico MQTT individual do entregador
        ↓
Se app estiver conectado:
    recebe oferta em tempo real
        ↓
Mostra alerta visual/sonoro no app
        ↓
Entregador pode abrir tela da oferta
        ↓
Backend valida se a oferta ainda está disponível
```

---

## 3. Regras de produto da V1

* Oferta só precisa chegar para entregador **online**.
* O app deve considerar online apenas quando o `LocationForegroundService` estiver ativo e o MQTT conectado.
* Cada oferta deve ter expiração curta.
* Oferta expirada não pode ser aceita.
* Se múltiplos entregadores receberem a mesma oferta, o backend continua sendo a fonte da verdade para o primeiro aceite válido.
* O app nunca decide sozinho que a entrega é dele.

---

## 4. Tópicos MQTT

Padronização proposta para crescer sem retrabalho:

```text
drivers/{driver_id}/location     → app publica GPS
drivers/{driver_id}/config       → backend publica atualização de config
drivers/{driver_id}/delivery     → backend publica nova oferta de entrega
drivers/{driver_id}/cancel       → backend publica cancelamento da oferta
```

Para a V1, o tópico novo necessário é:

```text
drivers/{driver_id}/delivery
```

---

## 5. Payload da oferta

Payload mínimo recomendado:

```json
{
  "event": "new_delivery",
  "event_id": "evt_8f3c2a",
  "delivery_id": "del_10293",
  "order_id": "ord_8821",
  "expires_at": "2026-05-27T15:35:00Z",
  "pickup": {
    "label": "Loja Centro",
    "lat": -22.9068,
    "lng": -47.0626
  },
  "dropoff": {
    "label": "Rua Exemplo, 123",
    "lat": -22.9123,
    "lng": -47.0711
  },
  "distance_km": 1.8,
  "payout": 12.50,
  "message": "Nova entrega disponível"
}
```

### Campos obrigatórios

* `event_id` — deduplicação no app
* `delivery_id` — identificador da oferta
* `order_id` — pedido relacionado
* `expires_at` — vencimento da oferta em UTC

### Campos opcionais

* `pickup`, `dropoff`
* `distance_km`
* `payout`
* `message`

---

## 6. Mudanças no app Android

### 6.1 `MqttManager`

Ao conectar no MQTT, além de fazer subscribe em `drivers/{id}/config`, também deve fazer subscribe em:

```text
drivers/{driver_id}/delivery
```

Ao receber payload de oferta:

* desserializa JSON
* ignora se `event_id` já foi processado recentemente
* ignora se `expires_at` já passou
* envia evento para a camada de UI/notificação

### 6.2 Novo model

Criar `DeliveryOffer.kt` com os campos do payload.

### 6.3 Novo gerenciador de ofertas

Criar algo como `DeliveryOfferManager` ou `OfferManager` para:

* manter última oferta recebida em memória
* expor `StateFlow` para a UI
* deduplicar `event_id`
* validar expiração local

### 6.4 UI da oferta

Na V1, não precisa tela complexa. Pode começar com:

* alerta visual no app
* `AlertDialog` ou card destacado na `MainActivity`
* botão para abrir detalhes

O mínimo aceitável é:

* título da oferta
* origem / destino
* distância
* valor
* tempo restante

### 6.5 Notificação local

Mesmo sem FCM, quando chegar oferta via MQTT com app online, criar uma **notificação local** de alta prioridade.

Objetivo:

* alertar mesmo se a tela atual não estiver aberta
* dar sensação de tempo real
* preparar o app para a futura V2 com FCM

---

## 7. Mudanças no backend

O backend que identifica entregadores potenciais deve:

1. calcular candidatos
2. para cada candidato, publicar no tópico `drivers/{driver_id}/delivery`
3. persistir no banco ou cache que a oferta foi enviada
4. registrar `expires_at`

Exemplo conceitual:

```python
topic = f"drivers/{driver_id}/delivery"
payload = {
    "event": "new_delivery",
    "event_id": event_id,
    "delivery_id": delivery_id,
    "order_id": order_id,
    "expires_at": expires_at_iso,
    "pickup": {...},
    "dropoff": {...},
    "distance_km": distance_km,
    "payout": payout,
}
emqx_client.publish(topic, json.dumps(payload), qos=1)
```

### Recomendação de backend

O publish não deve ficar espalhado na regra de negócio principal. Idealmente:

* serviço calcula candidatos
* cria registros de oferta
* worker/dispatcher publica no MQTT

Isso melhora:

* retry
* observabilidade
* deduplicação
* auditoria

---

## 8. Aceite da oferta

Mesmo na V1, o aceite **não** deve acontecer só no app.

Fluxo correto:

```text
Entregador toca em aceitar
        ↓
App chama endpoint HTTP do backend
        ↓
Backend verifica:
    - oferta ainda válida?
    - já foi aceita por outro?
    - entregador ainda elegível?
        ↓
Se sim: confirma
Se não: responde conflito/expirada
```

Endpoint sugerido:

```text
POST /api/tracker/deliveries/{delivery_id}/accept/
```

Payload sugerido:

```json
{
  "driver_id": "edgarsegundo",
  "event_id": "evt_8f3c2a"
}
```

---

## 9. Confiabilidade esperada da V1

### Garante

* baixa latência para app online
* infraestrutura simples
* reaproveitamento do EMQX existente
* experiência suficiente para validar dispatch em tempo real

### Não garante

* entrega com app morto
* entrega com app offline
* despertar do app pelo Android
* fallback quando o dispositivo não estiver conectado

Esses pontos ficam para a **V2 com FCM**.

---

## 10. Critérios de aceite

Para considerar a V1 pronta:

1. App conectado no MQTT recebe publicação em `drivers/{id}/delivery` em tempo real.
2. Oferta aparece visualmente no app em até poucos segundos.
3. Oferta expirada é ignorada.
4. `event_id` duplicado não gera alerta duplicado.
5. Aceite sempre passa pelo backend.
6. Se dois entregadores aceitarem ao mesmo tempo, backend resolve concorrência corretamente.

---

## 11. Próxima fase planejada

Na V2, adicionar:

* Firebase Cloud Messaging para fallback
* heads-up notification mesmo com app em background
* canais de notificação dedicados
* wake-up mais confiável quando o app não estiver conectado

Ou seja:

* **V1:** MQTT realtime online
* **V2:** MQTT realtime + FCM fallback

## Como enviar mensagem popup para testar notification

```bash
mosquitto_pub -h localhost -p 1883 \
  -t "drivers/edgarsegundo/delivery" \
  -m '{"event":"new_delivery","event_id":"evt005","delivery_id":"del001","order_id":"ord001","expires_at":"2030-01-01T12:00:00Z","message":"Teste","pickup":{"label":"Loja Centro"},"dropoff":{"label":"Rua Exemplo 123"},"distance_km":1.8,"payout":12.50}'
```

> Não esqueça de incremental o event_id, ex: evt006