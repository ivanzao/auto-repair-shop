package br.com.soat.order.repository

import br.com.soat.order.model.OrderSchedule
import java.util.UUID

interface OrderScheduleRepository {
    fun findAllByOrderId(orderId: UUID): List<OrderSchedule>
    fun save(orderSchedule: OrderSchedule): OrderSchedule
}