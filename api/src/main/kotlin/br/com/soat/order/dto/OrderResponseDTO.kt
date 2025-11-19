package br.com.soat.order.dto

import br.com.soat.br.com.soat.customer.Customer
import br.com.soat.br.com.soat.order.model.Order
import br.com.soat.br.com.soat.order.model.Order.Status
import br.com.soat.br.com.soat.order.model.Service
import br.com.soat.br.com.soat.user.model.User
import br.com.soat.br.com.soat.vehicle.Vehicle
import java.time.LocalDateTime
import java.util.UUID

data class OrderResponseDTO(
    val id: UUID,
    val createdAt: LocalDateTime,

    val status: Status,

    val customer: Customer,
    val vehicle: Vehicle,
    val attendant: User,

    val description: String,
    val services: List<Service>,
    val technician: String? = null,
) {
    companion object {
        fun from(order: Order) = OrderResponseDTO(
            id = order.id,
            createdAt = order.createdAt,
            status = order.status,
            customer = order.customer,
            vehicle = order.vehicle,
            attendant = order.attendant,
            description = order.description,
            services = order.services,
            technician = order.technician,
        )
    }
}
