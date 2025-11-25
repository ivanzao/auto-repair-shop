package br.com.soat.order.model

import br.com.soat.customer.model.Customer
import br.com.soat.service.model.Service
import br.com.soat.supply.model.SupplyRequest
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
    val extraSupplies: List<SupplyRequest> = emptyList(),

    val description: String,
    val technician: String? = null,
) {

    fun inDiagnosis() = copy(status = Status.IN_DIAGNOSIS)
    fun waitingApproval() = copy(status = Status.WAITING_APPROVAL)

    fun addServices(services: List<Service>) = copy(services = this.services + services)

    fun requiredSupplyRequests(): List<SupplyRequest> {
        val serviceSupplies = services.flatMap { it.requiredSupplies }
        return (serviceSupplies + extraSupplies).groupingBy { it.supplyId }
            .fold(0) { acc, supplyRequest -> acc + supplyRequest.quantity }
            .map { (supplyId, totalQuantity) -> SupplyRequest(supplyId, totalQuantity) }
    }

    enum class Status {
        RECEIVED, IN_DIAGNOSIS, WAITING_APPROVAL, APPROVED, REJECTED, IN_PROGRESS, CANCELED, COMPLETED
    }
}