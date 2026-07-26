package br.com.soat.consumer.handler

import br.com.soat.consumer.EventEnvelope
import br.com.soat.order.OrderListenerUseCase
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OrderCancellationHandlerTest {

    private val mapper = ObjectMapper().findAndRegisterModules()
    private val useCase = mockk<OrderListenerUseCase>(relaxed = true)

    @Test
    fun `cancels the order on the renamed SuppliesUnavailable`() {
        val envelope = mapper.readValue(SUPPLIES_UNAVAILABLE_ENVELOPE, EventEnvelope::class.java)

        OrderCancellationHandler(useCase).handle(envelope)

        verify {
            useCase.cancel(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "SuppliesUnavailable",
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
            )
        }
    }

    @Test
    fun `subscribes to every compensation event`() {
        assertEquals(
            setOf(
                "QuoteRejected",
                "PaymentFailed",
                "SuppliesUnavailable",
                "ExecutionFailed",
                "ReservationExpired",
            ),
            OrderCancellationHandler(useCase).eventTypes,
        )
    }
}
