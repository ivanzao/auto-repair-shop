package br.com.soat.consumer.handler

import br.com.soat.consumer.EventEnvelope
import br.com.soat.order.OrderListenerUseCase
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Test

class PaymentConfirmedHandlerTest {

    private val mapper = ObjectMapper()
    private val useCase = mockk<OrderListenerUseCase>(relaxed = true)

    private fun envelope(payload: String) = EventEnvelope(
        eventId = UUID.randomUUID(),
        eventType = "PaymentConfirmed",
        eventVersion = 1,
        occurredAt = Instant.parse("2026-07-18T14:03:00Z"),
        payload = mapper.readTree(payload),
    )

    @Test
    fun `translates the envelope into a typed use case call`() {
        val orderId = UUID.randomUUID()
        val e = envelope("""{"orderId":"$orderId","amount":209.90}""")

        PaymentConfirmedHandler(useCase).handle(e)

        verify { useCase.confirmPayment(orderId, BigDecimal("209.9"), e.eventId) }
    }

    @Test
    fun `tolerates a missing amount`() {
        val orderId = UUID.randomUUID()
        val e = envelope("""{"orderId":"$orderId"}""")

        PaymentConfirmedHandler(useCase).handle(e)

        verify { useCase.confirmPayment(orderId, null, e.eventId) }
    }
}
