package br.com.soat.attendant

import br.com.soat.IntegrationTest
import br.com.soat.attendant.model.Attendant
import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber
import java.util.UUID

fun IntegrationTest.createAttendant(
    name: String = "Test Attendant",
    document: String = "11122233344",
    email: String = "attendant_${UUID.randomUUID()}@test.com",
    contact: String = "11999990000",
): Attendant {
    val repo = get<AttendantRepository>()
    return repo.create(
        Attendant(
            name = name,
            document = Document(document),
            email = Email(email),
            contact = PhoneNumber(contact),
        )
    )
}
