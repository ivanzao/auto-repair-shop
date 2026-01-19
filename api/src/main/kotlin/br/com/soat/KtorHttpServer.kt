package br.com.soat

import br.com.soat.auth.authenticationRoutes
import br.com.soat.customer.customerRoutes
import br.com.soat.order.orderRoutes
import br.com.soat.security.configureAuthentication
import br.com.soat.service.serviceRoutes
import br.com.soat.shared.dto.ErrorResponseDTO
import br.com.soat.shared.dto.FieldError
import br.com.soat.shared.dto.ValidationErrorDTO
import br.com.soat.shared.dto.toErrorResponseDTO
import br.com.soat.shared.exception.ApplicationException
import br.com.soat.supply.supplyRoutes
import br.com.soat.user.userRoutes
import br.com.soat.vehicle.vehicleRoutes
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.exc.InvalidNullException
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.databind.exc.ValueInstantiationException
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.datatype.jsr310.ser.ZonedDateTimeSerializer
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.TimeZone
import org.koin.core.Koin

class KtorHttpServer(
    private val koin: Koin,
    private val port: Int = 8080,
    private val gracePeriodMillis: Long = 1000,
    private val timeoutMillis: Long = 2000,
    private val wait: Boolean = true,
) : HttpServer {

    private var server: EmbeddedServer<*, *>? = null

    init {
        server = embeddedServer(Netty, port = port) {
            configureAuthentication(koin)
            configureRouting(koin)
            configureSerialization()
            configureLogging()
            configureErrorHandling()
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            server?.stop(1000, 2000)
        })
    }


    private fun Application.configureRouting(koin: Koin) {
        routing {
            get("/health") {
                call.respondText("OK")
            }
            swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")
        }

        userRoutes(koin)
        supplyRoutes(koin)
        serviceRoutes(koin)
        vehicleRoutes(koin)
        customerRoutes(koin)
        orderRoutes(koin)
        authenticationRoutes(koin)
    }

    private fun Application.configureLogging() {
        install(CallLogging)
    }

    private fun Application.configureSerialization() {
        install(ContentNegotiation) {
            jackson {
                registerModule(KotlinModule.Builder().build())
                registerModule(JavaTimeModule().apply {
                    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                    addSerializer(ZonedDateTime::class.java, ZonedDateTimeSerializer(formatter))
                })
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                setTimeZone(TimeZone.getTimeZone("UTC"))
            }
        }
    }

    private fun Application.configureErrorHandling() {
        install(StatusPages) {
            exception<ApplicationException> { call, cause ->
                call.respond(HttpStatusCode.UnprocessableEntity, cause.toErrorResponseDTO())
            }

            exception<BadRequestException> { call, cause ->
                val fieldErrors = extractFieldErrors(cause)
                call.respond(HttpStatusCode.BadRequest, ValidationErrorDTO(fieldErrors))
            }

            exception<Throwable> { call, cause ->
                val message = "${cause::class.simpleName}: ${cause.message}"
                call.respond(HttpStatusCode.InternalServerError, ErrorResponseDTO.internalServerError(message))
            }
        }
    }

    override fun start() {
        server?.start(wait = wait)
            ?: throw IllegalStateException("NettyApplicationEngine is not initialized")
    }

    override fun stop() {
        server?.stop(gracePeriodMillis, timeoutMillis)
        server = null
    }

    private fun findRootCause(throwable: Throwable): Throwable {
        var current: Throwable = throwable
        while (current.cause != null && current.cause != current) {
            current = current.cause!!
        }
        return current
    }

    private fun extractFieldErrors(cause: BadRequestException): List<FieldError> {
        return when (val rootCause = findRootCause(cause)) {
            is ValueInstantiationException -> {
                val fieldPath = rootCause.path.joinToString(".") { ref ->
                    ref.fieldName ?: "[${ref.index}]"
                }
                listOf(FieldError(field = fieldPath.ifEmpty { "unknown" }, message = "Field is required"))
            }
            is InvalidNullException -> {
                val fieldPath = rootCause.path.joinToString(".") { ref ->
                    ref.fieldName ?: "[${ref.index}]"
                }
                listOf(FieldError(field = fieldPath.ifEmpty { "unknown" }, message = "Field is required and cannot be null"))
            }
            is MismatchedInputException -> {
                val fieldPath = rootCause.path.joinToString(".") { ref ->
                    ref.fieldName ?: "[${ref.index}]"
                }
                val message = when {
                    rootCause.targetType?.isEnum == true -> {
                        val enumValues = rootCause.targetType.enumConstants.joinToString(", ")
                        "Invalid value. Accepted values: $enumValues"
                    }
                    fieldPath.isNotEmpty() -> "Invalid or missing value"
                    else -> rootCause.originalMessage ?: "Invalid value"
                }
                if (fieldPath.isNotEmpty()) {
                    listOf(FieldError(field = fieldPath, message = message))
                } else {
                    listOf(FieldError(field = "request", message = rootCause.originalMessage ?: "Invalid request body"))
                }
            }
            is JsonMappingException -> {
                val fieldPath = rootCause.path.joinToString(".") { ref ->
                    ref.fieldName ?: "[${ref.index}]"
                }
                if (fieldPath.isNotEmpty()) {
                    listOf(FieldError(field = fieldPath, message = rootCause.originalMessage ?: "Invalid format"))
                } else {
                    listOf(FieldError(field = "request", message = rootCause.originalMessage ?: "Invalid request body"))
                }
            }
            else -> {
                val message = rootCause.message ?: cause.message ?: "Invalid request"
                listOf(FieldError(field = "request", message = message))
            }
        }
    }
}