package br.com.soat.event

/**
 * Nomes lógicos e estáveis dos eventos de integração (NÃO são class names Kotlin).
 * Fonte: contrato de eventos compartilhado.
 */
object EventType {
    // produzido pelo order
    const val ORDER_CREATED = "OrderCreated"

    // consumidos do billing
    const val PAYMENT_CONFIRMED = "PaymentConfirmed"
    const val QUOTE_REJECTED = "QuoteRejected"
    const val PAYMENT_FAILED = "PaymentFailed"

    // consumidos do execution
    const val EXECUTION_STARTED = "ExecutionStarted"
    const val DIAGNOSE_FINISHED = "DiagnoseFinished"
    const val EXECUTION_FINISHED = "ExecutionFinished"
    const val PARTS_UNAVAILABLE = "PartsUnavailable"
    const val EXECUTION_FAILED = "ExecutionFailed"
    const val RESERVATION_EXPIRED = "ReservationExpired"
}
