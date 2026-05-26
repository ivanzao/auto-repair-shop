package br.com.soat.order

import br.com.soat.customer.CustomerRepository
import br.com.soat.customer.exception.CustomerNotFoundException
import br.com.soat.event.EventPublisher
import br.com.soat.event.repository.EventRepository
import br.com.soat.order.exception.IllegalOrderCommandException
import br.com.soat.order.exception.InvalidOrderApprovalTokenException
import br.com.soat.order.exception.OrderNotFoundException
import br.com.soat.order.model.Order
import br.com.soat.order.model.OrderMetrics
import br.com.soat.order.model.OrderSchedule
import br.com.soat.order.event.OrderCompletedEvent
import br.com.soat.order.event.OrderDiagnoseFinishedEvent
import br.com.soat.order.event.OrderInProgressEvent
import br.com.soat.order.exception.ServiceNotFoundException
import br.com.soat.order.model.request.CreateOrderRequest
import br.com.soat.order.model.request.FinishOrderDiagnosisRequest
import br.com.soat.order.model.request.ScheduleOrderVehicleRequest
import br.com.soat.order.model.request.StartOrderDiagnosisRequest
import br.com.soat.order.repository.OrderApprovalTokenRepository
import br.com.soat.order.repository.OrderExecutionMetricRepository
import br.com.soat.order.repository.OrderRepository
import br.com.soat.order.repository.OrderScheduleRepository
import br.com.soat.service.model.Service
import br.com.soat.service.repository.ServiceRepository
import br.com.soat.shared.model.Page
import br.com.soat.shared.repository.RepositoryTransactionHandler
import br.com.soat.attendant.AttendantRepository
import br.com.soat.attendant.exception.AttendantNotFoundException
import br.com.soat.vehicle.VehicleRepository
import br.com.soat.vehicle.exception.VehicleNotFoundException
import br.com.soat.metric.MetricsPort
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import java.util.UUID.randomUUID
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory

class OrderUseCase(
    private val customerRepository: CustomerRepository,
    private val vehicleRepository: VehicleRepository,
    private val attendantRepository: AttendantRepository,
    private val orderRepository: OrderRepository,
    private val serviceRepository: ServiceRepository,
    private val eventRepository: EventRepository,
    private val orderScheduleRepository: OrderScheduleRepository,
    private val orderApprovalTokenRepository: OrderApprovalTokenRepository,
    private val orderExecutionMetricRepository: OrderExecutionMetricRepository,
    private val eventPublisher: EventPublisher,
    private val tx: RepositoryTransactionHandler,
    metrics: MetricsPort,
) {

    private val logger = LoggerFactory.getLogger(OrderUseCase::class.java)

    private val ordersCreated: MetricsPort.Counter = metrics.counter(
        name = "orders_created_total",
        description = "Total de ordens de serviço criadas",
    )

    private val ordersByStatus: Map<Order.Status, MetricsPort.Counter> =
        Order.Status.entries.associateWith { status ->
            metrics.counter(
                name = "orders_by_status_total",
                description = "Total de transições de ordens de serviço para cada status",
                tags = mapOf("status" to status.name),
            )
        }

    fun findById(orderId: UUID) = orderRepository.findById(orderId)
    fun findAll(page: Int): Page<Order> = orderRepository.findAllPaginated(page)
    fun getMetrics(): OrderMetrics = orderExecutionMetricRepository.getMetrics()

    fun create(request: CreateOrderRequest): Order {
        val customer = customerRepository.findById(request.customerId)
            ?: throw CustomerNotFoundException(request.customerId)

        val vehicle = vehicleRepository.findById(request.vehicleId)
            ?: throw VehicleNotFoundException(request.vehicleId)

        val attendant = attendantRepository.findById(request.attendantId)
            ?: throw AttendantNotFoundException(request.attendantId)

        val services = serviceRepository.findAllByIds(request.servicesIds)
        validateRequestedServicesExists(services, request.servicesIds)

        val order = Order(
            customer = customer,
            vehicle = vehicle,
            attendantId = attendant.id,
            description = request.description,
        ).addServices(services)
            .addSupplyRequirements(request.extraSupplyRequirements)

        val created = orderRepository.create(order)
        ordersCreated.increment()
        ordersByStatus[created.status]?.increment()
        logger.info(
            "Order created",
            kv("event", "order.created"),
            kv("orderId", created.id),
            kv("customerId", customer.id),
            kv("vehicleId", vehicle.id),
            kv("attendantId", attendant.id),
            kv("to_status", created.status.name),
        )
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

    fun startDiagnosis(request: StartOrderDiagnosisRequest): Order {
        val order = orderRepository.findById(request.orderId)
            ?: throw OrderNotFoundException(request.orderId)

        val previousStatus = order.status
        val durationInPrevious = Duration.between(order.modifiedAt, LocalDateTime.now())
        val updated = orderRepository.update(order.inDiagnosis(request.technician))
        ordersByStatus[updated.status]?.increment()
        logger.info(
            "Order diagnosis started",
            kv("event", "order.status_changed"),
            kv("orderId", updated.id),
            kv("from_status", previousStatus.name),
            kv("to_status", updated.status.name),
            kv("duration_in_previous_seconds", durationInPrevious.seconds),
            kv("technician", request.technician),
        )
        return updated
    }

    fun finishDiagnosis(request: FinishOrderDiagnosisRequest): Order {
        val order = orderRepository.findById(request.orderId) ?: throw OrderNotFoundException(request.orderId)

        val services = serviceRepository.findAllByIds(request.servicesIds)
        validateRequestedServicesExists(services, request.servicesIds)

        val orderWithServices = order
            .addServices(services)
            .addSupplyRequirements(request.extraSupplyRequirements)

        val previousStatus = order.status
        val durationInPrevious = Duration.between(order.modifiedAt, LocalDateTime.now())
        val (updatedOrder, event) = tx.inTransaction {
            val updated = orderRepository.update(orderWithServices)
            val savedEvent = eventRepository.save(OrderDiagnoseFinishedEvent(orderId = updated.id))
            updated to savedEvent
        }

        eventPublisher.publish(event)
        ordersByStatus[updatedOrder.status]?.increment()
        logger.info(
            "Order diagnosis finished",
            kv("event", "order.status_changed"),
            kv("orderId", updatedOrder.id),
            kv("from_status", previousStatus.name),
            kv("to_status", updatedOrder.status.name),
            kv("duration_in_previous_seconds", durationInPrevious.seconds),
        )

        return updatedOrder
    }

    fun complete(orderId: UUID): Order {
        val order = orderRepository.findById(orderId) ?: throw IllegalArgumentException("Order not found $orderId")
        val previousStatus = order.status
        val durationInPrevious = Duration.between(order.modifiedAt, LocalDateTime.now())
        val (savedOrder, event) = tx.inTransaction {
            val updated = orderRepository.update(order.completed())
            val savedEvent = eventRepository.save(OrderCompletedEvent(orderId = updated.id))
            updated to savedEvent
        }

        eventPublisher.publish(event)
        ordersByStatus[savedOrder.status]?.increment()
        logger.info(
            "Order completed",
            kv("event", "order.status_changed"),
            kv("orderId", savedOrder.id),
            kv("from_status", previousStatus.name),
            kv("to_status", savedOrder.status.name),
            kv("duration_in_previous_seconds", durationInPrevious.seconds),
        )

        return savedOrder
    }

    fun deliver(orderId: UUID): Order {
        val order = orderRepository.findById(orderId) ?: throw OrderNotFoundException(orderId)
        val previousStatus = order.status
        val durationInPrevious = Duration.between(order.modifiedAt, LocalDateTime.now())
        val delivered = orderRepository.update(order.delivered())
        ordersByStatus[delivered.status]?.increment()
        logger.info(
            "Order delivered",
            kv("event", "order.status_changed"),
            kv("orderId", delivered.id),
            kv("from_status", previousStatus.name),
            kv("to_status", delivered.status.name),
            kv("duration_in_previous_seconds", durationInPrevious.seconds),
        )
        return delivered
    }

    fun approveQuote(approvalTokenId: UUID) {
        val approvalToken = findValidApprovalToken(approvalTokenId)
            ?: throw InvalidOrderApprovalTokenException()

        val order = orderRepository.findById(approvalToken.orderId)
            ?: throw OrderNotFoundException(approvalToken.orderId)

        val previousStatus = order.status
        val durationInPrevious = Duration.between(order.modifiedAt, LocalDateTime.now())
        val event = tx.inTransaction {
            orderApprovalTokenRepository.update(approvalToken.markAsUsed())
            orderRepository.update(order.inProgress())
            eventRepository.save(OrderInProgressEvent(orderId = order.id))
        }

        eventPublisher.publish(event)
        ordersByStatus[Order.Status.IN_PROGRESS]?.increment()
        logger.info(
            "Order quote approved",
            kv("event", "order.status_changed"),
            kv("orderId", order.id),
            kv("from_status", previousStatus.name),
            kv("to_status", Order.Status.IN_PROGRESS.name),
            kv("duration_in_previous_seconds", durationInPrevious.seconds),
        )
    }

    fun declineQuote(approvalTokenId: UUID) {
        val approvalToken = findValidApprovalToken(approvalTokenId)
            ?: throw InvalidOrderApprovalTokenException()

        val order = orderRepository.findById(approvalToken.orderId)
            ?: throw OrderNotFoundException(approvalToken.orderId)

        val previousStatus = order.status
        val durationInPrevious = Duration.between(order.modifiedAt, LocalDateTime.now())
        tx.inTransaction {
            orderRepository.update(order.canceled())
            orderApprovalTokenRepository.update(approvalToken.markAsUsed())
        }
        ordersByStatus[Order.Status.CANCELED]?.increment()
        logger.info(
            "Order quote declined",
            kv("event", "order.status_changed"),
            kv("orderId", order.id),
            kv("from_status", previousStatus.name),
            kv("to_status", Order.Status.CANCELED.name),
            kv("duration_in_previous_seconds", durationInPrevious.seconds),
        )
    }

    private fun findValidApprovalToken(approvalTokenId: UUID) =
        orderApprovalTokenRepository.findById(approvalTokenId)
            ?.takeIf { it.isValid() }

    private fun validateRequestedServicesExists(foundServices: List<Service>, servicesIds: List<UUID>) {
        servicesIds.firstOrNull { it !in foundServices.map { service -> service.id } }
            ?.let { throw ServiceNotFoundException(it) }
    }
}