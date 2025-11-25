package br.com.soat.vehicle.model

import java.util.UUID

data class CreateVehicleRequest(
    val clientId: UUID,
    val plate: String,
    val brand: String,
    val model: String,
    val year: Int,
)
