package br.com.soat.user

import br.com.soat.IntegrationTest
import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber
import br.com.soat.user.model.User
import java.time.LocalDateTime
import java.util.UUID

fun IntegrationTest.createUser(
    id: UUID = UUID.randomUUID(),
    createdAt: LocalDateTime = LocalDateTime.now(),
    modifiedAt: LocalDateTime = LocalDateTime.now(),
    version: Int = 0,
    name: String = "John",
    hashedPassword: String = "\$2a\$10\$dummyHashForTesting1234567890",
    document: Document = Document("12345678909"),
    email: Email = Email("john@example.com"),
    contact: PhoneNumber = PhoneNumber("11999999999"),
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