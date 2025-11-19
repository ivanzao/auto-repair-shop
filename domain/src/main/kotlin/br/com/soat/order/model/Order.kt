package br.com.soat.br.com.soat.order.model

import br.com.soat.br.com.soat.customer.Customer
import br.com.soat.br.com.soat.user.model.User
import br.com.soat.br.com.soat.vehicle.Vehicle
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.UUID
import java.util.UUID.randomUUID

data class Order(
    val id: UUID = randomUUID(),
    val createdAt: LocalDateTime = now(),
    val modifiedAt: LocalDateTime = now(),

    val status: Status = Status.RECEIVED,

    val customer: Customer,
    val vehicle: Vehicle,
    val attendant: User,

    val description: String,
    val services: List<Service>,
    val technician: String? = null,
) {

    enum class Status {
        RECEIVED, IN_DIAGNOSIS, WAITING_APPROVAL, APPROVED, REJECTED, IN_PROGRESS, COMPLETED
    }
}