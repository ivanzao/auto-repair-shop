package br.com.soat.order

import br.com.soat.order.model.OrderApprovalToken
import br.com.soat.order.repository.OrderApprovalTokenRepository
import java.util.UUID
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class OrderApprovalTokenPostgresRepository : OrderApprovalTokenRepository {

    override fun save(token: OrderApprovalToken): OrderApprovalToken = transaction {
        OrderApprovalTokens.insert {
            it[id] = token.id
            it[createdAt] = token.createdAt.toKotlinLocalDateTime()
            it[modifiedAt] = token.modifiedAt.toKotlinLocalDateTime()
            it[version] = token.version
            it[orderId] = token.orderId
            it[expiresAt] = token.expiresAt.toKotlinLocalDateTime()
            it[usedAt] = token.usedAt?.toKotlinLocalDateTime()
        }.resultedValues?.singleOrNull()
            ?.toOrderApprovalToken()
            ?: throw IllegalStateException("Failed to save OrderApprovalToken")
    }

    override fun findById(id: UUID): OrderApprovalToken? = transaction {
        OrderApprovalTokens
            .selectAll()
            .where { OrderApprovalTokens.id eq id }
            .limit(1)
            .firstOrNull()
            ?.toOrderApprovalToken()
    }

    override fun findByOrderId(orderId: UUID): OrderApprovalToken? = transaction {
        OrderApprovalTokens
            .selectAll()
            .where { OrderApprovalTokens.orderId eq orderId }
            .limit(1)
            .firstOrNull()
            ?.toOrderApprovalToken()
    }

    override fun update(token: OrderApprovalToken): OrderApprovalToken = transaction {
        OrderApprovalTokens.update({ OrderApprovalTokens.id eq token.id }) {
            it[modifiedAt] = token.modifiedAt.toKotlinLocalDateTime()
            it[version] = token.version + 1
            it[usedAt] = token.usedAt?.toKotlinLocalDateTime()
        }
        findById(token.id) ?: throw IllegalStateException("Failed to update OrderApprovalToken")
    }
}