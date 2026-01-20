package br.com.soat.config

import br.com.soat.auth.port.AuthenticationTokenProvider
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.bearer
import io.ktor.server.response.respond
import org.koin.core.Koin

fun Application.configureAuthentication(koin: Koin) {
    val tokenProvider = koin.get<AuthenticationTokenProvider>()

    install(Authentication) {
        bearer("admin") {
            authenticate { credential ->
                val result = tokenProvider.validate(credential.token)

                if (result.isValid && result.role in listOf("ADMIN")) {
                    UserIdPrincipal(result.userId.toString())
                } else {
                    respond(HttpStatusCode.Unauthorized)
                }
            }
        }

        bearer("attendant") {
            authenticate { credential ->
                val result = tokenProvider.validate(credential.token)

                if (result.isValid && result.role in listOf("ADMIN", "ATTENDANT")) {
                    UserIdPrincipal(result.userId.toString())
                } else {
                    respond(HttpStatusCode.Unauthorized)
                }
            }
        }
    }
}