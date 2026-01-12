package br.com.soat

import br.com.soat.auth.dto.AuthenticateUserRequestDTO
import br.com.soat.auth.dto.AuthenticateUserResponseDTO
import br.com.soat.auth.dto.RefreshTokenRequestDTO
import br.com.soat.customer.dto.CreateCustomerRequestDTO
import br.com.soat.customer.dto.CustomerResponseDTO
import br.com.soat.order.dto.OrderQuoteApprovalRequestDTO
import br.com.soat.order.dto.CreateOrderRequestDTO
import br.com.soat.order.dto.FinishOrderDiagnosisRequestDTO
import br.com.soat.order.dto.OrderResponseDTO
import br.com.soat.order.dto.OrderScheduleVehicleRequestDTO
import br.com.soat.order.dto.OrderStatusResponseDTO
import br.com.soat.order.dto.StartOrderDiagnosisRequestDTO
import br.com.soat.supply.dto.CreateSupplyRequestDTO
import br.com.soat.supply.dto.SupplyResponseDTO
import br.com.soat.user.dto.CreateUserRequestDTO
import br.com.soat.user.dto.UserResponseDTO
import br.com.soat.user.model.User
import br.com.soat.vehicle.dto.CreateVehicleRequestDTO
import br.com.soat.vehicle.dto.VehicleResponseDTO
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Optional
import java.util.UUID

class IntegrationTestHttpClient(private val serverPort: Int) {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val mapper = jacksonObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())

    fun getOrder(orderId: String, bearerToken: String): HttpResponse<OrderResponseDTO> {
        val response = get("/orders/$orderId", bearerToken)
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    fun createOrder(dto: CreateOrderRequestDTO, bearerToken: String): HttpResponse<OrderResponseDTO> {
        val body = mapper.writeValueAsString(dto)
        val response = post("/orders", body, bearerToken)
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    fun getOrderStatus(orderId: String): HttpResponse<OrderStatusResponseDTO> {
        val response = get("/orders/$orderId/status")
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    fun startDiagnosis(
        orderId: String,
        dto: StartOrderDiagnosisRequestDTO,
        bearerToken: String
    ): HttpResponse<OrderResponseDTO> {
        val body = mapper.writeValueAsString(dto)
        val response = post("/orders/$orderId/start-diagnosis", body, bearerToken)
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    fun finishDiagnosis(
        orderId: String,
        dto: FinishOrderDiagnosisRequestDTO,
        bearerToken: String
    ): HttpResponse<OrderResponseDTO> {
        val body = mapper.writeValueAsString(dto)
        val response = post("/orders/$orderId/finish-diagnosis", body, bearerToken)
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    fun scheduleDelivery(
        orderId: String,
        dto: OrderScheduleVehicleRequestDTO,
        bearerToken: String
    ): HttpResponse<*> {
        val body = mapper.writeValueAsString(dto)
        return post("/orders/$orderId/schedule-delivery", body, bearerToken)
    }

    fun approveOrder(dto: OrderQuoteApprovalRequestDTO): HttpResponse<*> {
        val body = mapper.writeValueAsString(dto)
        return post("/orders/quote/approve", body)
    }

    fun login(dto: AuthenticateUserRequestDTO): HttpResponse<AuthenticateUserResponseDTO> {
        val body = mapper.writeValueAsString(dto)
        val response = post("/login", body)
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    fun refreshToken(dto: RefreshTokenRequestDTO): HttpResponse<AuthenticateUserResponseDTO> {
        val body = mapper.writeValueAsString(dto)
        val response = post("/refresh", body)
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    fun createUser(dto: CreateUserRequestDTO, bearerToken: String): HttpResponse<UserResponseDTO> {
        val body = mapper.writeValueAsString(dto)
        val response = post("/users", body, bearerToken)
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    fun createCustomer(dto: CreateCustomerRequestDTO, bearerToken: String): HttpResponse<CustomerResponseDTO> {
        val body = mapper.writeValueAsString(dto)
        val response = post("/customers", body, bearerToken)
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    fun getCustomer(customerId: String, bearerToken: String): HttpResponse<String> {
        return get("/customers/$customerId", bearerToken)
    }

    fun updateCustomer(customerId: String, dto: CreateCustomerRequestDTO, bearerToken: String): HttpResponse<String> {
        val body = mapper.writeValueAsString(dto)
        return put("/customers/$customerId", body, bearerToken)
    }

    fun deleteCustomer(customerId: String, bearerToken: String): HttpResponse<String> {
        return delete("/customers/$customerId", bearerToken)
    }

    fun createVehicle(dto: CreateVehicleRequestDTO, bearerToken: String): HttpResponse<VehicleResponseDTO> {
        val body = mapper.writeValueAsString(dto)
        val response = post("/vehicles", body, bearerToken)
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    fun getVehicle(vehicleId: String, bearerToken: String): HttpResponse<String> {
        return get("/vehicles/$vehicleId", bearerToken)
    }

    fun updateVehicle(vehicleId: String, dto: CreateVehicleRequestDTO, bearerToken: String): HttpResponse<String> {
        val body = mapper.writeValueAsString(dto)
        return put("/vehicles/$vehicleId", body, bearerToken)
    }

    fun deleteVehicle(vehicleId: String, bearerToken: String): HttpResponse<String> {
        return delete("/vehicles/$vehicleId", bearerToken)
    }

    fun createSupply(dto: CreateSupplyRequestDTO, bearerToken: String): HttpResponse<SupplyResponseDTO> {
        val body = mapper.writeValueAsString(dto)
        val response = post("/supplies", body, bearerToken)
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    fun getSupply(supplyId: UUID, bearerToken: String): HttpResponse<String> {
        return get("/supplies/$supplyId", bearerToken)
    }

    fun updateSupply(supplyId: UUID, dto: CreateSupplyRequestDTO, bearerToken: String): HttpResponse<String> {
        val body = mapper.writeValueAsString(dto)
        return put("/supplies/$supplyId", body, bearerToken)
    }

    fun deleteSupply(supplyId: UUID, bearerToken: String): HttpResponse<String> {
        return delete("/supplies/$supplyId", bearerToken)
    }

    private fun post(path: String, body: String, bearerToken: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort$path"))
            .header("Content-Type", "application/json")
            .apply { bearerToken?.let { header("Authorization", "Bearer $bearerToken") } }
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun get(path: String, bearerToken: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort$path"))
            .header("Content-Type", "application/json")
            .apply { bearerToken?.let { header("Authorization", "Bearer $bearerToken") } }
            .GET()
            .build()

        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun put(path: String, body: String, bearerToken: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort$path"))
            .header("Content-Type", "application/json")
            .apply { bearerToken?.let { header("Authorization", "Bearer $bearerToken") } }
            .PUT(HttpRequest.BodyPublishers.ofString(body))
            .build()

        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun delete(path: String, bearerToken: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort$path"))
            .header("Content-Type", "application/json")
            .apply { bearerToken?.let { header("Authorization", "Bearer $bearerToken") } }
            .DELETE()
            .build()

        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }
}

data class SerializedHttpResponse<T>(
    val original: HttpResponse<String>,
    val deserializedBody: T
) : HttpResponse<T> {
    override fun statusCode() = original.statusCode()
    override fun request() = original.request()
    override fun previousResponse() = Optional.empty<HttpResponse<T?>?>()
    override fun headers() = original.headers()
    override fun body() = deserializedBody
    override fun sslSession() = original.sslSession()
    override fun uri() = original.uri()
    override fun version() = original.version()
}
