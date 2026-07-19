package br.com.soat.event

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InboundEventDispatcherTest {

    private val mapper = ObjectMapper()

    private class InMemoryProcessedStore : ProcessedEventStore {
        val processed = mutableSetOf<Pair<UUID, String>>()
        override fun isProcessed(eventId: UUID, consumerId: String) = (eventId to consumerId) in processed
        override fun markAsProcessed(eventId: UUID, consumerId: String) { processed += (eventId to consumerId) }
    }

    private fun envelope(type: String, id: UUID = UUID.randomUUID()) = EventEnvelope(
        eventId = id,
        eventType = type,
        eventVersion = 1,
        occurredAt = Instant.parse("2026-07-18T14:03:00Z"),
        payload = mapper.readTree("""{"orderId":"${UUID.randomUUID()}"}"""),
    )

    private class RecordingHandler(private val type: String) : InboundEventHandler {
        override val eventTypes = setOf(type)
        val handled = mutableListOf<UUID>()
        override fun handle(envelope: EventEnvelope) { handled += envelope.eventId }
    }

    @Test
    fun `dispatches to handler matching eventType`() {
        val handler = RecordingHandler(EventType.PAYMENT_CONFIRMED)
        val dispatcher = InboundEventDispatcher(InMemoryProcessedStore(), listOf(handler))

        val e = envelope(EventType.PAYMENT_CONFIRMED)
        assertTrue(dispatcher.process(e))
        assertEquals(listOf(e.eventId), handler.handled)
    }

    @Test
    fun `marks unknown eventType as processed without any handler`() {
        val handler = RecordingHandler(EventType.PAYMENT_CONFIRMED)
        val dispatcher = InboundEventDispatcher(InMemoryProcessedStore(), listOf(handler))

        assertTrue(dispatcher.process(envelope("SomethingWeIgnore")))
        assertTrue(handler.handled.isEmpty())
    }

    @Test
    fun `does not re-run an already processed event for the same consumer`() {
        val handler = RecordingHandler(EventType.EXECUTION_FINISHED)
        val store = InMemoryProcessedStore()
        val dispatcher = InboundEventDispatcher(store, listOf(handler))

        val e = envelope(EventType.EXECUTION_FINISHED)
        dispatcher.process(e)
        dispatcher.process(e)

        assertEquals(1, handler.handled.size)
    }

    @Test
    fun `returns false and does not mark processed when a handler throws`() {
        val failing = object : InboundEventHandler {
            override val eventTypes = setOf(EventType.PARTS_UNAVAILABLE)
            override fun handle(envelope: EventEnvelope): Unit = throw RuntimeException("boom")
        }
        val store = InMemoryProcessedStore()
        val dispatcher = InboundEventDispatcher(store, listOf(failing))

        val e = envelope(EventType.PARTS_UNAVAILABLE)
        assertFalse(dispatcher.process(e))
        assertTrue(store.processed.isEmpty())
    }
}
