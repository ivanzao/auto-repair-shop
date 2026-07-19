package br.com.soat.order.model

import br.com.soat.customer.model.Customer
import br.com.soat.order.exception.IllegalOrderStateException
import br.com.soat.service.model.Service
import br.com.soat.supply.model.SupplyRequirement
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
    val attendantId: UUID,
    val services: List<Service> = emptyList(),
    val extraSupplies: List<SupplyRequirement> = emptyList(),

    val description: String,
    val technician: String? = null,
) {

    /** Pagamento confirmado: RECEIVED → IN_PROGRESS. Idempotente (no-op fora de RECEIVED). */
    fun markInProgress(): Order =
        if (status == Status.RECEIVED) copy(status = Status.IN_PROGRESS, modifiedAt = now()) else this

    /** Execução concluída: IN_PROGRESS → COMPLETED. Idempotente (no-op fora de IN_PROGRESS). */
    fun markCompleted(): Order =
        if (status == Status.IN_PROGRESS) copy(status = Status.COMPLETED, modifiedAt = now()) else this

    /**
     * Compensação: cancela a OS em qualquer estado não-terminal. `COMPLETED` é
     * terminal — `ExecutionFailed` compensa durante a execução (OS IN_PROGRESS),
     * antes da conclusão, então nunca cancela uma OS já concluída. Idempotente.
     */
    fun markCanceled(): Order =
        if (status in TERMINAL) this else copy(status = Status.CANCELED, modifiedAt = now())

    /** Entrega manual do veículo (REST): COMPLETED → DELIVERED. */
    fun delivered(): Order {
        if (status != Status.COMPLETED)
            throw IllegalOrderStateException("Order must be COMPLETED. Current status: $status")

        return copy(status = Status.DELIVERED, modifiedAt = now())
    }

    fun addServices(services: List<Service>) = copy(services = this.services + services)

    fun addSupplyRequirements(supplyRequirements: List<SupplyRequirement>) = copy(
        extraSupplies = (this.extraSupplies + supplyRequirements)
            .groupingBy { it.supplyId }
            .fold(0) { acc, req -> acc + req.quantity }
            .map { (supplyId, totalQuantity) -> SupplyRequirement(supplyId, totalQuantity) }
    )

    fun canScheduleVehicleDelivery() = status == Status.RECEIVED
    fun canScheduleVehicleReturn() = status == Status.COMPLETED

    fun getSupplyRequirements(): List<SupplyRequirement> {
        val serviceSupplies = services.flatMap { it.requiredSupplies }
        return (serviceSupplies + extraSupplies).groupingBy { it.supplyId }
            .fold(0) { acc, requirement -> acc + requirement.quantity }
            .map { (supplyId, totalQuantity) -> SupplyRequirement(supplyId, totalQuantity) }
    }

    enum class Status {
        RECEIVED, IN_PROGRESS, CANCELED, COMPLETED, DELIVERED
    }

    companion object {
        private val TERMINAL = setOf(Status.COMPLETED, Status.DELIVERED, Status.CANCELED)
    }
}