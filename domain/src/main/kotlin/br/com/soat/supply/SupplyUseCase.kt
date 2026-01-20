package br.com.soat.supply

import br.com.soat.supply.exception.SupplyNotFoundException
import br.com.soat.supply.model.request.CreateSupplyRequest
import br.com.soat.supply.model.Supply
import br.com.soat.supply.repository.SupplyRepository
import java.util.UUID

class SupplyUseCase(
    private val storagePort: SupplyRepository
) {

    fun findById(id: UUID) = storagePort.findById(id) ?: throw SupplyNotFoundException(id)
    fun findAll() = storagePort.findAll()

    fun create(request: CreateSupplyRequest) =
        storagePort.create(
            Supply(
                name = request.name,
                description = request.description,
                quantityInStock = request.quantity,
                price = request.price,
            )
        )

    fun update(id: UUID, request: CreateSupplyRequest): Supply {
        val existingSupply = storagePort.findById(id) ?:
            throw IllegalStateException("Trying to update a non-existing supply $id")

        return storagePort.update(
            existingSupply.copy(
                name = request.name,
                description = request.description,
                quantityInStock = request.quantity,
                price = request.price
            )
        )
    }

    fun delete(id: UUID) = storagePort.delete(id)
}