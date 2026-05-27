# DEBUG

## How to test config endpoint

```bash
# Local
curl http://127.0.0.1:8000/api/tracker/drivers/edgarsegundo/config/

# Prod
curl -H "Accept: application/json" \
     -H "X-API-Key: 649dba..." \
     "https://sys.fastvistos.com.br/api/tracker/drivers/edgarsegundo/config/"
```

## Como enviar mensagem popup para testar notification

```bash
mosquitto_pub -h localhost -p 1883 \
  -t "drivers/edgarsegundo/delivery" \
  -m '{"event":"new_delivery","event_id":"evt005","delivery_id":"del001","order_id":"ord001","expires_at":"2030-01-01T12:00:00Z","message":"Teste","pickup":{"label":"Loja Centro"},"dropoff":{"label":"Rua Exemplo 123"},"distance_km":1.8,"payout":12.50}'
```

> Não esqueça de incremental o event_id, ex: evt006