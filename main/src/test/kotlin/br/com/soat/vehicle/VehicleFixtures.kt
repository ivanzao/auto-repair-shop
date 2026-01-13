package br.com.soat.vehicle

import br.com.soat.IntegrationTest
import br.com.soat.shared.vo.VehiclePlate
import br.com.soat.vehicle.model.Vehicle
import java.time.LocalDateTime
import java.util.UUID

fun IntegrationTest.createVehicle(
    clientId: UUID,
    id: UUID = UUID.randomUUID(),
    createdAt: LocalDateTime = LocalDateTime.now(),
    modifiedAt: LocalDateTime = LocalDateTime.now(),
    version: Int = 0,
    plate: VehiclePlate = VehiclePlate("ABC1234"),
    brand: String = "Toyota",
    model: String = "Corolla",
    year: Int = 2024
) = get<VehicleRepository>().create(
    Vehicle(
        id = id,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        version = version,
        clientId = clientId,
        plate = plate,
        brand = brand,
        model = model,
        year = year
    )
)
