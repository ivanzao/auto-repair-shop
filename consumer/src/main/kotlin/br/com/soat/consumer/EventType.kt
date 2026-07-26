package br.com.soat.consumer

object EventType {
    const val PAYMENT_CONFIRMED = "PaymentConfirmed"
    const val QUOTE_REJECTED = "QuoteRejected"
    const val PAYMENT_FAILED = "PaymentFailed"

    const val DIAGNOSE_FINISHED = "DiagnoseFinished"
    const val EXECUTION_STARTED = "ExecutionStarted"
    const val EXECUTION_FINISHED = "ExecutionFinished"
    const val SUPPLIES_UNAVAILABLE = "SuppliesUnavailable"
    const val EXECUTION_FAILED = "ExecutionFailed"
    const val RESERVATION_EXPIRED = "ReservationExpired"
}
