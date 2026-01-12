package br.com.soat.order.repository

import br.com.soat.order.model.Order
import java.util.UUID

interface OrderRepository {
    fun findById(id: UUID): Order?
    fun create(order: Order): Order
    fun update(order: Order): Order
}