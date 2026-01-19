package br.com.soat.order

import br.com.soat.customer.CustomerRepository
import br.com.soat.event.EventPublisher
import br.com.soat.event.repository.EventRepository
import br.com.soat.order.model.Order
import br.com.soat.order.model.OrderMetrics
import br.com.soat.order.model.OrderSchedule
import br.com.soat.order.model.OrderService
import br.com.soat.order.model.event.OrderCompletedEvent
import br.com.soat.order.model.event.OrderDiagnoseFinishedEvent
import br.com.soat.order.model.event.OrderInProgressEvent
import br.com.soat.order.repository.OrderApprovalTokenRepository
import br.com.soat.order.repository.OrderExecutionMetricRepository
import br.com.soat.order.repository.OrderRepository
import br.com.soat.order.repository.OrderScheduleRepository
import br.com.soat.order.repository.OrderServiceRepository
import br.com.soat.order.model.request.CreateOrderRequest
import br.com.soat.order.model.request.FinishOrderDiagnosisRequest
import br.com.soat.order.model.request.ScheduleOrderVehicleRequest
import br.com.soat.order.model.request.StartOrderDiagnosisRequest
import br.com.soat.shared.model.Page
import br.com.soat.shared.repository.RepositoryTransactionHandler
import br.com.soat.user.UserRepository
import br.com.soat.vehicle.VehicleRepository
import java.util.UUID
import java.util.UUID.randomUUID

class OrderUseCase(
    private val customerRepository: CustomerRepository,
    private val vehicleRepository: VehicleRepository,
    private val userRepository: UserRepository,
    private val orderRepository: OrderRepository,
    private val serviceRepository: OrderServiceRepository,
    private val eventRepository: EventRepository,
    private val orderScheduleRepository: OrderScheduleRepository,
    private val orderApprovalTokenRepository: OrderApprovalTokenRepository,
    private val orderExecutionMetricRepository: OrderExecutionMetricRepository,
    private val eventPublisher: EventPublisher,
    private val tx: RepositoryTransactionHandler
) {

    fun create(request: CreateOrderRequest): Order {
        val customer = customerRepository.findById(request.customerId)
            ?: throw IllegalArgumentException("Customer not found ${request.customerId}")

        val vehicle = vehicleRepository.findById(request.vehicleId)
            ?: throw IllegalArgumentException("Vehicle not found ${request.vehicleId}")

        val attendant = userRepository.findById(request.attendantId)
            ?: throw IllegalArgumentException("Attendant not found ${request.attendantId}")

        return orderRepository.create(
            Order(
                customer = customer,
                vehicle = vehicle,
                attendant = attendant,
                description = request.description,
            )
        )
    }

    fun scheduleVehicleDelivery(request: ScheduleOrderVehicleRequest) {
        val order = orderRepository.findById(request.orderId)
            ?: throw IllegalArgumentException("Order not found ${request.orderId}")

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
        val order = orderRepository.findById(request.orderId)
            ?: throw IllegalArgumentException("Order not found ${request.orderId}")

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
            ?: throw IllegalArgumentException("Order not found ${request.orderId}")

        return orderRepository.update(order.inDiagnosis(request.technician))
    }

    fun finishDiagnosis(request: FinishOrderDiagnosisRequest): Order {
        val order = orderRepository.findById(request.orderId)
            ?: throw IllegalArgumentException("Order not found ${request.orderId}")

        val services = serviceRepository.findAllByIds(request.servicesIds)
        validateRequestedServicesExists(services, request.servicesIds)

        val orderWithServices = order.addServices(services)
            .addSupplyRequirements(request.extraSuppliesRequests)

        val (updatedOrder, event) = tx.inTransaction {
            val order = orderRepository.update(orderWithServices)
            val event = eventRepository.save(OrderDiagnoseFinishedEvent(orderId = order.id))
            order to event
        }

        eventPublisher.publish(event)

        return updatedOrder
    }

    fun findById(orderId: UUID): Order? {
        return orderRepository.findById(orderId)
    }

    fun findAll(page: Int): Page<Order> = orderRepository.findAllPaginated(page)

    fun getMetrics(): OrderMetrics = orderExecutionMetricRepository.getMetrics()

    fun complete(orderId: UUID): Order {
        val order = orderRepository.findById(orderId)
            ?: throw IllegalArgumentException("Order not found $orderId")

        val (savedOrder, event) = tx.inTransaction {
            val order = orderRepository.update(order.completed())
            val event = eventRepository.save(OrderCompletedEvent(orderId = order.id))
            order to event
        }

        eventPublisher.publish(event)

        return savedOrder
    }

    fun approveQuote(approvalTokenId: UUID) {
        val approvalToken = orderApprovalTokenRepository.findById(approvalTokenId)
            ?: throw IllegalArgumentException("Approval token not found $approvalTokenId")

        if (!approvalToken.isValid()) {
            throw IllegalArgumentException("Approval token is invalid or expired")
        }

        val order = orderRepository.findById(approvalToken.orderId)
            ?: throw IllegalArgumentException("Order not found ${approvalToken.orderId}")

        val event = tx.inTransaction {
            orderApprovalTokenRepository.update(approvalToken.markAsUsed())
            orderRepository.update(order.inProgress())
            eventRepository.save(OrderInProgressEvent(orderId = order.id))
        }

        eventPublisher.publish(event)
    }

    fun declineQuote(approvalTokenId: UUID) {
        val approvalToken = orderApprovalTokenRepository.findById(approvalTokenId)
            ?: throw IllegalArgumentException("Approval token not found $approvalTokenId")

        if (!approvalToken.isValid()) {
            throw IllegalArgumentException("Approval token is invalid or expired")
        }

        val order = orderRepository.findById(approvalToken.orderId)
            ?: throw IllegalArgumentException("Order not found ${approvalToken.orderId}")

        tx.inTransaction {
            orderRepository.update(order.canceled())
            orderApprovalTokenRepository.update(approvalToken.markAsUsed())
        }
    }

    private fun validateRequestedServicesExists(foundServices: List<OrderService>, servicesIds: List<UUID>) {
        servicesIds.filter { it !in foundServices.map { service -> service.id } }
            .takeIf { it.isNotEmpty() }
            ?.let { throw IllegalArgumentException("Requested services not found: ${it.joinToString(", ")}") }
    }
}