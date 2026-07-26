package br.com.soat.bdd

import br.com.soat.consumer.InboundEventConsumer
import br.com.soat.customer.repository.CustomerRepository
import br.com.soat.customer.model.Customer
import br.com.soat.order.OrderUseCase
import br.com.soat.order.model.request.CreateOrderRequest
import br.com.soat.order.repository.OrderRepository
import br.com.soat.shared.model.User
import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber
import br.com.soat.shared.vo.VehiclePlate
import br.com.soat.vehicle.repository.VehicleRepository
import br.com.soat.vehicle.model.Vehicle
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature
import io.cucumber.java.Before
import io.cucumber.java.pt.Dado
import io.cucumber.java.pt.Entao
import io.cucumber.java.pt.Quando
import java.math.BigDecimal
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull

class OrderFlowSteps {

    private val mapper: ObjectMapper = ObjectMapper().findAndRegisterModules()
        .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true)
        .configure(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES, false)
    private val reservationId = UUID.fromString("44444444-4444-4444-4444-444444444444")
    private val serviceId = UUID.fromString("55555555-5555-5555-5555-555555555555")
    private val supplyId = UUID.fromString("66666666-6666-6666-6666-666666666666")
    private val diagnosedById = UUID.fromString("00000000-0000-0000-0000-000000000003")
    private lateinit var orderId: UUID

    @Before
    fun setup() {
        BddContext.start()
        BddContext.reset()
    }

    @Dado("uma ordem de serviço criada")
    fun umaOrdemCriada() {
        val customer = BddContext.get<CustomerRepository>().create(
            Customer(
                name = "Maria Silva",
                document = Document("12345678909"),
                email = Email("maria@exemplo.com"),
                contact = PhoneNumber("11987654321"),
            ),
        )
        val vehicle = BddContext.get<VehicleRepository>().create(
            Vehicle(
                clientId = customer.id,
                plate = VehiclePlate("ABC1234"),
                brand = "Volkswagen",
                model = "Gol 1.6",
                year = 2024,
            ),
        )
        val order = BddContext.get<OrderUseCase>().create(
            CreateOrderRequest(
                openedBy = User(UUID.randomUUID(), "12345678909"),
                customerId = customer.id,
                vehicleId = vehicle.id,
                description = "engine noise",
            ),
        )
        orderId = order.id
    }

    @Entao("o evento {string} é publicado no tópico de eventos do order")
    fun eventoPublicado(eventType: String) {
        val envelope = BddContext.receiveFromTopic(mapper, eventType)
        assertNotNull(envelope, "nenhum evento $eventType publicado no tópico")
        assertEquals(eventType, envelope!!["eventType"].asText())
        assertEquals(orderId.toString(), envelope["payload"]["orderId"].asText())
    }

    @Entao("o evento {string} carrega a reserva e o total do diagnóstico")
    fun eventoCarregaOrcamento(eventType: String) {
        val envelope = BddContext.receiveFromTopic(mapper, eventType)
        assertNotNull(envelope, "nenhum evento $eventType publicado no tópico")

        val payload = envelope!!["payload"]
        assertEquals(orderId.toString(), payload["orderId"].asText())
        assertEquals(reservationId.toString(), payload["reservationId"].asText())
        assertEquals("Maria Silva", payload["customer"]["name"].asText())
        assertEquals("maria@exemplo.com", payload["customer"]["email"].asText())
        assertEquals(serviceId.toString(), payload["services"][0]["id"].asText())
        assertEquals("Troca de oleo", payload["services"][0]["name"].asText())
        assertEquals(BigDecimal("100.00"), payload["services"][0]["price"].decimalValue())
        assertEquals(supplyId.toString(), payload["supplies"][0]["id"].asText())
        assertEquals("Filtro de oleo", payload["supplies"][0]["name"].asText())
        assertEquals(2, payload["supplies"][0]["quantity"].asInt())
        assertEquals(BigDecimal("30.00"), payload["supplies"][0]["unitPrice"].decimalValue())
        assertEquals(BigDecimal("160.00"), payload["totalAmount"].decimalValue())

        assertEquals(
            setOf("orderId", "reservationId", "customer", "services", "supplies", "totalAmount"),
            payload.fieldNames().asSequence().toSet(),
            "o payload publicado carrega exatamente as chaves do contrato v2",
        )
        assertNull(payload["diagnosedBy"], "o contrato não repassa diagnosedBy ao billing")
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

    @Entao("a ordem guarda o snapshot precificado")
    fun ordemGuardaSnapshot() {
        val order = BddContext.get<OrderRepository>().findById(orderId)!!
        assertEquals(listOf(serviceId), order.services.map { it.id })
        assertEquals(BigDecimal("100.00"), order.services.single().price)
        assertEquals(listOf(supplyId), order.supplies.map { it.id })
        assertEquals(2, order.supplies.single().quantity)
        assertEquals(BigDecimal("30.00"), order.supplies.single().unitPrice)
    }

    @Entao("a ordem ainda não sabe quem diagnosticou")
    fun ordemSemDiagnostico() {
        val order = BddContext.get<OrderRepository>().findById(orderId)!!
        assertNull(order.diagnosedBy)
    }

    @Entao("a ordem registra quem diagnosticou")
    fun ordemRegistraDiagnostico() {
        val order = BddContext.get<OrderRepository>().findById(orderId)!!
        assertNotNull(order.diagnosedBy, "o diagnosedBy tem de sobreviver à releitura do banco")
        assertEquals(diagnosedById, order.diagnosedBy!!.id)
        assertEquals("12345678909", order.diagnosedBy!!.document)
    }

    private fun injetar(eventType: String) {
        BddContext.sendInbound(envelopeFor(eventType))
        BddContext.get<InboundEventConsumer>().poll()
    }

    private fun envelopeFor(eventType: String): String {
        val payload = when (eventType) {
            "DiagnoseFinished" ->
                """{"orderId":"$orderId","reservationId":"$reservationId",""" +
                    """"diagnosedBy":{"id":"$diagnosedById","document":"12345678909"},""" +
                    """"customer":{"name":"Maria Silva","email":"maria@exemplo.com"},""" +
                    """"services":[{"id":"$serviceId","name":"Troca de oleo","price":100.00}],""" +
                    """"supplies":[{"id":"$supplyId","name":"Filtro de oleo","quantity":2,"unitPrice":30.00}],""" +
                    """"totalAmount":160.00}"""

            "PaymentConfirmed" ->
                """{"orderId":"$orderId","paymentId":"pay-1","amount":160.00}"""

            "SuppliesUnavailable" ->
                """{"orderId":"$orderId","missingSupplies":[""" +
                    """{"supplyId":"$supplyId","name":"Filtro de oleo","requested":4,"available":1}]}"""

            else -> """{"orderId":"$orderId"}"""
        }
        return """{"eventId":"${UUID.randomUUID()}","eventType":"$eventType","eventVersion":1,""" +
            """"occurredAt":"2026-07-25T18:00:00Z","payload":$payload}"""
    }
}
