package br.com.soat.consumer

import br.com.soat.event.EventEnvelope
import br.com.soat.event.EventType
import br.com.soat.event.InboundEventDispatcher
import br.com.soat.event.InboundEventHandler
import br.com.soat.event.ProcessedEventStore
import br.com.soat.messaging.MessageQueue
import br.com.soat.messaging.QueueMessage
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InboundEventConsumerTest {

    private val mapper = ObjectMapper().findAndRegisterModules()

    private class FakeQueue(messages: List<QueueMessage>) : MessageQueue {
        private val pending = ArrayDeque(messages)
        val deleted = mutableListOf<String>()
        override fun receive(maxMessages: Int, waitSeconds: Int): List<QueueMessage> {
            val batch = mutableListOf<QueueMessage>()
            while (pending.isNotEmpty() && batch.size < maxMessages) batch += pending.removeFirst()
            return batch
        }
        override fun delete(receiptHandle: String) { deleted += receiptHandle }
    }

    private class NoopStore : ProcessedEventStore {
        override fun isProcessed(eventId: UUID, consumerId: String) = false
        override fun markAsProcessed(eventId: UUID, consumerId: String) {}
    }

    private fun envelopeJson(type: String, orderId: UUID = UUID.randomUUID()) = """
        {"eventId":"${UUID.randomUUID()}","eventType":"$type","eventVersion":1,
         "occurredAt":"2026-07-18T14:03:00Z","payload":{"orderId":"$orderId"}}
    """.trimIndent()

    @Test
    fun `dispatches and deletes a message that is fully processed`() {
        val handled = mutableListOf<UUID>()
        val handler = object : InboundEventHandler {
            override val eventTypes = setOf(EventType.PAYMENT_CONFIRMED)
            override fun handle(envelope: EventEnvelope) { handled += envelope.eventId }
        }
        val queue = FakeQueue(listOf(QueueMessage(envelopeJson(EventType.PAYMENT_CONFIRMED), "rh-1")))
        val consumer = InboundEventConsumer(
            queue,
            InboundEventDispatcher(NoopStore(), listOf(handler)),
            mapper,
            kotlinx.coroutines.Dispatchers.Unconfined,
        )

        consumer.pollOnce()

        assertEquals(1, handled.size)
        assertEquals(listOf("rh-1"), queue.deleted)
    }

    @Test
    fun `deletes an unknown eventType without a handler (mesh superset)`() {
        val queue = FakeQueue(listOf(QueueMessage(envelopeJson("SomethingWeIgnore"), "rh-2")))
        val consumer = InboundEventConsumer(
            queue,
            InboundEventDispatcher(NoopStore(), emptyList()),
            mapper,
            kotlinx.coroutines.Dispatchers.Unconfined,
        )

        consumer.pollOnce()

        assertEquals(listOf("rh-2"), queue.deleted)
    }

    @Test
    fun `does not delete a message whose handler failed`() {
        val handler = object : InboundEventHandler {
            override val eventTypes = setOf(EventType.PARTS_UNAVAILABLE)
            override fun handle(envelope: EventEnvelope): Unit = throw RuntimeException("boom")
        }
        val queue = FakeQueue(listOf(QueueMessage(envelopeJson(EventType.PARTS_UNAVAILABLE), "rh-3")))
        val consumer = InboundEventConsumer(
            queue,
            InboundEventDispatcher(NoopStore(), listOf(handler)),
            mapper,
            kotlinx.coroutines.Dispatchers.Unconfined,
        )

        consumer.pollOnce()

        assertTrue(queue.deleted.isEmpty())
    }

    @Test
    fun `skips an unparseable message without crashing`() {
        val queue = FakeQueue(listOf(QueueMessage("not json", "rh-4")))
        val consumer = InboundEventConsumer(
            queue,
            InboundEventDispatcher(NoopStore(), emptyList()),
            mapper,
            kotlinx.coroutines.Dispatchers.Unconfined,
        )

        consumer.pollOnce()

        assertTrue(queue.deleted.isEmpty())
    }
}
