package br.com.soat.service

import br.com.soat.order.model.OrderService
import br.com.soat.order.repository.OrderServiceRepository
import br.com.soat.service.model.request.CreateServiceRequest
import java.util.UUID

class ServiceUseCase(
    private val repository: OrderServiceRepository
) {

    fun findById(id: UUID) = repository.findById(id)
    fun findAll() = repository.findAll()

    fun create(request: CreateServiceRequest) =
        repository.create(
            OrderService(
                name = request.name,
                description = request.description,
                price = request.price,
                requiredSupplies = request.requiredSupplies
            )
        )

    fun update(id: UUID, request: CreateServiceRequest): OrderService {
        val existingService = repository.findById(id) ?:
            throw IllegalStateException("Trying to update a non-existing service $id")

        return repository.update(
            existingService.copy(
                name = request.name,
                description = request.description,
                price = request.price,
                requiredSupplies = request.requiredSupplies
            )
        )
    }

    fun delete(id: UUID) = repository.delete(id)
}
