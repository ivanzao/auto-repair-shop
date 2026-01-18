package br.com.soat.order.model

import br.com.soat.customer.model.Customer
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
    val services: List<OrderService> = emptyList(),
    val extraSupplies: List<SupplyRequirement> = emptyList(),

    val description: String,
    val technician: String? = null,
) {

    fun inDiagnosis(technician: String) = copy(status = Status.IN_DIAGNOSIS, technician = technician)
    fun waitingApproval() = copy(status = Status.WAITING_APPROVAL)
    fun inProgress() = copy(status = Status.IN_PROGRESS)
    fun canceled() = copy(status = Status.CANCELED)
    fun completed() = copy(status = Status.COMPLETED)

    fun addServices(services: List<OrderService>) = copy(services = this.services + services)

    fun addSupplyRequirements(supplyRequirements: List<SupplyRequirement>) = copy(extraSupplies = supplyRequirements)

    fun getSupplyRequirements(): List<SupplyRequirement> {
        val serviceSupplies = services.flatMap { it.requiredSupplies }
        return (serviceSupplies + extraSupplies).groupingBy { it.supplyId }
            .fold(0) { acc, requirement -> acc + requirement.quantity }
            .map { (supplyId, totalQuantity) -> SupplyRequirement(supplyId, totalQuantity) }
    }

    enum class Status {
        RECEIVED, IN_DIAGNOSIS, WAITING_APPROVAL, IN_PROGRESS, CANCELED, COMPLETED
    }
}