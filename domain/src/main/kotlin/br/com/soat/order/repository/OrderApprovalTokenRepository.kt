package br.com.soat.order.repository

import br.com.soat.order.model.OrderApprovalToken
import java.util.UUID

interface OrderApprovalTokenRepository {
    fun findById(id: UUID): OrderApprovalToken?
    fun findByOrderId(orderId: UUID): OrderApprovalToken?
    fun save(token: OrderApprovalToken): OrderApprovalToken
    fun update(token: OrderApprovalToken): OrderApprovalToken
}
