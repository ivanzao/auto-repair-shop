package br.com.soat.user

import br.com.soat.IntegrationTest
import br.com.soat.user.model.User
import java.time.LocalDateTime
import java.util.UUID
import kotlin.random.Random

fun IntegrationTest.createUser(
    id: UUID = UUID.randomUUID(),
    createdAt: LocalDateTime = LocalDateTime.now(),
    modifiedAt: LocalDateTime = LocalDateTime.now(),
    version: Int = 0,
    name: String = "John",
    hashedPassword: String = "\$2a\$10\$dummyHashForTesting1234567890",
    document: String = Random.nextLong(10000000000L, 99999999999L).toString(),
    email: String = "john@example.com",
    contact: String = "(11) 99999-9999",
    role: User.Role = User.Role.ADMIN
) = get<UserRepository>().create(
    User(
        id = id,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        version = version,
        name = name,
        hashedPassword = hashedPassword,
        document = document,
        email = email,
        contact = contact,
        role = role
    )
)