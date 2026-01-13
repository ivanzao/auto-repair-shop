package br.com.soat.vehicle.dto

import br.com.soat.vehicle.model.Vehicle

data class VehicleResponseDTO(
    val id: String,
    val plate: String,
    val brand: String,
    val model: String,
    val year: Int,
    val clientId: String,
) {

    companion object {
        fun from(vehicle: Vehicle) = VehicleResponseDTO(
            id = vehicle.id.toString(),
            plate = vehicle.plate.value,
            brand = vehicle.brand,
            model = vehicle.model,
            year = vehicle.year,
            clientId = vehicle.clientId.toString()
        )
    }
}