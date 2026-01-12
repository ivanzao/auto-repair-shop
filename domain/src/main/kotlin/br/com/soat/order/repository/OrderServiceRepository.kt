package br.com.soat.order.repository

import br.com.soat.order.model.OrderService
import java.util.UUID

interface OrderServiceRepository {
    fun create(service: OrderService): OrderService
    fun update(service: OrderService): OrderService
    fun findById(id: UUID): OrderService?
    fun findAllByIds(servicesIds: List<UUID>): List<OrderService>
}