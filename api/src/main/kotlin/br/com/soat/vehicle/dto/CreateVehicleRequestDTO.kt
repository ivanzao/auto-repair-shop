package br.com.soat.vehicle.dto

import br.com.soat.vehicle.model.CreateVehicleRequest
import java.util.UUID

data class CreateVehicleRequestDTO(
    val clientId: UUID,
    val plate: String,
    val brand: String,
    val model: String,
    val year: Int,
) {
    
    fun toModel() =
        CreateVehicleRequest(
            clientId = clientId,
            plate = plate,
            brand = brand,
            model = model,
            year = year
        )
}
