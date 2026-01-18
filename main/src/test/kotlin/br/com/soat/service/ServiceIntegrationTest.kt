package br.com.soat.service

import br.com.soat.IntegrationTest
import br.com.soat.auth.port.AuthenticationTokenProvider
import br.com.soat.order.repository.OrderServiceRepository
import br.com.soat.service.dto.CreateServiceRequestDTO
import br.com.soat.user.createUser
import java.math.BigDecimal
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ServiceIntegrationTest : IntegrationTest() {

    private val serviceRepository: OrderServiceRepository by lazy { get<OrderServiceRepository>() }
    private val tokenProvider: AuthenticationTokenProvider by lazy { get<AuthenticationTokenProvider>() }

    @Test
    fun `should create service successfully`() {
        val user = createUser()
        val bearerToken = tokenProvider.generate(user, LocalDateTime.now().plusDays(1))

        val requestDto = CreateServiceRequestDTO(
            name = "Troca de óleo",
            description = "Serviço de troca de óleo do motor",
            price = BigDecimal("150.00"),
            requiredSupplies = emptyList()
        )

        val createServiceResponse = http.createService(requestDto, bearerToken)
        assertEquals(201, createServiceResponse.statusCode(), "HTTP status code must be 201 Created")

        val createdService = serviceRepository.findById(createServiceResponse.body().id)!!
        assertEquals(requestDto.name, createdService.name)
        assertEquals(requestDto.description, createdService.description)
        assertEquals(requestDto.price, createdService.price)
    }

    @Test
    fun `should get service by id`() {
        val user = createUser()
        val bearerToken = tokenProvider.generate(user, LocalDateTime.now().plusDays(1))

        val createRequestDto = CreateServiceRequestDTO(
            name = "Alinhamento",
            description = "Alinhamento de direção",
            price = BigDecimal("80.00"),
            requiredSupplies = emptyList()
        )

        val createResponse = http.createService(createRequestDto, bearerToken)
        assertEquals(201, createResponse.statusCode(), "HTTP status code must be 201 Created")

        val serviceId = createResponse.body().id
        val getResponse = http.getService(serviceId, bearerToken)
        assertEquals(200, getResponse.statusCode(), "HTTP status code must be 200 OK")

        val fetchedService = serviceRepository.findById(serviceId)!!
        assertEquals(createRequestDto.name, fetchedService.name)
        assertEquals(createRequestDto.description, fetchedService.description)
        assertEquals(createRequestDto.price, fetchedService.price)
    }

    @Test
    fun `should get all services`() {
        val user = createUser()
        val bearerToken = tokenProvider.generate(user, LocalDateTime.now().plusDays(1))

        val service1 = CreateServiceRequestDTO(
            name = "Balanceamento",
            description = "Balanceamento de rodas",
            price = BigDecimal("60.00"),
            requiredSupplies = emptyList()
        )

        val service2 = CreateServiceRequestDTO(
            name = "Revisão",
            description = "Revisão completa do veículo",
            price = BigDecimal("500.00"),
            requiredSupplies = emptyList()
        )

        http.createService(service1, bearerToken)
        http.createService(service2, bearerToken)

        val getAllResponse = http.getAllServices(bearerToken)
        assertEquals(200, getAllResponse.statusCode(), "HTTP status code must be 200 OK")

        val allServices = serviceRepository.findAll()
        assertEquals(2, allServices.size)
    }

    @Test
    fun `should update service`() {
        val user = createUser()
        val bearerToken = tokenProvider.generate(user, LocalDateTime.now().plusDays(1))

        val createRequestDto = CreateServiceRequestDTO(
            name = "Troca de pneus",
            description = "Troca de pneus do veículo",
            price = BigDecimal("200.00"),
            requiredSupplies = emptyList()
        )

        val createResponse = http.createService(createRequestDto, bearerToken)
        assertEquals(201, createResponse.statusCode(), "HTTP status code must be 201 Created")

        val serviceId = createResponse.body().id
        val updateRequestDto = CreateServiceRequestDTO(
            name = "Troca de pneus Updated",
            description = "Troca completa de pneus do veículo",
            price = BigDecimal("250.00"),
            requiredSupplies = emptyList()
        )

        val updateResponse = http.updateService(serviceId, updateRequestDto, bearerToken)
        assertEquals(200, updateResponse.statusCode(), "HTTP status code must be 200 OK")

        val updatedService = serviceRepository.findById(serviceId)!!
        assertEquals(updateRequestDto.name, updatedService.name)
        assertEquals(updateRequestDto.description, updatedService.description)
        assertEquals(updateRequestDto.price, updatedService.price)
    }

    @Test
    fun `should delete service`() {
        val user = createUser()
        val bearerToken = tokenProvider.generate(user, LocalDateTime.now().plusDays(1))

        val createRequestDto = CreateServiceRequestDTO(
            name = "To Delete",
            description = "Serviço a ser deletado",
            price = BigDecimal("100.00"),
            requiredSupplies = emptyList()
        )

        val createResponse = http.createService(createRequestDto, bearerToken)
        assertEquals(201, createResponse.statusCode(), "HTTP status code must be 201 Created")

        val serviceId = createResponse.body().id
        val deleteResponse = http.deleteService(serviceId, bearerToken)
        assertEquals(204, deleteResponse.statusCode(), "HTTP status code must be 204 No Content")

        val getResponse = http.getService(serviceId, bearerToken)
        assertEquals(404, getResponse.statusCode(), "HTTP status code must be 404 Not Found")

        val deletedService = serviceRepository.findById(serviceId)
        assertNull(deletedService, "Service should be deleted from database")
    }

    @Test
    fun `should return 404 when getting non-existent service`() {
        val user = createUser()
        val bearerToken = tokenProvider.generate(user, LocalDateTime.now().plusDays(1))

        val nonExistentId = java.util.UUID.randomUUID()
        val getResponse = http.getService(nonExistentId, bearerToken)
        assertEquals(404, getResponse.statusCode(), "HTTP status code must be 404 Not Found")
    }

    @Test
    fun `should return 404 when updating non-existent service`() {
        val user = createUser()
        val bearerToken = tokenProvider.generate(user, LocalDateTime.now().plusDays(1))

        val nonExistentId = java.util.UUID.randomUUID()
        val updateRequestDto = CreateServiceRequestDTO(
            name = "Non existent",
            description = "Non existent service",
            price = BigDecimal("100.00"),
            requiredSupplies = emptyList()
        )

        val updateResponse = http.updateService(nonExistentId, updateRequestDto, bearerToken)
        assertEquals(404, updateResponse.statusCode(), "HTTP status code must be 404 Not Found")
    }

    @Test
    fun `should return 404 when deleting non-existent service`() {
        val user = createUser()
        val bearerToken = tokenProvider.generate(user, LocalDateTime.now().plusDays(1))

        val nonExistentId = java.util.UUID.randomUUID()
        val deleteResponse = http.deleteService(nonExistentId, bearerToken)
        assertEquals(404, deleteResponse.statusCode(), "HTTP status code must be 404 Not Found")
    }
}