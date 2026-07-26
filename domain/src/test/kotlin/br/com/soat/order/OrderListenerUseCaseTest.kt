package br.com.soat.order

import br.com.soat.customer.model.Customer
import br.com.soat.event.EventPublisher
import br.com.soat.event.OutboxEvent
import br.com.soat.event.model.DomainEvent
import br.com.soat.event.repository.OutboxRepository
import br.com.soat.order.event.OrderAwaitingApprovalEvent
import br.com.soat.order.model.Order
import br.com.soat.order.model.QuotedService
import br.com.soat.order.model.QuotedSupply
import br.com.soat.order.model.request.FinishedDiagnosis
import br.com.soat.order.repository.OrderRepository
import br.com.soat.shared.model.Page
import br.com.soat.shared.model.User
import br.com.soat.shared.repository.IdempotencyRepository
import br.com.soat.shared.repository.RepositoryTransactionHandler
import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber
import br.com.soat.shared.vo.VehiclePlate
import br.com.soat.vehicle.model.Vehicle
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OrderListenerUseCaseTest {

    private val serviceId = UUID.fromString("55555555-5555-5555-5555-555555555555")
    private val supplyId = UUID.fromString("66666666-6666-6666-6666-666666666666")
    private val reservationId = UUID.fromString("44444444-4444-4444-4444-444444444444")
    private val diagnosedBy = User(UUID.fromString("00000000-0000-0000-0000-000000000003"), "12345678909")

    private class FakeOrderRepository(seed: Order) : OrderRepository {
        val store = mutableMapOf(seed.id to seed)
        var updates = 0
        override fun findById(id: UUID): Order? = store[id]
        override fun findAllPaginated(page: Int): Page<Order> = throw UnsupportedOperationException()
        override fun create(order: Order): Order { store[order.id] = order; return order }
        override fun update(order: Order): Order { updates++; store[order.id] = order; return order }
    }

    private class FakeIdempotency : IdempotencyRepository {
        val seen = mutableSetOf<Pair<UUID, UUID>>()
        override fun exists(entityId: UUID, idempotencyId: UUID) = (entityId to idempotencyId) in seen
        override fun save(entityId: UUID, idempotencyId: UUID) { seen += entityId to idempotencyId }
    }

    private class FakeOutbox : OutboxRepository {
        val saved = mutableListOf<DomainEvent>()
        override fun save(event: DomainEvent): OutboxEvent {
            saved += event
            return OutboxEvent(event.eventId, event.eventType, event.eventVersion, event.occurredAt, "{}")
        }
        override fun findPendingOlderThan(age: Duration, limit: Int): List<OutboxEvent> = emptyList()
        override fun delete(eventId: UUID) {}
    }

    private class FakePublisher : EventPublisher {
        val published = mutableListOf<OutboxEvent>()
        override fun publish(event: OutboxEvent) { published += event }
    }

    private object DirectTransaction : RepositoryTransactionHandler {
        override fun <T> inTransaction(function: () -> T): T = function()
    }

    private class RecordingMetrics : OrderMetricsPort {
        var inbound = 0
        var statusChanges = 0
        override fun orderCreated() {}
        override fun statusChanged(status: Order.Status) { statusChanges++ }
        override fun inboundEventApplied() { inbound++ }
    }

    private fun order(status: Order.Status): Order = Order(
        customer = Customer(
            name = "Maria Silva", document = Document("12345678909"),
            email = Email("maria@exemplo.com"), contact = PhoneNumber("11999999999"),
        ),
        vehicle = Vehicle(
            clientId = UUID.randomUUID(), plate = VehiclePlate("ABC1234"),
            brand = "Volkswagen", model = "Gol 1.6", year = 2024,
        ),
        openedBy = User(UUID.randomUUID(), "12345678909"),
        description = "engine noise",
        status = status,
    )

    private fun diagnosis(orderId: UUID) = FinishedDiagnosis(
        orderId = orderId,
        reservationId = reservationId,
        diagnosedBy = diagnosedBy,
        services = listOf(QuotedService(serviceId, "Troca de oleo", BigDecimal("100.00"))),
        supplies = listOf(QuotedSupply(supplyId, "Filtro de oleo", 2, BigDecimal("30.00"))),
        totalAmount = BigDecimal("160.00"),
    )

    private fun useCase(
        repo: OrderRepository,
        idempotency: IdempotencyRepository = FakeIdempotency(),
        outbox: OutboxRepository = FakeOutbox(),
        publisher: EventPublisher = FakePublisher(),
        metrics: OrderMetricsPort = RecordingMetrics(),
    ) = OrderListenerUseCase(repo, idempotency, outbox, publisher, DirectTransaction, metrics)

    @Test
    fun `awaitApproval moves RECEIVED to WAITING_APPROVAL and stores the priced snapshot`() {
        val o = order(Order.Status.RECEIVED)
        val repo = FakeOrderRepository(o)

        useCase(repo).awaitApproval(diagnosis(o.id), UUID.randomUUID())

        val stored = repo.store[o.id]!!
        assertEquals(Order.Status.WAITING_APPROVAL, stored.status)
        assertEquals(listOf(QuotedService(serviceId, "Troca de oleo", BigDecimal("100.00"))), stored.services)
        assertEquals(listOf(QuotedSupply(supplyId, "Filtro de oleo", 2, BigDecimal("30.00"))), stored.supplies)
    }

    @Test
    fun `awaitApproval stores who diagnosed the order`() {
        val o = order(Order.Status.RECEIVED)
        val repo = FakeOrderRepository(o)
        assertNull(repo.store[o.id]!!.diagnosedBy)

        useCase(repo).awaitApproval(diagnosis(o.id), UUID.randomUUID())

        assertEquals(diagnosedBy, repo.store[o.id]!!.diagnosedBy)
    }

    @Test
    fun `awaitApproval writes OrderAwaitingApproval to the outbox and publishes it`() {
        val o = order(Order.Status.RECEIVED)
        val repo = FakeOrderRepository(o)
        val outbox = FakeOutbox()
        val publisher = FakePublisher()

        useCase(repo, outbox = outbox, publisher = publisher).awaitApproval(diagnosis(o.id), UUID.randomUUID())

        assertEquals(listOf("OrderAwaitingApproval"), outbox.saved.map { it.eventType })
        assertEquals(listOf("OrderAwaitingApproval"), publisher.published.map { it.eventType })
    }

    @Test
    fun `the published OrderAwaitingApproval carries the reservation of the diagnosis`() {
        val o = order(Order.Status.RECEIVED)
        val repo = FakeOrderRepository(o)
        val outbox = FakeOutbox()

        useCase(repo, outbox = outbox).awaitApproval(diagnosis(o.id), UUID.randomUUID())

        val event = outbox.saved.single() as OrderAwaitingApprovalEvent
        assertEquals(diagnosedBy, repo.store[o.id]!!.diagnosedBy, "a OS guarda")
        assertEquals(reservationId, event.reservationId)
        assertEquals(BigDecimal("160.00"), event.totalAmount)
    }

    @Test
    fun `awaitApproval on a wrong status emits nothing`() {
        val o = order(Order.Status.IN_PROGRESS)
        val repo = FakeOrderRepository(o)
        val outbox = FakeOutbox()
        val publisher = FakePublisher()

        useCase(repo, outbox = outbox, publisher = publisher).awaitApproval(diagnosis(o.id), UUID.randomUUID())

        assertEquals(Order.Status.IN_PROGRESS, repo.store[o.id]!!.status)
        assertNull(repo.store[o.id]!!.diagnosedBy, "sem transição, nem o diagnosedBy é gravado")
        assertTrue(outbox.saved.isEmpty())
        assertTrue(publisher.published.isEmpty())
    }

    @Test
    fun `confirmPayment moves WAITING_APPROVAL to EXECUTION_ENQUEUED`() {
        val o = order(Order.Status.WAITING_APPROVAL)
        val repo = FakeOrderRepository(o)
        useCase(repo).confirmPayment(o.id, BigDecimal("160.00"), UUID.randomUUID())
        assertEquals(Order.Status.EXECUTION_ENQUEUED, repo.store[o.id]!!.status)
    }

    @Test
    fun `startExecution moves EXECUTION_ENQUEUED to IN_PROGRESS`() {
        val o = order(Order.Status.EXECUTION_ENQUEUED)
        val repo = FakeOrderRepository(o)
        useCase(repo).startExecution(o.id, UUID.randomUUID())
        assertEquals(Order.Status.IN_PROGRESS, repo.store[o.id]!!.status)
    }

    @Test
    fun `finishExecution completes an IN_PROGRESS order`() {
        val o = order(Order.Status.IN_PROGRESS)
        val repo = FakeOrderRepository(o)
        useCase(repo).finishExecution(o.id, UUID.randomUUID())
        assertEquals(Order.Status.COMPLETED, repo.store[o.id]!!.status)
    }

    @Test
    fun `cancel cancels an order still waiting for diagnosis`() {
        val o = order(Order.Status.RECEIVED)
        val repo = FakeOrderRepository(o)
        useCase(repo).cancel(o.id, "SuppliesUnavailable", UUID.randomUUID())
        assertEquals(Order.Status.CANCELED, repo.store[o.id]!!.status)
    }

    @Test
    fun `cancel does not touch a COMPLETED order`() {
        val o = order(Order.Status.COMPLETED)
        val repo = FakeOrderRepository(o)
        useCase(repo).cancel(o.id, "ExecutionFailed", UUID.randomUUID())
        assertEquals(Order.Status.COMPLETED, repo.store[o.id]!!.status)
    }

    @Test
    fun `a redelivered event is skipped entirely`() {
        val o = order(Order.Status.WAITING_APPROVAL)
        val repo = FakeOrderRepository(o)
        val idempotency = FakeIdempotency()
        val eventId = UUID.randomUUID()
        val uc = useCase(repo, idempotency)

        uc.confirmPayment(o.id, BigDecimal("160.00"), eventId)
        uc.confirmPayment(o.id, BigDecimal("160.00"), eventId)

        assertEquals(1, repo.updates, "a segunda entrega não pode escrever de novo")
        assertEquals(Order.Status.EXECUTION_ENQUEUED, repo.store[o.id]!!.status)
    }

    @Test
    fun `a redelivered DiagnoseFinished does not publish the event twice`() {
        val o = order(Order.Status.RECEIVED)
        val repo = FakeOrderRepository(o)
        val idempotency = FakeIdempotency()
        val publisher = FakePublisher()
        val eventId = UUID.randomUUID()
        val uc = useCase(repo, idempotency, publisher = publisher)

        uc.awaitApproval(diagnosis(o.id), eventId)
        uc.awaitApproval(diagnosis(o.id), eventId)

        assertEquals(1, publisher.published.size)
    }

    @Test
    fun `idempotency is recorded even when the status does not change`() {
        val o = order(Order.Status.IN_PROGRESS)
        val repo = FakeOrderRepository(o)
        val idempotency = FakeIdempotency()
        val eventId = UUID.randomUUID()

        useCase(repo, idempotency).confirmPayment(o.id, null, eventId)

        assertEquals(0, repo.updates, "status inalterado não escreve a order")
        assertTrue(idempotency.exists(o.id, eventId), "mas o evento foi visto")
    }

    @Test
    fun `an unknown order is ignored without throwing`() {
        val repo = FakeOrderRepository(order(Order.Status.RECEIVED))
        useCase(repo).finishExecution(UUID.randomUUID(), UUID.randomUUID())
    }

    @Test
    fun `every applied inbound event is counted`() {
        val o = order(Order.Status.EXECUTION_ENQUEUED)
        val repo = FakeOrderRepository(o)
        val metrics = RecordingMetrics()

        useCase(repo, metrics = metrics).startExecution(o.id, UUID.randomUUID())

        assertEquals(1, metrics.inbound)
        assertEquals(1, metrics.statusChanges)
    }
}
