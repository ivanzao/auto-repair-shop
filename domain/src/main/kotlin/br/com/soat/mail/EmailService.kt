package br.com.soat.mail

import br.com.soat.mail.model.OrderQuoteApprovalEmailInput

interface EmailService {
    fun sendOrderQuoteApprovalEmail(input: OrderQuoteApprovalEmailInput)
}