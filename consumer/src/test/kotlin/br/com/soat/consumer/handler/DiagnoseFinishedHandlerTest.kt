package br.com.soat.consumer.handler

import br.com.soat.consumer.EventEnvelope
import br.com.soat.order.OrderListenerUseCase
import br.com.soat.order.model.QuotedService
import br.com.soat.order.model.QuotedSupply
import br.com.soat.order.model.request.FinishedDiagnosis
import br.com.soat.shared.model.User
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DiagnoseFinishedHandlerTest {

    private val mapper = ObjectMapper().findAndRegisterModules()
        .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true)
        .configure(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES, false)
    private val useCase = mockk<OrderListenerUseCase>(relaxed = true)

    @Test
    fun `translates the frozen contract envelope into the finished diagnosis`() {
        val envelope = mapper.readValue(DIAGNOSE_FINISHED_ENVELOPE, EventEnvelope::class.java)

        DiagnoseFinishedHandler(useCase).handle(envelope)

        verify {
            useCase.awaitApproval(
                FinishedDiagnosis(
                    orderId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    reservationId = UUID.fromString("44444444-4444-4444-4444-444444444444"),
                    diagnosedBy = User(
                        UUID.fromString("00000000-0000-0000-0000-000000000003"),
                        "12345678909",
                    ),
                    services = listOf(
                        QuotedService(
                            UUID.fromString("55555555-5555-5555-5555-555555555555"),
                            "Troca de oleo",
                            BigDecimal("100.00"),
                        ),
                    ),
                    supplies = listOf(
                        QuotedSupply(
                            UUID.fromString("66666666-6666-6666-6666-666666666666"),
                            "Filtro de oleo",
                            2,
                            BigDecimal("30.00"),
                        ),
                    ),
                    totalAmount = BigDecimal("160.00"),
                ),
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
            )
        }
    }

    @Test
    fun `subscribes only to DiagnoseFinished`() {
        assertEquals(setOf("DiagnoseFinished"), DiagnoseFinishedHandler(useCase).eventTypes)
    }
}
