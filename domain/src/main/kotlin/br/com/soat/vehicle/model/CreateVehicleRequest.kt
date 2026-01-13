package br.com.soat.vehicle.model

import br.com.soat.shared.vo.VehiclePlate
import java.util.UUID

data class CreateVehicleRequest(
    val clientId: UUID,
    val plate: VehiclePlate,
    val brand: String,
    val model: String,
    val year: Int,
)
