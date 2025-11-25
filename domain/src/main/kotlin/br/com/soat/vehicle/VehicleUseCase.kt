package br.com.soat.vehicle

import br.com.soat.vehicle.model.CreateVehicleRequest
import br.com.soat.vehicle.model.Vehicle
import br.com.soat.vehicle.VehicleRepository

class VehicleUseCase(
    private val storagePort: VehicleRepository
) {

    fun create(request: CreateVehicleRequest): Vehicle {
        return storagePort.create(
            Vehicle(
                clientId = request.clientId,
                plate = request.plate,
                brand = request.brand,
                model = request.model,
                year = request.year,
            )
        )
    }
}