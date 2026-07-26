package br.com.soat.order.model

import br.com.soat.customer.model.Customer
import br.com.soat.order.exception.IllegalOrderStateException
import br.com.soat.shared.model.User
import br.com.soat.vehicle.model.Vehicle
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.UUID
import java.util.UUID.randomUUID

data class Order(
    val id: UUID = randomUUID(),
    val createdAt: LocalDateTime = now(),
    val modifiedAt: LocalDateTime = now(),
    val version: Int = 0,

    val status: Status = Status.RECEIVED,

    val customer: Customer,
    val vehicle: Vehicle,
    val openedBy: User,
    val diagnosedBy: User? = null,
    val services: List<QuotedService> = emptyList(),
    val supplies: List<QuotedSupply> = emptyList(),

    val description: String,
) {

    fun awaitingApproval(
        diagnosedBy: User,
        services: List<QuotedService>,
        supplies: List<QuotedSupply>,
    ): Order =
        if (status == Status.RECEIVED)
            copy(
                status = Status.WAITING_APPROVAL,
                diagnosedBy = diagnosedBy,
                services = services,
                supplies = supplies,
                modifiedAt = now(),
            )
        else this

    fun executionEnqueued(): Order =
        if (status == Status.WAITING_APPROVAL)
            copy(status = Status.EXECUTION_ENQUEUED, modifiedAt = now())
        else this

    fun inProgress(): Order =
        if (status == Status.EXECUTION_ENQUEUED) copy(status = Status.IN_PROGRESS, modifiedAt = now()) else this

    fun completed(): Order =
        if (status == Status.IN_PROGRESS) copy(status = Status.COMPLETED, modifiedAt = now()) else this

    fun canceled(): Order =
        if (status in TERMINAL) this else copy(status = Status.CANCELED, modifiedAt = now())

    fun delivered(): Order {
        if (status != Status.COMPLETED)
            throw IllegalOrderStateException("Order must be COMPLETED. Current status: $status")

        return copy(status = Status.DELIVERED, modifiedAt = now())
    }

    fun canScheduleVehicleDelivery() = status == Status.RECEIVED
    fun canScheduleVehicleReturn() = status == Status.COMPLETED

    enum class Status {
        RECEIVED, WAITING_APPROVAL, EXECUTION_ENQUEUED, IN_PROGRESS, COMPLETED, DELIVERED, CANCELED
    }

    companion object {
        private val TERMINAL = setOf(Status.COMPLETED, Status.DELIVERED, Status.CANCELED)
    }
}
