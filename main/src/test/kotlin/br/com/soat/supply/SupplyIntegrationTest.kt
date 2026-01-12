package br.com.soat.supply

import br.com.soat.IntegrationTest
import br.com.soat.auth.port.AuthenticationTokenProvider
import br.com.soat.supply.dto.CreateSupplyRequestDTO
import br.com.soat.user.createUser
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SupplyIntegrationTest : IntegrationTest() {

    private val supplyRepository: SupplyRepository by lazy { get<SupplyRepository>() }
    private val tokenProvider: AuthenticationTokenProvider by lazy { get<AuthenticationTokenProvider>() }

    @Test
    fun `should create supply successfully`() {
        val user = createUser()
        val bearerToken = tokenProvider.generate(user, LocalDateTime.now().plusDays(1))

        val requestDto = CreateSupplyRequestDTO(
            name = "Parafuso",
            description = "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
            quantity = 3176,
            price = BigDecimal("15.00"),
        )

        val createSupplyResponse = http.createSupply(requestDto, bearerToken)
        assertEquals(201, createSupplyResponse.statusCode(), "HTTP status code must be 201 Created")

        val createdSupply = supplyRepository.findById(createSupplyResponse.body().id)!!
        assertEquals(requestDto.name, createdSupply.name)
        assertEquals(requestDto.description, createdSupply.description)
        assertEquals(requestDto.price, createdSupply.price)
        assertEquals(requestDto.quantity, createdSupply.quantityInStock)
    }

    @Test
    fun `should get supply by id`() {
        val user = createUser()
        val bearerToken = tokenProvider.generate(user, LocalDateTime.now().plusDays(1))

        val createRequestDto = CreateSupplyRequestDTO(
            name = "Porca",
            description = "Porca sextavada",
            quantity = 1000,
            price = BigDecimal("0.50"),
        )

        val createResponse = http.createSupply(createRequestDto, bearerToken)
        assertEquals(201, createResponse.statusCode(), "HTTP status code must be 201 Created")

        val supplyId = createResponse.body().id
        val getResponse = http.getSupply(supplyId, bearerToken)
        assertEquals(200, getResponse.statusCode(), "HTTP status code must be 200 OK")

        val fetchedSupply = supplyRepository.findById(supplyId)!!
        assertEquals(createRequestDto.name, fetchedSupply.name)
        assertEquals(createRequestDto.description, fetchedSupply.description)
        assertEquals(createRequestDto.price, fetchedSupply.price)
        assertEquals(createRequestDto.quantity, fetchedSupply.quantityInStock)
    }

    @Test
    fun `should update supply`() {
        val user = createUser()
        val bearerToken = tokenProvider.generate(user, LocalDateTime.now().plusDays(1))

        val createRequestDto = CreateSupplyRequestDTO(
            name = "Arruela",
            description = "Arruela de pressão",
            quantity = 500,
            price = BigDecimal("0.20"),
        )

        val createResponse = http.createSupply(createRequestDto, bearerToken)
        assertEquals(201, createResponse.statusCode(), "HTTP status code must be 201 Created")

        val supplyId = createResponse.body().id
        val updateRequestDto = CreateSupplyRequestDTO(
            name = "Arruela Updated",
            description = "Arruela de pressão",
            quantity = 600,
            price = BigDecimal("0.25"),
        )

        val updateResponse = http.updateSupply(supplyId, updateRequestDto, bearerToken)
        assertEquals(200, updateResponse.statusCode(), "HTTP status code must be 200 OK")

        val updatedSupply = supplyRepository.findById(supplyId)!!
        assertEquals(updateRequestDto.name, updatedSupply.name)
        assertEquals(updateRequestDto.description, updatedSupply.description)
        assertEquals(updateRequestDto.price, updatedSupply.price)
        assertEquals(updateRequestDto.quantity, updatedSupply.quantityInStock)
    }

    @Test
    fun `should delete supply`() {
        val user = createUser()
        val bearerToken = tokenProvider.generate(user, LocalDateTime.now().plusDays(1))

        val createRequestDto = CreateSupplyRequestDTO(
            name = "To Delete",
            description = "To be deleted",
            quantity = 10,
            price = BigDecimal("1.00"),
        )

        val createResponse = http.createSupply(createRequestDto, bearerToken)
        assertEquals(201, createResponse.statusCode(), "HTTP status code must be 201 Created")

        val supplyId = createResponse.body().id
        val deleteResponse = http.deleteSupply(supplyId, bearerToken)
        assertEquals(204, deleteResponse.statusCode(), "HTTP status code must be 204 No Content")

        val getResponse = http.getSupply(supplyId, bearerToken)
        assertEquals(404, getResponse.statusCode(), "HTTP status code must be 404 Not Found")

        val deletedSupply = supplyRepository.findById(supplyId)
        assertNull(deletedSupply, "Supply should be deleted from database")
    }
}
