package br.com.soat.vehicle

import br.com.soat.vehicle.exception.VehicleNotFoundException
import br.com.soat.vehicle.model.CreateVehicleRequest
import br.com.soat.vehicle.model.Vehicle
import br.com.soat.vehicle.repository.VehicleRepository
import java.util.UUID

class VehicleUseCase(
    private val storagePort: VehicleRepository
) {

    fun findById(id: UUID) = storagePort.findById(id) ?: throw VehicleNotFoundException(id)
    fun findAll(): List<Vehicle> = storagePort.findAll()

    fun create(request: CreateVehicleRequest) = storagePort.create(
        Vehicle(
            clientId = request.clientId,
            plate = request.plate,
            brand = request.brand,
            model = request.model,
            year = request.year,
        )
    )

    fun update(id: UUID, request: CreateVehicleRequest): Vehicle {
        val vehicle = storagePort.findById(id) ?: throw VehicleNotFoundException(id)
        val updatedVehicle = vehicle.copy(
            clientId = request.clientId,
            plate = request.plate,
            brand = request.brand,
            model = request.model,
            year = request.year,
            modifiedAt = java.time.LocalDateTime.now(),
            version = vehicle.version + 1
        )

        return storagePort.update(updatedVehicle)
    }

    fun delete(id: UUID) {
        storagePort.delete(id)
    }
}
