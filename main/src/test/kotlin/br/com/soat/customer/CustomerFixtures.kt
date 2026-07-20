package br.com.soat.customer

import br.com.soat.IntegrationTest
import br.com.soat.customer.repository.CustomerRepository
import br.com.soat.customer.model.Customer
import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber
import java.time.LocalDateTime
import java.util.UUID

fun IntegrationTest.createCustomer(
    id: UUID = UUID.randomUUID(),
    createdAt: LocalDateTime = LocalDateTime.now(),
    modifiedAt: LocalDateTime = LocalDateTime.now(),
    version: Int = 0,
    name: String = "John",
    document: Document = Document("12345678909"),
    email: Email = Email("john@example.com"),
    contact: PhoneNumber = PhoneNumber("11987654321")
) = get<CustomerRepository>().create(
    Customer(
        id = id,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        version = version,
        name = name,
        document = document,
        email = email,
        contact = contact
    )
)