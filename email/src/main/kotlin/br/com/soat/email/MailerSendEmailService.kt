package br.com.soat.email

import br.com.soat.config.Config
import br.com.soat.mail.EmailService
import br.com.soat.mail.model.OrderQuoteApprovalEmailInput
import br.com.soat.shared.util.readFileFromResource
import com.mailersend.sdk.MailerSend
import com.mailersend.sdk.emails.Email

class MailerSendEmailService(config: Config) : EmailService {

    private val ms = MailerSend()
        .apply { setToken(config.getString("mailersend.token")) }

    override fun sendOrderQuoteApprovalEmail(input: OrderQuoteApprovalEmailInput) {
        val template = readFileFromResource("email-template.html")
        val email = Email().apply {
            addRecipient(input.customerName, input.customerEmail)
            setSubject("Order Quote Approval")
            setHtml(template.setVariables(input))
        }

        ms.emails().send(email)
    }

    private fun String.setVariables(input: OrderQuoteApprovalEmailInput) =
        replace("{{callbackToken}}", input.callbackToken)
            .replace("{{serviceTable}}", input.services.joinToString("\n") { "<tr><td>${it.name}</td><td>R$ ${it.price}</td></tr>" })
            .replace("{{suppliesTable}}", input.supplies.joinToString("\n") { "<tr><td>${it.name}</td><td>${it.quantity}</td><td>R$ ${it.price}</td><td>R$ ${input.supplies.filter { supply -> supply.name == it.name }.sumOf { it.price }}</td></tr>" })
            .replace("{{total}}", (input.services.sumOf { it.price } + input.supplies.sumOf { it.price }).toString())
}


