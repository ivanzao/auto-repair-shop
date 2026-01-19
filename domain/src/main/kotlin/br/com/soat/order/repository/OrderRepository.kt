package br.com.soat.order.repository

import br.com.soat.order.model.Order
import br.com.soat.shared.model.Page
import java.util.UUID

interface OrderRepository {
    fun findById(id: UUID): Order?
    fun findAllPaginated(page: Int): Page<Order>
    fun create(order: Order): Order
    fun update(order: Order): Order
}