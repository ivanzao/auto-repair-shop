package br.com.soat.order

import br.com.soat.IntegrationTest
import br.com.soat.auth.port.AuthenticationTokenProvider
import br.com.soat.customer.createCustomer
import br.com.soat.mail.EmailService
import br.com.soat.mail.model.OrderQuoteApprovalEmailInput
import br.com.soat.order.dto.CreateOrderRequestDTO
import br.com.soat.order.dto.FinishOrderDiagnosisRequestDTO
import br.com.soat.order.dto.OrderScheduleVehicleRequestDTO
import br.com.soat.order.dto.StartOrderDiagnosisRequestDTO
import br.com.soat.order.dto.SupplyRequirementDTO
import br.com.soat.order.model.Order
import br.com.soat.order.model.OrderApprovalToken
import br.com.soat.order.model.OrderExecutionMetric
import br.com.soat.order.model.OrderSchedule
import br.com.soat.order.repository.OrderApprovalTokenRepository
import br.com.soat.order.repository.OrderExecutionMetricRepository
import br.com.soat.order.repository.OrderRepository
import br.com.soat.order.repository.OrderScheduleRepository
import br.com.soat.supply.SupplyRepository
import br.com.soat.supply.createSupply
import br.com.soat.supply.model.SupplyRequirement
import br.com.soat.user.createUser
import br.com.soat.user.model.User
import br.com.soat.vehicle.createVehicle
import br.com.soat.waitFor
import io.mockk.every
import io.mockk.slot
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class OrderLifecycleIntegrationTest : IntegrationTest() {

    private val tokenProvider: AuthenticationTokenProvider by lazy { get<AuthenticationTokenProvider>() }
    private val orderRepository: OrderRepository by lazy { get<OrderRepository>() }
    private val orderScheduleRepository: OrderScheduleRepository by lazy { get<OrderScheduleRepository>() }
    private val orderApprovalTokenRepository: OrderApprovalTokenRepository by lazy { get<OrderApprovalTokenRepository>() }
    private val orderExecutionMetricRepository: OrderExecutionMetricRepository by lazy { get<OrderExecutionMetricRepository>() }
    private val emailService: EmailService by lazy { get<EmailService>() }
    private val supplyRepository: SupplyRepository by lazy { get<SupplyRepository>() }

    @Test
    fun `should complete full order lifecycle`() {
        // prepare
        val attendant = createUser(role = User.Role.ATTENDANT)
        val customer = createCustomer()
        val vehicle = createVehicle(customer.id)
        val supply = createSupply(quantityInStock = 10)
        val service = createService(requiredSupplies = listOf(SupplyRequirement(supply.id, 5)))

        val bearerToken = tokenProvider.generate(attendant, LocalDateTime.now().plusDays(1))

        val emailInputSlot = slot<OrderQuoteApprovalEmailInput>()
        every { emailService.sendOrderQuoteApprovalEmail(capture(emailInputSlot)) } answers {}

        // test
        val requestDto = CreateOrderRequestDTO(
            customerId = customer.id,
            vehicleId = vehicle.id,
            description = "Noise in the engine",
            attendantId = attendant.id
        )
        val createOrderResponse = http.createOrder(requestDto, bearerToken)
        assertEquals(201, createOrderResponse.statusCode())

        // create and validate order
        val createdOrder = orderRepository.findById(createOrderResponse.body().id)!!
        assertEquals(customer.id, createdOrder.customer.id)
        assertEquals(vehicle.id, createdOrder.vehicle.id)
        assertEquals(attendant.id, createdOrder.attendant.id)
        assertEquals(requestDto.description, createdOrder.description)
        assertEquals(Order.Status.RECEIVED, createdOrder.status)

        // validate public status endpoint (no authentication)
        val statusAfterCreation = http.getOrderStatus(createdOrder.id.toString())
        assertEquals(200, statusAfterCreation.statusCode())
        assertEquals(createdOrder.id, statusAfterCreation.body().id)
        assertEquals(Order.Status.RECEIVED, statusAfterCreation.body().status)
        assertNotNull(statusAfterCreation.body().modifiedAt)

        // create and validate order schedule delivery
        val scheduleDeliveryRequest = OrderScheduleVehicleRequestDTO(
            dateTime = LocalDateTime.now().atZone(UTC).plusHours(2)
        )
        val scheduleDeliveryResponse = http.scheduleDelivery(createdOrder.id.toString(), scheduleDeliveryRequest, bearerToken)
        assertEquals(200, scheduleDeliveryResponse.statusCode())

        val createdOrderScheduleDelivery = orderScheduleRepository.findAllByOrderId(createdOrder.id).single()
        assertEquals(
            scheduleDeliveryRequest.dateTime.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            createdOrderScheduleDelivery.dateTime.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
        assertEquals(OrderSchedule.Type.DELIVERY, createdOrderScheduleDelivery.type)

        // start diagnosis
        val startDiagnosisRequest = StartOrderDiagnosisRequestDTO(technician = "Tech Mike")
        val orderInDiagnosisResponse = http.startDiagnosis(createdOrder.id.toString(), startDiagnosisRequest, bearerToken)
        assertEquals(200, orderInDiagnosisResponse.statusCode())

        val orderInDiagnosis = orderRepository.findById(createdOrder.id)!!
        assertEquals(Order.Status.IN_DIAGNOSIS, orderInDiagnosis.status)
        assertEquals(startDiagnosisRequest.technician, orderInDiagnosis.technician)

        // validate public status endpoint shows IN_DIAGNOSIS
        val statusAfterDiagnosis = http.getOrderStatus(orderInDiagnosis.id.toString())
        assertEquals(200, statusAfterDiagnosis.statusCode())
        assertEquals(Order.Status.IN_DIAGNOSIS, statusAfterDiagnosis.body().status)

        // finish diagnosis and reserve supplies
        val finishDiagnosisRequest = FinishOrderDiagnosisRequestDTO(
            servicesIds = listOf(service.id),
            extraSuppliesRequests = listOf(SupplyRequirementDTO(supplyId = supply.id, quantity = 1))
        )
        val orderDiagnosedResponse = http.finishDiagnosis(orderInDiagnosis.id.toString(), finishDiagnosisRequest, bearerToken)
        assertEquals(200, orderDiagnosedResponse.statusCode())

        var orderWaitingApproval: Order? = orderRepository.findById(orderInDiagnosis.id)
        var approvalToken: OrderApprovalToken? = null
        waitFor {
            orderWaitingApproval = orderRepository.findById(orderInDiagnosis.id)
            orderWaitingApproval?.status == Order.Status.WAITING_APPROVAL
        }
        assertEquals(Order.Status.WAITING_APPROVAL, orderWaitingApproval!!.status)
        assertEquals(finishDiagnosisRequest.servicesIds, orderWaitingApproval!!.services.map { it.id })
        assertEquals(finishDiagnosisRequest.extraSuppliesRequests.map { it.toModel() }, orderWaitingApproval!!.extraSupplies)

        // validate public status endpoint shows WAITING_APPROVAL
        val statusAfterWaitingApproval = http.getOrderStatus(orderWaitingApproval!!.id.toString())
        assertEquals(200, statusAfterWaitingApproval.statusCode())
        assertEquals(Order.Status.WAITING_APPROVAL, statusAfterWaitingApproval.body().status)

        // assert approval token was created
        waitFor {
            approvalToken = orderApprovalTokenRepository.findByOrderId(orderWaitingApproval!!.id)
            approvalToken != null
        }
        assertEquals(orderWaitingApproval!!.id, approvalToken!!.orderId)
        assertEquals(null, approvalToken!!.usedAt)
        assertEquals(true, approvalToken!!.isValid())

        // assert reserved supplies
        val requestedSupply = supplyRepository.findById(supply.id)!!
        assertEquals(4, requestedSupply.quantityInStock) // 10 starting - 5 from services - 1 from extra supplies

        // assert email was sent
        assertEquals(approvalToken?.id.toString(), emailInputSlot.captured.callbackToken)
        assertEquals(customer.name, emailInputSlot.captured.customerName)
        assertEquals(customer.email.value, emailInputSlot.captured.customerEmail)
        assertEquals(orderWaitingApproval!!.services.map { it.name }, emailInputSlot.captured.services.map { it.name })
        assertEquals(listOf(OrderQuoteApprovalEmailInput.Supply(supply.name, 6, supply.price)), emailInputSlot.captured.supplies)

        // approve order by token
        val approveOrderResponse = http.approveOrder(approvalToken!!.id.toString())
        assertEquals(200, approveOrderResponse.statusCode())

        // assert order status is IN_PROGRESS
        val orderInProgress = orderRepository.findById(orderWaitingApproval!!.id)!!
        assertEquals(Order.Status.IN_PROGRESS, orderInProgress.status)

        // validate public status endpoint shows IN_PROGRESS
        val statusAfterApproval = http.getOrderStatus(orderInProgress.id.toString())
        assertEquals(200, statusAfterApproval.statusCode())
        assertEquals(Order.Status.IN_PROGRESS, statusAfterApproval.body().status)

        // assert approval token was marked as used
        val usedApprovalToken = orderApprovalTokenRepository.findById(approvalToken!!.id)!!
        assertEquals(false, usedApprovalToken.isValid())
        assertEquals(true, usedApprovalToken.usedAt != null)

        // assert execution metric was created with inProgressAt
        var executionMetric: OrderExecutionMetric? = null
        waitFor {
            executionMetric = orderExecutionMetricRepository.findByOrderId(orderInProgress.id)
            executionMetric != null
        }
        assertNotNull(executionMetric)
        assertNotNull(executionMetric!!.inProgressAt)
        assertEquals(null, executionMetric!!.completedAt)

        // complete order
        val completeOrderResponse = http.completeOrder(orderInProgress.id.toString(), bearerToken)
        assertEquals(200, completeOrderResponse.statusCode())

        // assert order status is COMPLETED
        val orderCompleted = orderRepository.findById(orderInProgress.id)!!
        assertEquals(Order.Status.COMPLETED, orderCompleted.status)

        // validate public status endpoint shows COMPLETED
        val statusAfterCompletion = http.getOrderStatus(orderCompleted.id.toString())
        assertEquals(200, statusAfterCompletion.statusCode())
        assertEquals(Order.Status.COMPLETED, statusAfterCompletion.body().status)

        // assert execution metric was updated with completedAt
        waitFor {
            executionMetric = orderExecutionMetricRepository.findByOrderId(orderCompleted.id)
            executionMetric?.completedAt != null
        }
        assertNotNull(executionMetric!!.completedAt)

        // deliver order
        val deliverOrderResponse = http.deliverOrder(orderCompleted.id.toString(), bearerToken)
        assertEquals(200, deliverOrderResponse.statusCode())

        // assert order status is DELIVERED
        val orderDelivered = orderRepository.findById(orderCompleted.id)!!
        assertEquals(Order.Status.DELIVERED, orderDelivered.status)

        // validate public status endpoint shows DELIVERED
        val statusAfterDelivery = http.getOrderStatus(orderDelivered.id.toString())
        assertEquals(200, statusAfterDelivery.statusCode())
        assertEquals(Order.Status.DELIVERED, statusAfterDelivery.body().status)
    }
}
