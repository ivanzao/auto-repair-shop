# language: pt
Funcionalidade: Ciclo de vida da ordem de serviço dirigido por eventos

  O order produz OrderCreated ao abrir a OS e deriva o status a partir dos
  eventos de billing e execution. Neste teste os dois serviços são simulados
  publicando envelopes direto na fila de entrada do order.

  Cenário: Fluxo feliz até a conclusão
    Dado uma ordem de serviço criada
    Então o evento "OrderCreated" é publicado no tópico de eventos do order
    Quando o billing publica "PaymentConfirmed" para a ordem
    Então a ordem fica com status "IN_PROGRESS"
    Quando o execution publica "ExecutionFinished" para a ordem
    Então a ordem fica com status "COMPLETED"

  Cenário: Compensação por peça indisponível
    Dado uma ordem de serviço criada
    Quando o execution publica "PartsUnavailable" para a ordem
    Então a ordem fica com status "CANCELED"
