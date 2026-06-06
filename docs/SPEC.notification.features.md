# Arquitetura real usada por apps tipo iFood/Uber

## 1. FCM (Push Notification)

Base do sistema.

Serve para:

* acordar o app
* avisar evento novo
* funcionar mesmo com app fechado
* funcionar com tela bloqueada

Tecnologia:

* Firebase Cloud Messaging

Sem isso, o Android pode simplesmente não entregar eventos quando o app estiver morto.

---

## 2. WebSocket (tempo real)

Usado quando o entregador está ONLINE no app.

Serve para:

* receber corridas instantaneamente
* latência muito baixa
* atualização contínua

Porque push sozinho pode atrasar alguns segundos.

Então:

* usuário ONLINE → WebSocket
* usuário OFFLINE/background → FCM

Esse é o padrão mais comum.

---

## 3. Foreground Service

Muito importante em apps de logística.

Serve para:

* impedir Android de matar o processo facilmente
* manter localização viva
* manter socket ativo
* aumentar estabilidade

Aquela notificação fixa:

> “Você está online”

isso normalmente é um Foreground Service.

---

## 4. Heads-up Notification

É só o estilo visual da notificação.

Aquela que aparece flutuando na tela.

Usado para:

* novas corridas
* alertas urgentes

---

## 5. Notification Channels + prioridade alta

Controla:

* som
* vibração
* importância
* comportamento na tela bloqueada

No Android isso é obrigatório para experiência profissional.

---

# Então eles usam TODOS?

Na prática:

## SIM, geralmente usam tudo junto.

Fluxo típico:

```text
Servidor detecta nova corrida
        ↓
Se app está online:
    envia via WebSocket
        ↓
App toca alerta instantâneo

Se app está background/morto:
    envia FCM high priority
        ↓
Android acorda app
        ↓
Mostra heads-up notification
```

E o Foreground Service fica ajudando estabilidade o tempo inteiro.

---

# O segredo dos apps “profissionais”

O diferencial não é “push notification”.

É a combinação de:

* realtime socket
* foreground service
* push fallback
* otimização de bateria
* retry
* reconexão automática
* prioridade alta
* backend rápido
* fila de eventos

---

# O que você provavelmente DEVE pedir

Se quer algo nível iFood/Uber:

Preciso implementar arquitetura profissional de dispatch em tempo real para motoristas/entregadores no Android nativo (Kotlin).

A solução deve combinar:

* Firebase Cloud Messaging (FCM)
* WebSocket realtime
* Foreground Service
* Heads-up notifications
* Notification Channels
* High priority notifications
* Reconexão automática
* Retry/fallback
* Baixa latência
* Recebimento confiável mesmo com app em background, bloqueado ou parcialmente encerrado pelo Android

Fluxo esperado:

* App online → eventos via WebSocket
* App em background/offline → fallback via FCM

---

# O mais importante

Muitos devs implementam:

* “só push”

E aí o sistema:

* atrasa
* perde corrida
* Android mata
* notificação falha

Apps grandes NÃO dependem de uma única tecnologia.

---

