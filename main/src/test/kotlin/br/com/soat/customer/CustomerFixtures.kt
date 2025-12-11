package br.com.soat.customer

import br.com.soat.IntegrationTest
import br.com.soat.customer.model.Customer
import java.time.LocalDateTime
import java.util.UUID
import kotlin.random.Random

fun IntegrationTest.createCustomer(
    id: UUID = UUID.randomUUID(),
    createdAt: LocalDateTime = LocalDateTime.now(),
    modifiedAt: LocalDateTime = LocalDateTime.now(),
    version: Int = 0,
    name: String = "John",
    document: String = Random.nextLong(10000000000L, 99999999999L).toString(),
    email: String = "john@example.com",
    contact: String = "1234567890"
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