package br.com.soat.consumer.handler

import br.com.soat.consumer.EventEnvelope
import br.com.soat.order.OrderListenerUseCase
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExecutionStartedHandlerTest {

    private val mapper = ObjectMapper().findAndRegisterModules()
    private val useCase = mockk<OrderListenerUseCase>(relaxed = true)

    @Test
    fun `moves the order status instead of only logging`() {
        val envelope = mapper.readValue(EXECUTION_STARTED_ENVELOPE, EventEnvelope::class.java)

        ExecutionStartedHandler(useCase).handle(envelope)

        verify {
            useCase.startExecution(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
            )
        }
    }

    @Test
    fun `subscribes only to ExecutionStarted`() {
        assertEquals(setOf("ExecutionStarted"), ExecutionStartedHandler(useCase).eventTypes)
    }
}
