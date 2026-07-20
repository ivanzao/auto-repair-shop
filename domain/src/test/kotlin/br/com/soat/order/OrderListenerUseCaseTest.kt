package br.com.soat.order

import br.com.soat.customer.model.Customer
import br.com.soat.order.model.Order
import br.com.soat.order.repository.OrderRepository
import br.com.soat.shared.model.Page
import br.com.soat.shared.repository.IdempotencyRepository
import br.com.soat.shared.repository.RepositoryTransactionHandler
import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber
import br.com.soat.shared.vo.VehiclePlate
import br.com.soat.vehicle.model.Vehicle
import java.math.BigDecimal
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OrderListenerUseCaseTest {

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
            name = "John", document = Document("12345678909"),
            email = Email("john@example.com"), contact = PhoneNumber("11999999999"),
        ),
        vehicle = Vehicle(
            clientId = UUID.randomUUID(), plate = VehiclePlate("ABC1234"),
            brand = "Toyota", model = "Corolla", year = 2024,
        ),
        attendantId = UUID.randomUUID(),
        description = "d",
        status = status,
    )

    private fun useCase(
        repo: OrderRepository,
        idempotency: IdempotencyRepository = FakeIdempotency(),
        metrics: OrderMetricsPort = RecordingMetrics(),
    ) = OrderListenerUseCase(repo, idempotency, DirectTransaction, metrics)

    @Test
    fun `confirmPayment moves RECEIVED to IN_PROGRESS`() {
        val o = order(Order.Status.RECEIVED)
        val repo = FakeOrderRepository(o)
        useCase(repo).confirmPayment(o.id, BigDecimal("209.90"), UUID.randomUUID())
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
    fun `cancel cancels an IN_PROGRESS order`() {
        val o = order(Order.Status.IN_PROGRESS)
        val repo = FakeOrderRepository(o)
        useCase(repo).cancel(o.id, "ExecutionFailed", UUID.randomUUID())
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
        val o = order(Order.Status.RECEIVED)
        val repo = FakeOrderRepository(o)
        val idempotency = FakeIdempotency()
        val eventId = UUID.randomUUID()
        val uc = useCase(repo, idempotency)

        uc.confirmPayment(o.id, BigDecimal("10.00"), eventId)
        uc.confirmPayment(o.id, BigDecimal("10.00"), eventId)

        assertEquals(1, repo.updates, "a segunda entrega não pode escrever de novo")
        assertEquals(Order.Status.IN_PROGRESS, repo.store[o.id]!!.status)
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
    fun `recordExecutionProgress does not change status`() {
        val o = order(Order.Status.IN_PROGRESS)
        val repo = FakeOrderRepository(o)
        val metrics = RecordingMetrics()

        useCase(repo, metrics = metrics).recordExecutionProgress(o.id, "DiagnoseFinished")

        assertEquals(Order.Status.IN_PROGRESS, repo.store[o.id]!!.status)
        assertEquals(0, repo.updates)
        assertEquals(1, metrics.inbound)
    }
}
