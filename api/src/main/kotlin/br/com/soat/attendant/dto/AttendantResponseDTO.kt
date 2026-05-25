package br.com.soat.attendant.dto

import br.com.soat.attendant.model.Attendant
import java.time.LocalDateTime
import java.util.UUID

data class AttendantResponseDTO(
    val id: UUID,
    val name: String,
    val document: String,
    val email: String,
    val contact: String,
    val createdAt: LocalDateTime,
    val modifiedAt: LocalDateTime,
) {
    companion object {
        fun from(attendant: Attendant) = AttendantResponseDTO(
            id = attendant.id,
            name = attendant.name,
            document = attendant.document.value,
            email = attendant.email.value,
            contact = attendant.contact.value,
            createdAt = attendant.createdAt,
            modifiedAt = attendant.modifiedAt,
        )
    }
}
