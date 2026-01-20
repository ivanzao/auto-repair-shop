package br.com.soat.order.dto

import br.com.soat.customer.model.Customer
import br.com.soat.order.model.Order
import br.com.soat.order.model.Order.Status
import br.com.soat.service.model.Service
import br.com.soat.user.model.User
import br.com.soat.vehicle.model.Vehicle
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import java.util.UUID

data class OrderResponseDTO(
    val id: UUID,
    val createdAt: ZonedDateTime,

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
            createdAt = order.createdAt.atZone(UTC),
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
