package br.com.soat.consumer.handler

import br.com.soat.consumer.EventEnvelope
import br.com.soat.consumer.EventType
import br.com.soat.consumer.InboundEventHandler
import br.com.soat.order.OrderListenerUseCase
import br.com.soat.order.model.QuotedService
import br.com.soat.order.model.QuotedSupply
import br.com.soat.order.model.request.FinishedDiagnosis
import br.com.soat.shared.model.User
import java.util.UUID

class DiagnoseFinishedHandler(
    private val orderListenerUseCase: OrderListenerUseCase,
) : InboundEventHandler {

    override val eventTypes = setOf(EventType.DIAGNOSE_FINISHED)

    override fun handle(envelope: EventEnvelope) {
        val payload = envelope.payload
        val diagnosedBy = payload.get("diagnosedBy")
        val diagnosis = FinishedDiagnosis(
            orderId = envelope.orderId(),
            reservationId = UUID.fromString(payload.get("reservationId").asText()),
            diagnosedBy = User(
                id = UUID.fromString(diagnosedBy.get("id").asText()),
                document = diagnosedBy.get("document").asText(),
            ),
            services = payload.get("services").map {
                QuotedService(
                    id = UUID.fromString(it.get("id").asText()),
                    name = it.get("name").asText(),
                    price = it.get("price").decimalValue(),
                )
            },
            supplies = payload.get("supplies").map {
                QuotedSupply(
                    id = UUID.fromString(it.get("id").asText()),
                    name = it.get("name").asText(),
                    quantity = it.get("quantity").asInt(),
                    unitPrice = it.get("unitPrice").decimalValue(),
                )
            },
            totalAmount = payload.get("totalAmount").decimalValue(),
        )
        orderListenerUseCase.awaitApproval(diagnosis, envelope.eventId)
    }
}
