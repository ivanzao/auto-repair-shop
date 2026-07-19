package br.com.soat.messaging

/** Uma mensagem recebida da fila. [body] é o envelope JSON cru (raw delivery). */
data class QueueMessage(val body: String, val receiptHandle: String)

/**
 * Abstração fina sobre a fila de entrada, para desacoplar o consumidor do SDK da
 * AWS e permitir testá-lo sem infraestrutura.
 */
interface MessageQueue {
    fun receive(maxMessages: Int, waitSeconds: Int): List<QueueMessage>
    fun delete(receiptHandle: String)
}
