package br.com.soat.event

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OutboxEnvelopeTest {

    private val mapper = ObjectMapper()

    @Test
    fun `wraps the payload in the contract envelope`() {
        val id = UUID.randomUUID()
        val event = OutboxEvent(
            eventId = id,
            eventType = EventType.ORDER_CREATED,
            eventVersion = 1,
            occurredAt = Instant.parse("2026-07-18T14:03:00Z"),
            payload = """{"orderId":"$id","services":[]}""",
        )

        val envelope = mapper.readTree(event.toEnvelopeJson(mapper))

        assertEquals(id.toString(), envelope["eventId"].asText())
        assertEquals("OrderCreated", envelope["eventType"].asText())
        assertEquals(1, envelope["eventVersion"].asInt())
        assertEquals("2026-07-18T14:03:00Z", envelope["occurredAt"].asText())
        // payload é objeto aninhado, não string escapada
        assertEquals(id.toString(), envelope["payload"]["orderId"].asText())
        assertTrue(envelope["payload"]["services"].isArray)
    }

    private fun assertTrue(condition: Boolean) = org.junit.jupiter.api.Assertions.assertTrue(condition)
}
