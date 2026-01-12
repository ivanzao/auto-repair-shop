package br.com.soat.order

import br.com.soat.order.model.OrderApprovalToken
import kotlinx.datetime.toJavaLocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object OrderApprovalTokens : Table("order_approval_tokens") {
    val id = uuid("id")
    val createdAt = datetime("created_at")
    val modifiedAt = datetime("modified_at")
    val version = integer("version").default(0)
    val orderId = uuid("order_id").references(Orders.id)
    val expiresAt = datetime("expires_at")
    val usedAt = datetime("used_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

fun ResultRow.toOrderApprovalToken(): OrderApprovalToken = OrderApprovalToken(
    id = this[OrderApprovalTokens.id],
    createdAt = this[OrderApprovalTokens.createdAt].toJavaLocalDateTime(),
    modifiedAt = this[OrderApprovalTokens.modifiedAt].toJavaLocalDateTime(),
    version = this[OrderApprovalTokens.version],
    orderId = this[OrderApprovalTokens.orderId],
    expiresAt = this[OrderApprovalTokens.expiresAt].toJavaLocalDateTime(),
    usedAt = this[OrderApprovalTokens.usedAt]?.toJavaLocalDateTime()
)