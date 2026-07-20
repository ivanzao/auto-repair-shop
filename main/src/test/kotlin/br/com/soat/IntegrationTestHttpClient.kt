package br.com.soat

import br.com.soat.customer.dto.CreateCustomerRequestDTO
import br.com.soat.customer.dto.CustomerResponseDTO
import br.com.soat.order.dto.CreateOrderRequestDTO
import br.com.soat.order.dto.OrderMetricsResponseDTO
import br.com.soat.order.dto.OrderResponseDTO
import br.com.soat.order.dto.OrderScheduleVehicleRequestDTO
import br.com.soat.order.dto.OrderStatusResponseDTO
import br.com.soat.service.dto.CreateServiceRequestDTO
import br.com.soat.service.dto.ServiceResponseDTO
import br.com.soat.attendant.dto.AttendantResponseDTO
import br.com.soat.attendant.dto.CreateAttendantRequestDTO
import br.com.soat.attendant.dto.UpdateAttendantRequestDTO
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

    fun listOrders(authHeaders: Map<String, String>, page: Int = 1): HttpResponse<String> {
        return get("/v1/orders?page=$page", authHeaders)
    }

    fun getOrder(orderId: String, authHeaders: Map<String, String>): HttpResponse<OrderResponseDTO> {
        val response = get("/v1/orders/$orderId", authHeaders)
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    fun createOrder(dto: CreateOrderRequestDTO, authHeaders: Map<String, String>): HttpResponse<OrderResponseDTO> {
        val body = mapper.writeValueAsString(dto)
        val response = post("/v1/orders", body, authHeaders)
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    fun getOrderStatus(orderId: String): HttpResponse<OrderStatusResponseDTO> {
        val response = get("/v1/orders/$orderId/status")
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    fun scheduleDelivery(
        orderId: String,
        dto: OrderScheduleVehicleRequestDTO,
        authHeaders: Map<String, String>
    ): HttpResponse<*> {
        val body = mapper.writeValueAsString(dto)
        return post("/v1/orders/$orderId/schedule-delivery", body, authHeaders)
    }

    fun deliverOrder(orderId: String, authHeaders: Map<String, String>): HttpResponse<OrderResponseDTO> {
        val response = post("/v1/orders/$orderId/deliver", "{}", authHeaders)
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    fun getOrderMetrics(authHeaders: Map<String, String>): HttpResponse<OrderMetricsResponseDTO> {
        val response = get("/v1/orders/metrics", authHeaders)
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    fun createAttendant(dto: CreateAttendantRequestDTO, authHeaders: Map<String, String>): HttpResponse<AttendantResponseDTO> {
        val body = mapper.writeValueAsString(dto)
        val response = post("/v1/attendants", body, authHeaders)
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    fun getAttendant(attendantId: String, authHeaders: Map<String, String>): HttpResponse<String> {
        return get("/v1/attendants/$attendantId", authHeaders)
    }

    fun getAllAttendants(authHeaders: Map<String, String>): HttpResponse<String> {
        return get("/v1/attendants", authHeaders)
    }

    fun updateAttendant(attendantId: String, dto: UpdateAttendantRequestDTO, authHeaders: Map<String, String>): HttpResponse<String> {
        val body = mapper.writeValueAsString(dto)
        return put("/v1/attendants/$attendantId", body, authHeaders)
    }

    fun deleteAttendant(attendantId: String, authHeaders: Map<String, String>): HttpResponse<String> {
        return delete("/v1/attendants/$attendantId", authHeaders)
    }

    fun createCustomer(dto: CreateCustomerRequestDTO, authHeaders: Map<String, String>): HttpResponse<CustomerResponseDTO> {
        val body = mapper.writeValueAsString(dto)
        val response = post("/v1/customers", body, authHeaders)
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    fun getCustomer(customerId: String, authHeaders: Map<String, String>): HttpResponse<String> {
        return get("/v1/customers/$customerId", authHeaders)
    }

    fun updateCustomer(customerId: String, dto: CreateCustomerRequestDTO, authHeaders: Map<String, String>): HttpResponse<String> {
        val body = mapper.writeValueAsString(dto)
        return put("/v1/customers/$customerId", body, authHeaders)
    }

    fun deleteCustomer(customerId: String, authHeaders: Map<String, String>): HttpResponse<String> {
        return delete("/v1/customers/$customerId", authHeaders)
    }

    fun createVehicle(dto: CreateVehicleRequestDTO, authHeaders: Map<String, String>): HttpResponse<VehicleResponseDTO> {
        val body = mapper.writeValueAsString(dto)
        val response = post("/v1/vehicles", body, authHeaders)
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    fun getVehicle(vehicleId: String, authHeaders: Map<String, String>): HttpResponse<String> {
        return get("/v1/vehicles/$vehicleId", authHeaders)
    }

    fun updateVehicle(vehicleId: String, dto: CreateVehicleRequestDTO, authHeaders: Map<String, String>): HttpResponse<String> {
        val body = mapper.writeValueAsString(dto)
        return put("/v1/vehicles/$vehicleId", body, authHeaders)
    }

    fun deleteVehicle(vehicleId: String, authHeaders: Map<String, String>): HttpResponse<String> {
        return delete("/v1/vehicles/$vehicleId", authHeaders)
    }

    fun createService(dto: CreateServiceRequestDTO, authHeaders: Map<String, String>): HttpResponse<ServiceResponseDTO> {
        val body = mapper.writeValueAsString(dto)
        val response = post("/v1/services", body, authHeaders)
        return SerializedHttpResponse(response, mapper.readValue(response.body()))
    }

    fun getService(serviceId: UUID, authHeaders: Map<String, String>): HttpResponse<String> {
        return get("/v1/services/$serviceId", authHeaders)
    }

    fun getAllServices(authHeaders: Map<String, String>): HttpResponse<String> {
        return get("/v1/services", authHeaders)
    }

    fun updateService(serviceId: UUID, dto: CreateServiceRequestDTO, authHeaders: Map<String, String>): HttpResponse<String> {
        val body = mapper.writeValueAsString(dto)
        return put("/v1/services/$serviceId", body, authHeaders)
    }

    fun deleteService(serviceId: UUID, authHeaders: Map<String, String>): HttpResponse<String> {
        return delete("/v1/services/$serviceId", authHeaders)
    }

    private fun post(path: String, body: String, authHeaders: Map<String, String> = emptyMap()): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort$path"))
            .header("Content-Type", "application/json")
        authHeaders.forEach { (k, v) -> builder.header(k, v) }
        val request = builder.POST(HttpRequest.BodyPublishers.ofString(body)).build()

        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun get(path: String, authHeaders: Map<String, String> = emptyMap()): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort$path"))
            .header("Content-Type", "application/json")
        authHeaders.forEach { (k, v) -> builder.header(k, v) }
        val request = builder.GET().build()

        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun put(path: String, body: String, authHeaders: Map<String, String> = emptyMap()): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort$path"))
            .header("Content-Type", "application/json")
        authHeaders.forEach { (k, v) -> builder.header(k, v) }
        val request = builder.PUT(HttpRequest.BodyPublishers.ofString(body)).build()

        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun delete(path: String, authHeaders: Map<String, String> = emptyMap()): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort$path"))
            .header("Content-Type", "application/json")
        authHeaders.forEach { (k, v) -> builder.header(k, v) }
        val request = builder.DELETE().build()

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
