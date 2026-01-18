package br.com.soat.email

import br.com.soat.config.Config
import br.com.soat.mail.model.OrderQuoteApprovalEmailInput
import br.com.soat.order.model.OrderService
import com.mailersend.sdk.MailerSend
import com.mailersend.sdk.emails.Email
import com.mailersend.sdk.emails.Emails
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.unmockkConstructor
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MailerSendEmailServiceTest {

    private val config = mockk<Config>()
    private val emailsApi = mockk<Emails>(relaxed = true)

    @BeforeEach
    fun setup() {
        mockkConstructor(MailerSend::class)
        every { anyConstructed<MailerSend>().setToken(any()) } returns Unit
        every { anyConstructed<MailerSend>().emails() } returns emailsApi
    }

    @AfterEach
    fun tearDown() {
        unmockkConstructor(MailerSend::class)
    }

    @Test
    fun `should send email with correct recipient and subject`() {
        every { config.getString("mailersend.token") } returns "test-token"
        every { config.getString("application.url") } returns "http://localhost:8080"

        val emailSlot = slot<Email>()
        every { emailsApi.send(capture(emailSlot)) } returns mockk(relaxed = true)

        val service = MailerSendEmailService(config)

        val input = OrderQuoteApprovalEmailInput(
            customerName = "João Silva",
            customerEmail = "joao@email.com",
            services = listOf(
                OrderService(
                    name = "Troca de óleo",
                    description = "Troca de óleo do motor",
                    price = BigDecimal("150.00")
                )
            ),
            supplies = listOf(
                OrderQuoteApprovalEmailInput.Supply(
                    name = "Óleo 5W30",
                    quantity = 4,
                    price = BigDecimal("50.00")
                )
            ),
            callbackToken = "abc123"
        )

        service.sendOrderQuoteApprovalEmail(input)

        verify(exactly = 1) { emailsApi.send(any()) }

        val sentEmail = emailSlot.captured
        assertTrue(sentEmail.recipients.any { it.email == "joao@email.com" && it.name == "João Silva" })
    }

    @Test
    fun `should replace template variables correctly`() {
        every { config.getString("mailersend.token") } returns "test-token"
        every { config.getString("application.url") } returns "http://localhost:8080"

        val emailSlot = slot<Email>()
        every { emailsApi.send(capture(emailSlot)) } returns mockk(relaxed = true)

        val service = MailerSendEmailService(config)

        val input = OrderQuoteApprovalEmailInput(
            customerName = "Maria Santos",
            customerEmail = "maria@email.com",
            services = listOf(
                OrderService(
                    name = "Alinhamento",
                    description = "Alinhamento de rodas",
                    price = BigDecimal("80.00")
                ),
                OrderService(
                    name = "Balanceamento",
                    description = "Balanceamento de rodas",
                    price = BigDecimal("60.00")
                )
            ),
            supplies = listOf(
                OrderQuoteApprovalEmailInput.Supply(
                    name = "Peso de chumbo",
                    quantity = 8,
                    price = BigDecimal("5.00")
                )
            ),
            callbackToken = "token-xyz"
        )

        service.sendOrderQuoteApprovalEmail(input)

        val sentEmail = emailSlot.captured
        val html = sentEmail.html

        assertTrue(html.contains("token-xyz"))
        assertTrue(html.contains("http://localhost:8080"))
        assertTrue(html.contains("Alinhamento"))
        assertTrue(html.contains("Balanceamento"))
        assertTrue(html.contains("Peso de chumbo"))
        assertTrue(html.contains("145"))
    }
}