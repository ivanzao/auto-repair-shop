package br.com.soat

import br.com.soat.auth.dto.AuthenticateUserRequestDTO
import br.com.soat.auth.dto.AuthenticateUserResponseDTO
import br.com.soat.auth.dto.RefreshTokenRequestDTO
import br.com.soat.customer.dto.CreateCustomerRequestDTO
import br.com.soat.customer.dto.CustomerResponseDTO
import br.com.soat.order.dto.CreateOrderRequestDTO
import br.com.soat.order.dto.FinishOrderDiagnosisRequestDTO
import br.com.soat.order.dto.OrderMetricsResponseDTO
import br.com.soat.order.dto.OrderResponseDTO
import br.com.soat.order.dto.OrderScheduleVehicleRequestDTO
import br.com.soat.order.dto.OrderStatusResponseDTO
import br.com.soat.order.dto.StartOrderDiagnosisRequestDTO
import br.com.soat.service.dto.CreateServiceRequestDTO
import br.com.soat.service.dto.ServiceResponseDTO
import br.com.soat.supply.dto.CreateSupplyRequestDTO
import br.com.soat.supply.dto.SupplyResponseDTO
import br.com.soat.user.dto.CreateUserRequestDTO
import br.com.soat.user.dto.UpdateUserRequestDTO
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

    // ==================== ORDER ====================

    fun getOrder(orderId: String, bearerToken: String): HttpResponse<OrderResponseDTO> {
        val response = get("/v1/orders/$orderId", bearerToken)
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    fun createOrder(dto: CreateOrderRequestDTO, bearerToken: String): HttpResponse<OrderResponseDTO> {
        val body = mapper.writeValueAsString(dto)
        val response = post("/v1/orders", body, bearerToken)
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    fun getOrderStatus(orderId: String): HttpResponse<OrderStatusResponseDTO> {
        val response = get("/v1/orders/$orderId/status")
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    fun startDiagnosis(
        orderId: String,
        dto: StartOrderDiagnosisRequestDTO,
        bearerToken: String
    ): HttpResponse<OrderResponseDTO> {
        val body = mapper.writeValueAsString(dto)
        val response = post("/v1/orders/$orderId/start-diagnosis", body, bearerToken)
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    fun finishDiagnosis(
        orderId: String,
        dto: FinishOrderDiagnosisRequestDTO,
        bearerToken: String
    ): HttpResponse<OrderResponseDTO> {
        val body = mapper.writeValueAsString(dto)
        val response = post("/v1/orders/$orderId/finish-diagnosis", body, bearerToken)
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    fun scheduleDelivery(
        orderId: String,
        dto: OrderScheduleVehicleRequestDTO,
        bearerToken: String
    ): HttpResponse<*> {
        val body = mapper.writeValueAsString(dto)
        return post("/v1/orders/$orderId/schedule-delivery", body, bearerToken)
    }

    fun approveOrder(token: String): HttpResponse<*> {
        return get("/v1/orders/quote/approve?token=$token")
    }

    fun declineOrder(token: String): HttpResponse<*> {
        return get("/v1/orders/quote/decline?token=$token")
    }

    fun completeOrder(orderId: String, bearerToken: String): HttpResponse<OrderResponseDTO> {
        val response = post("/v1/orders/$orderId/complete", "{}", bearerToken)
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    fun getOrderMetrics(bearerToken: String): HttpResponse<OrderMetricsResponseDTO> {
        val response = get("/v1/orders/metrics", bearerToken)
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    // ==================== AUTHENTICATION ====================

    fun login(dto: AuthenticateUserRequestDTO): HttpResponse<AuthenticateUserResponseDTO> {
        val body = mapper.writeValueAsString(dto)
        val response = post("/v1/login", body)
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    fun refreshToken(dto: RefreshTokenRequestDTO): HttpResponse<AuthenticateUserResponseDTO> {
        val body = mapper.writeValueAsString(dto)
        val response = post("/v1/refresh", body)
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    // ==================== USER ====================

    fun createUser(dto: CreateUserRequestDTO, bearerToken: String): HttpResponse<UserResponseDTO> {
        val body = mapper.writeValueAsString(dto)
        val response = post("/v1/users", body, bearerToken)
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    fun getUser(userId: String, bearerToken: String): HttpResponse<String> {
        return get("/v1/users/$userId", bearerToken)
    }

    fun getAllUsers(bearerToken: String): HttpResponse<String> {
        return get("/v1/users", bearerToken)
    }

    fun updateUser(userId: String, dto: UpdateUserRequestDTO, bearerToken: String): HttpResponse<String> {
        val body = mapper.writeValueAsString(dto)
        return put("/v1/users/$userId", body, bearerToken)
    }

    fun deleteUser(userId: String, bearerToken: String): HttpResponse<String> {
        return delete("/v1/users/$userId", bearerToken)
    }

    // ==================== CUSTOMER ====================

    fun createCustomer(dto: CreateCustomerRequestDTO, bearerToken: String): HttpResponse<CustomerResponseDTO> {
        val body = mapper.writeValueAsString(dto)
        val response = post("/v1/customers", body, bearerToken)
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    fun getCustomer(customerId: String, bearerToken: String): HttpResponse<String> {
        return get("/v1/customers/$customerId", bearerToken)
    }

    fun updateCustomer(customerId: String, dto: CreateCustomerRequestDTO, bearerToken: String): HttpResponse<String> {
        val body = mapper.writeValueAsString(dto)
        return put("/v1/customers/$customerId", body, bearerToken)
    }

    fun deleteCustomer(customerId: String, bearerToken: String): HttpResponse<String> {
        return delete("/v1/customers/$customerId", bearerToken)
    }

    // ==================== VEHICLE ====================

    fun createVehicle(dto: CreateVehicleRequestDTO, bearerToken: String): HttpResponse<VehicleResponseDTO> {
        val body = mapper.writeValueAsString(dto)
        val response = post("/v1/vehicles", body, bearerToken)
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    fun getVehicle(vehicleId: String, bearerToken: String): HttpResponse<String> {
        return get("/v1/vehicles/$vehicleId", bearerToken)
    }

    fun updateVehicle(vehicleId: String, dto: CreateVehicleRequestDTO, bearerToken: String): HttpResponse<String> {
        val body = mapper.writeValueAsString(dto)
        return put("/v1/vehicles/$vehicleId", body, bearerToken)
    }

    fun deleteVehicle(vehicleId: String, bearerToken: String): HttpResponse<String> {
        return delete("/v1/vehicles/$vehicleId", bearerToken)
    }

    // ==================== SUPPLY ====================

    fun createSupply(dto: CreateSupplyRequestDTO, bearerToken: String): HttpResponse<SupplyResponseDTO> {
        val body = mapper.writeValueAsString(dto)
        val response = post("/v1/supplies", body, bearerToken)
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    fun getSupply(supplyId: UUID, bearerToken: String): HttpResponse<String> {
        return get("/v1/supplies/$supplyId", bearerToken)
    }

    fun updateSupply(supplyId: UUID, dto: CreateSupplyRequestDTO, bearerToken: String): HttpResponse<String> {
        val body = mapper.writeValueAsString(dto)
        return put("/v1/supplies/$supplyId", body, bearerToken)
    }

    fun deleteSupply(supplyId: UUID, bearerToken: String): HttpResponse<String> {
        return delete("/v1/supplies/$supplyId", bearerToken)
    }

    // ==================== SERVICE ====================

    fun createService(dto: CreateServiceRequestDTO, bearerToken: String): HttpResponse<ServiceResponseDTO> {
        val body = mapper.writeValueAsString(dto)
        val response = post("/v1/services", body, bearerToken)
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    fun getService(serviceId: UUID, bearerToken: String): HttpResponse<String> {
        return get("/v1/services/$serviceId", bearerToken)
    }

    fun getAllServices(bearerToken: String): HttpResponse<String> {
        return get("/v1/services", bearerToken)
    }

    fun updateService(serviceId: UUID, dto: CreateServiceRequestDTO, bearerToken: String): HttpResponse<String> {
        val body = mapper.writeValueAsString(dto)
        return put("/v1/services/$serviceId", body, bearerToken)
    }

    fun deleteService(serviceId: UUID, bearerToken: String): HttpResponse<String> {
        return delete("/v1/services/$serviceId", bearerToken)
    }

    // ==================== HTTP METHODS ====================

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
