package br.com.soat.order

import br.com.soat.customer.repository.CustomerRepository
import br.com.soat.customer.exception.CustomerNotFoundException
import br.com.soat.event.EventPublisher
import br.com.soat.event.repository.OutboxRepository
import br.com.soat.order.event.OrderCreatedEvent
import br.com.soat.order.exception.IllegalOrderCommandException
import br.com.soat.order.exception.OrderNotFoundException
import br.com.soat.order.model.Order
import br.com.soat.order.model.OrderMetrics
import br.com.soat.order.model.OrderSchedule
import br.com.soat.order.model.logParams
import br.com.soat.order.model.request.CreateOrderRequest
import br.com.soat.order.model.request.ScheduleOrderVehicleRequest
import br.com.soat.order.repository.OrderExecutionMetricRepository
import br.com.soat.order.repository.OrderRepository
import br.com.soat.order.repository.OrderScheduleRepository
import br.com.soat.shared.model.Page
import br.com.soat.shared.repository.RepositoryTransactionHandler
import br.com.soat.vehicle.repository.VehicleRepository
import br.com.soat.vehicle.exception.VehicleNotFoundException
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import java.util.UUID.randomUUID
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory

class OrderUseCase(
    private val customerRepository: CustomerRepository,
    private val vehicleRepository: VehicleRepository,
    private val orderRepository: OrderRepository,
    private val outbox: OutboxRepository,
    private val orderScheduleRepository: OrderScheduleRepository,
    private val orderExecutionMetricRepository: OrderExecutionMetricRepository,
    private val eventPublisher: EventPublisher,
    private val tx: RepositoryTransactionHandler,
    private val metrics: OrderMetricsPort,
) {

    private val logger = LoggerFactory.getLogger(OrderUseCase::class.java)

    fun findById(orderId: UUID) = orderRepository.findById(orderId)
    fun findAll(page: Int): Page<Order> = orderRepository.findAllPaginated(page)
    fun getMetrics(): OrderMetrics = orderExecutionMetricRepository.getMetrics()

    fun create(request: CreateOrderRequest): Order {
        val customer = customerRepository.findById(request.customerId)
            ?: throw CustomerNotFoundException(request.customerId)

        val vehicle = vehicleRepository.findById(request.vehicleId)
            ?: throw VehicleNotFoundException(request.vehicleId)

        val order = Order(
            customer = customer,
            vehicle = vehicle,
            openedBy = request.openedBy,
            description = request.description,
        )

        val (created, event) = tx.inTransaction {
            val saved = orderRepository.create(order)
            saved to outbox.save(OrderCreatedEvent.from(saved))
        }
        eventPublisher.publish(event)
        metrics.orderCreated()
        metrics.statusChanged(created.status)
        logger.info("Order created", *created.logParams(kv("event", "order.created")))
        return created
    }

    fun scheduleVehicleDelivery(request: ScheduleOrderVehicleRequest) {
        val order = orderRepository.findById(request.orderId) ?: throw OrderNotFoundException(request.orderId)

        if (!order.canScheduleVehicleDelivery())
            throw IllegalOrderCommandException("Cannot schedule vehicle delivery for Order ${order.id}")

        orderScheduleRepository.save(
            OrderSchedule(
                id = randomUUID(),
                orderId = order.id,
                dateTime = request.dateTime,
                type = OrderSchedule.Type.DELIVERY,
            )
        )
    }

    fun scheduleVehicleReturn(request: ScheduleOrderVehicleRequest) {
        val order = orderRepository.findById(request.orderId) ?: throw OrderNotFoundException(request.orderId)

        if (!order.canScheduleVehicleReturn())
            throw IllegalOrderCommandException("Cannot schedule vehicle return for Order ${order.id}")

        orderScheduleRepository.save(
            OrderSchedule(
                id = randomUUID(),
                orderId = order.id,
                dateTime = request.dateTime,
                type = OrderSchedule.Type.RETURN,
            )
        )
    }

    fun deliver(orderId: UUID): Order {
        val order = orderRepository.findById(orderId) ?: throw OrderNotFoundException(orderId)
        val previousStatus = order.status
        val durationInPrevious = Duration.between(order.modifiedAt, LocalDateTime.now())
        val delivered = orderRepository.update(order.delivered())
        metrics.statusChanged(delivered.status)
        logger.info(
            "Order delivered",
            *delivered.logParams(
                kv("event", "order.status_changed"),
                kv("from_status", previousStatus.name),
                kv("duration_in_previous_seconds", durationInPrevious.seconds),
            ),
        )
        return delivered
    }
}
