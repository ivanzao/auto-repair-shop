package br.com.soat.mail.model

import br.com.soat.order.model.OrderService
import java.math.BigDecimal

data class OrderQuoteApprovalEmailInput(
    val customerName: String,
    val customerEmail: String,
    val services: List<OrderService>,
    val supplies: List<Supply>,
    val callbackToken: String
) {

    data class Supply(val name: String, val quantity: Int, val price: BigDecimal)
}