package br.com.soat.event

/**
 * Handler de um ou mais eventos de integração recebidos de uma fila, endereçados
 * pelos [eventTypes] lógicos (não pela classe Kotlin). Um handler pode tratar
 * vários tipos que levam à mesma ação (ex. as várias falhas que cancelam a OS).
 * Um envelope cujo `eventType` não casa com nenhum handler é considerado
 * processado (a fila recebe um superset dos eventos — ver [InboundEventDispatcher]).
 *
 * [consumerId] identifica este handler no registro de idempotência
 * `processed_events`; por padrão usa o nome da classe.
 */
interface InboundEventHandler {
    val eventTypes: Set<String>
    val consumerId: String
        get() = this::class.simpleName ?: this::class.java.name

    fun handle(envelope: EventEnvelope)
}
