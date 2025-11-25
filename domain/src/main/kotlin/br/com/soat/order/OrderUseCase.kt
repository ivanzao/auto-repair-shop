package br.com.soat.order

import br.com.soat.customer.CustomerRepository
import br.com.soat.event.order.OrderDiagnoseFinishedEvent
import br.com.soat.order.command.SendQuoteToClientCommand
import br.com.soat.order.model.Order
import br.com.soat.order.model.request.CreateOrderRequest
import br.com.soat.order.model.request.FinishOrderDiagnosisRequest
import br.com.soat.order.model.request.StartOrderDiagnosisRequest
import br.com.soat.service.ServiceRepository
import br.com.soat.service.model.Service
import br.com.soat.shared.repository.CommandRepository
import br.com.soat.shared.repository.EventRepository
import br.com.soat.shared.repository.RepositoryTransactionManager
import br.com.soat.user.UserRepository
import br.com.soat.vehicle.VehicleRepository
import java.util.UUID

class OrderUseCase(
    private val customerRepository: CustomerRepository,
    private val vehicleRepository: VehicleRepository,
    private val userRepository: UserRepository,
    private val orderRepository: OrderRepository,
    private val serviceRepository: ServiceRepository,
    private val eventRepository: EventRepository,
    private val commandRepository: CommandRepository,
    private val tx: RepositoryTransactionManager
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

    fun startDiagnosis(request: StartOrderDiagnosisRequest): Order {
        val order = orderRepository.findById(request.orderId)
            ?: throw IllegalArgumentException("Order not found ${request.orderId}")

        return orderRepository.update(order.inDiagnosis())
    }

    fun finishDiagnosis(request: FinishOrderDiagnosisRequest): Order {
        val order = orderRepository.findById(request.orderId)
            ?: throw IllegalArgumentException("Order not found ${request.orderId}")

        val services = serviceRepository.findAllByIds(request.servicesIds)
        validateRequestedServicesExists(services, request.servicesIds)

        val orderWithServices = order.addServices(services)

        return tx.inTransaction {
            eventRepository.save(OrderDiagnoseFinishedEvent(orderId = order.id))
            orderRepository.update(orderWithServices)
        }
    }

    fun sendQuoteToApproval(orderId: UUID) {
        val order = orderRepository.findById(orderId)
            ?: throw IllegalArgumentException("Order not found $orderId")

        tx.inTransaction {
            commandRepository.save(SendQuoteToClientCommand(orderId = orderId, idempotencyId = orderId))
            orderRepository.update(order.waitingApproval())
        }
    }

    fun findById(orderId: UUID): Order? {
        return orderRepository.findById(orderId)
    }

    private fun validateRequestedServicesExists(foundServices: List<Service>, servicesIds: List<UUID>) {
        servicesIds.filter { it !in foundServices.map { service -> service.id } }
            .takeIf { it.isNotEmpty() }
            ?.let { throw IllegalArgumentException("Requested services not found: ${it.joinToString(", ")}") }
    }
}