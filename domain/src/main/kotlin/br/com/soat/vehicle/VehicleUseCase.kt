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

    fun findById(id: java.util.UUID): Vehicle? {
        return storagePort.findById(id)
    }

    fun findAll(): List<Vehicle> {
        return storagePort.findAll()
    }

    fun update(id: java.util.UUID, request: CreateVehicleRequest): Vehicle? {
        val existingVehicle = storagePort.findById(id) ?: return null
        
        val updatedVehicle = existingVehicle.copy(
            clientId = request.clientId,
            plate = request.plate,
            brand = request.brand,
            model = request.model,
            year = request.year,
            modifiedAt = java.time.LocalDateTime.now(),
            version = existingVehicle.version + 1
        )
        
        return storagePort.update(updatedVehicle)
    }

    fun delete(id: java.util.UUID): Boolean {
        val existingVehicle = storagePort.findById(id) ?: return false
        storagePort.delete(existingVehicle.id)
        return true
    }
}