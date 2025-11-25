package br.com.soat.supply

import br.com.soat.supply.model.request.CreateSupplyRequest
import br.com.soat.supply.model.Supply

class SupplyUseCase(
    private val storagePort: SupplyRepository
) {

    fun create(request: CreateSupplyRequest): Supply {
        return storagePort.create(
            Supply(
                name = request.name,
                description = request.description,
                quantityInStock = request.quantity,
                price = request.price,
            )
        )
    }
}