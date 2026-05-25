package br.com.soat.vehicle

import br.com.soat.IntegrationTest
import br.com.soat.customer.dto.CreateCustomerRequestDTO
import br.com.soat.vehicle.dto.CreateVehicleRequestDTO
import java.util.UUID
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class VehicleIntegrationTest : IntegrationTest() {

    private val vehicleRepository: VehicleRepository by lazy { get<VehicleRepository>() }

    private fun createCustomer(authHeaders: Map<String, String>): UUID {
        val requestDto = CreateCustomerRequestDTO(
            name = "Test Customer",
            document = Random.nextLong(10000000000L, 99999999999L).toString(),
            email = "test${Random.nextLong()}@example.com",
            contact = "11 99999-9999"
        )
        val response = http.createCustomer(requestDto, authHeaders)
        return UUID.fromString(response.body().id)
    }

    @Test
    fun `should get vehicle by id`() {
        val authHeaders = adminHeaders()
        val clientId = createCustomer(authHeaders)

        val createRequestDto = CreateVehicleRequestDTO(
            clientId = clientId,
            plate = "XYZ5678",
            brand = "Honda",
            model = "Civic",
            year = 2023
        )

        val createResponse = http.createVehicle(createRequestDto, authHeaders)
        assertEquals(201, createResponse.statusCode(), "HTTP status code must be 201 Created")

        val vehicleId = createResponse.body().id
        val getResponse = http.getVehicle(vehicleId, authHeaders)
        assertEquals(200, getResponse.statusCode(), "HTTP status code must be 200 OK")

        val fetchedVehicle = vehicleRepository.findById(UUID.fromString(vehicleId))!!
        assertEquals(createRequestDto.plate, fetchedVehicle.plate.value)
        assertEquals(createRequestDto.brand, fetchedVehicle.brand)
        assertEquals(createRequestDto.model, fetchedVehicle.model)
        assertEquals(createRequestDto.year, fetchedVehicle.year)
        assertEquals(createRequestDto.clientId, fetchedVehicle.clientId)
    }

    @Test
    fun `should create vehicle successfully`() {
        val authHeaders = adminHeaders()
        val clientId = createCustomer(authHeaders)

        val requestDto = CreateVehicleRequestDTO(
            clientId = clientId,
            plate = "ABC1234",
            brand = "Toyota",
            model = "Corolla",
            year = 2024
        )

        val createVehicleResponse = http.createVehicle(requestDto, authHeaders)
        assertEquals(201, createVehicleResponse.statusCode(), "HTTP status code must be 201 Created")

        val createdVehicle = vehicleRepository.findById(UUID.fromString(createVehicleResponse.body().id))!!
        assertEquals(requestDto.plate, createdVehicle.plate.value)
        assertEquals(requestDto.brand, createdVehicle.brand)
        assertEquals(requestDto.model, createdVehicle.model)
        assertEquals(requestDto.year, createdVehicle.year)
        assertEquals(requestDto.clientId, createdVehicle.clientId)
    }

    @Test
    fun `should update vehicle`() {
        val authHeaders = adminHeaders()
        val clientId = createCustomer(authHeaders)

        val createRequestDto = CreateVehicleRequestDTO(
            clientId = clientId,
            plate = "DEF-9012",
            brand = "Ford",
            model = "Focus",
            year = 2022
        )

        val createResponse = http.createVehicle(createRequestDto, authHeaders)
        assertEquals(201, createResponse.statusCode(), "HTTP status code must be 201 Created")

        val vehicleId = createResponse.body().id
        val updateRequestDto = CreateVehicleRequestDTO(
            clientId = clientId,
            plate = "DEF9012",
            brand = "Ford",
            model = "Focus Updated",
            year = 2022
        )

        val updateResponse = http.updateVehicle(vehicleId, updateRequestDto, authHeaders)
        assertEquals(200, updateResponse.statusCode(), "HTTP status code must be 200 OK")

        val updatedVehicle = vehicleRepository.findById(UUID.fromString(vehicleId))!!
        assertEquals(updateRequestDto.plate, updatedVehicle.plate.value)
        assertEquals(updateRequestDto.brand, updatedVehicle.brand)
        assertEquals(updateRequestDto.model, updatedVehicle.model)
        assertEquals(updateRequestDto.year, updatedVehicle.year)
        assertEquals(updateRequestDto.clientId, updatedVehicle.clientId)
    }

    @Test
    fun `should delete vehicle`() {
        val authHeaders = adminHeaders()
        val clientId = createCustomer(authHeaders)

        val createRequestDto = CreateVehicleRequestDTO(
            clientId = clientId,
            plate = "DEL-0000",
            brand = "Delete",
            model = "Me",
            year = 2000
        )

        val createResponse = http.createVehicle(createRequestDto, authHeaders)
        assertEquals(201, createResponse.statusCode(), "HTTP status code must be 201 Created")

        val vehicleId = createResponse.body().id
        val deleteResponse = http.deleteVehicle(vehicleId, authHeaders)
        assertEquals(204, deleteResponse.statusCode(), "HTTP status code must be 204 No Content")

        val getResponse = http.getVehicle(vehicleId, authHeaders)
        assertEquals(404, getResponse.statusCode(), "HTTP status code must be 404 Not Found")

        val deletedVehicle = vehicleRepository.findById(UUID.fromString(vehicleId))
        assertNull(deletedVehicle, "Vehicle should be deleted from database")
    }
}
