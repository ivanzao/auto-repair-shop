package br.com.soat.bdd

import br.com.soat.attendant.AttendantRepository
import br.com.soat.attendant.model.Attendant
import br.com.soat.consumer.InboundEventConsumer
import br.com.soat.customer.CustomerRepository
import br.com.soat.customer.model.Customer
import br.com.soat.messaging.OutboxRelay
import br.com.soat.order.OrderUseCase
import br.com.soat.order.model.request.CreateOrderRequest
import br.com.soat.order.repository.OrderRepository
import br.com.soat.service.model.Service
import br.com.soat.service.repository.ServiceRepository
import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber
import br.com.soat.shared.vo.VehiclePlate
import br.com.soat.vehicle.VehicleRepository
import br.com.soat.vehicle.model.Vehicle
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.java.Before
import io.cucumber.java.pt.Dado
import io.cucumber.java.pt.Entao
import io.cucumber.java.pt.Quando
import java.math.BigDecimal
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull

class OrderFlowSteps {

    private val mapper: ObjectMapper = ObjectMapper().findAndRegisterModules()
    private lateinit var orderId: UUID

    @Before
    fun setup() {
        BddContext.start()
        BddContext.reset()
    }

    @Dado("uma ordem de serviço criada")
    fun umaOrdemCriada() {
        val customer = BddContext.get<CustomerRepository>().create(
            Customer(name = "John", document = Document("12345678909"), email = Email("john@example.com"), contact = PhoneNumber("11987654321")),
        )
        val vehicle = BddContext.get<VehicleRepository>().create(
            Vehicle(clientId = customer.id, plate = VehiclePlate("ABC1234"), brand = "Toyota", model = "Corolla", year = 2024),
        )
        val attendant = BddContext.get<AttendantRepository>().create(
            Attendant(name = "Att", document = Document("11122233344"), email = Email("att@test.com"), contact = PhoneNumber("11999990000")),
        )
        val service = BddContext.get<ServiceRepository>().create(
            Service(name = "Repair", description = null, price = BigDecimal("149.90")),
        )
        val order = BddContext.get<OrderUseCase>().create(
            CreateOrderRequest(
                attendantId = attendant.id,
                customerId = customer.id,
                vehicleId = vehicle.id,
                description = "engine noise",
                servicesIds = listOf(service.id),
            ),
        )
        orderId = order.id
    }

    @Entao("o evento {string} é publicado no tópico de eventos do order")
    fun eventoPublicado(eventType: String) {
        BddContext.get<OutboxRelay>().relayPending()
        val envelope = BddContext.receiveFromTopic(mapper)
        assertNotNull(envelope, "nenhum evento publicado no tópico")
        assertEquals(eventType, envelope!!["eventType"].asText())
        assertEquals(orderId.toString(), envelope["payload"]["orderId"].asText())
    }

    @Quando("o billing publica {string} para a ordem")
    fun billingPublica(eventType: String) = injetar(eventType)

    @Quando("o execution publica {string} para a ordem")
    fun executionPublica(eventType: String) = injetar(eventType)

    @Entao("a ordem fica com status {string}")
    fun ordemComStatus(status: String) {
        val order = BddContext.get<OrderRepository>().findById(orderId)
        assertNotNull(order, "ordem não encontrada")
        assertEquals(status, order!!.status.name)
    }

    private fun injetar(eventType: String) {
        BddContext.sendInbound(envelopeFor(eventType))
        BddContext.get<InboundEventConsumer>().pollOnce()
    }

    private fun envelopeFor(eventType: String): String {
        val payload = when (eventType) {
            "PaymentConfirmed" -> """{"orderId":"$orderId","paymentId":"pay-1","amount":149.90}"""
            "PartsUnavailable" -> """{"orderId":"$orderId","missingParts":[]}"""
            else -> """{"orderId":"$orderId"}"""
        }
        return """{"eventId":"${UUID.randomUUID()}","eventType":"$eventType","eventVersion":1,"occurredAt":"2026-07-18T14:03:00Z","payload":$payload}"""
    }
}
