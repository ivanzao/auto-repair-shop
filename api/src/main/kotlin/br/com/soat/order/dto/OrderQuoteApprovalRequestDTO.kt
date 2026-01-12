package br.com.soat.order.dto

import java.util.UUID

data class OrderQuoteApprovalRequestDTO(
    val approvalToken: UUID,
)