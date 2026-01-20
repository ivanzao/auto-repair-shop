package br.com.soat.order.model

import br.com.soat.customer.model.Customer
import br.com.soat.order.exception.IllegalOrderStateException
import br.com.soat.service.model.Service
import br.com.soat.supply.model.SupplyRequirement
import br.com.soat.user.model.User
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
    val attendant: User,
    val services: List<Service> = emptyList(),
    val extraSupplies: List<SupplyRequirement> = emptyList(),

    val description: String,
    val technician: String? = null,
) {

    fun inDiagnosis(technician: String): Order {
        if (status != Status.RECEIVED)
            throw IllegalOrderStateException("Order must be RECEIVED. Current status: $status")

        return copy(status = Status.IN_DIAGNOSIS, technician = technician)
    }

    fun waitingApproval(): Order {
        if (status != Status.IN_DIAGNOSIS)
            throw IllegalOrderStateException("Order must be IN_DIAGNOSIS. Current status: $status")

        return copy(status = Status.WAITING_APPROVAL)
    }

    fun inProgress(): Order {
        if (status != Status.WAITING_APPROVAL)
            throw IllegalOrderStateException("Order must be WAITING_APPROVAL. Current status: $status")

        return copy(status = Status.IN_PROGRESS)
    }

    fun completed() : Order {
        if (status != Status.IN_PROGRESS)
            throw IllegalOrderStateException("Order must be IN_PROGRESS. Current status: $status")

        return copy(status = Status.COMPLETED)
    }

    fun delivered() : Order {
        if (status != Status.COMPLETED)
            throw IllegalOrderStateException("Order must be COMPLETED. Current status: $status")

        return copy(status = Status.DELIVERED)
    }

    fun canceled() : Order {
        if (status == Status.COMPLETED)
            throw IllegalOrderStateException("Order must not be COMPLETED")

        if (status == Status.CANCELED)
            throw IllegalOrderStateException("Order already canceled")

        return copy(status = Status.CANCELED)
    }

    fun addServices(services: List<Service>) = copy(services = this.services + services)

    fun addSupplyRequirements(supplyRequirements: List<SupplyRequirement>) = copy(extraSupplies = supplyRequirements)

    fun canScheduleVehicleDelivery() = status == Status.RECEIVED
    fun canScheduleVehicleReturn() = status == Status.COMPLETED

    fun getSupplyRequirements(): List<SupplyRequirement> {
        val serviceSupplies = services.flatMap { it.requiredSupplies }
        return (serviceSupplies + extraSupplies).groupingBy { it.supplyId }
            .fold(0) { acc, requirement -> acc + requirement.quantity }
            .map { (supplyId, totalQuantity) -> SupplyRequirement(supplyId, totalQuantity) }
    }

    enum class Status {
        RECEIVED, IN_DIAGNOSIS, WAITING_APPROVAL, IN_PROGRESS, CANCELED, COMPLETED, DELIVERED
    }
}