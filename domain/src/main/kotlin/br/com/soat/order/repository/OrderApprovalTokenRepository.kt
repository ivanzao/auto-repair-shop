package br.com.soat.order.repository

import br.com.soat.order.model.OrderApprovalToken
import java.util.UUID

interface OrderApprovalTokenRepository {
    fun save(token: OrderApprovalToken): OrderApprovalToken
    fun findById(id: UUID): OrderApprovalToken?
    fun findByOrderId(orderId: UUID): OrderApprovalToken?
    fun update(token: OrderApprovalToken): OrderApprovalToken
}