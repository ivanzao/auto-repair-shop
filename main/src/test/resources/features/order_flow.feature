# language: pt
Funcionalidade: Ciclo de vida da ordem de serviço dirigido por eventos

  O order abre a OS com OrderCreated, deriva o status a partir dos eventos de
  execution e billing, e publica OrderAwaitingApproval quando o diagnóstico
  chega precificado. Neste teste os dois serviços são simulados publicando
  envelopes direto na fila de entrada do order.

  Cenário: Fluxo feliz do diagnóstico até a conclusão
    Dado uma ordem de serviço criada
    Então o evento "OrderCreated" é publicado no tópico de eventos do order
    E a ordem fica com status "RECEIVED"
    E a ordem ainda não sabe quem diagnosticou
    Quando o execution publica "DiagnoseFinished" para a ordem
    Então a ordem fica com status "WAITING_APPROVAL"
    E a ordem guarda o snapshot precificado
    E a ordem registra quem diagnosticou
    E o evento "OrderAwaitingApproval" carrega a reserva e o total do diagnóstico
    Quando o billing publica "PaymentConfirmed" para a ordem
    Então a ordem fica com status "EXECUTION_ENQUEUED"
    Quando o execution publica "ExecutionStarted" para a ordem
    Então a ordem fica com status "IN_PROGRESS"
    Quando o execution publica "ExecutionFinished" para a ordem
    Então a ordem fica com status "COMPLETED"

  Cenário: Compensação por insumo indisponível durante o diagnóstico
    Dado uma ordem de serviço criada
    Quando o execution publica "SuppliesUnavailable" para a ordem
    Então a ordem fica com status "CANCELED"

  Cenário: Compensação por orçamento recusado
    Dado uma ordem de serviço criada
    Quando o execution publica "DiagnoseFinished" para a ordem
    Então a ordem fica com status "WAITING_APPROVAL"
    Quando o billing publica "QuoteRejected" para a ordem
    Então a ordem fica com status "CANCELED"

  Cenário: Pagamento fora de ordem não pula o diagnóstico
    Dado uma ordem de serviço criada
    Quando o billing publica "PaymentConfirmed" para a ordem
    Então a ordem fica com status "RECEIVED"
