package br.com.soat

import br.com.soat.config.Config
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.net.ServerSocket
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.testcontainers.containers.PostgreSQLContainer

abstract class IntegrationTest {

    protected var serverPort = ServerSocket(0).use { it.localPort }
    protected var server: KtorHttpServer? = null
    protected val postgresContainer = PostgreSQLContainer("postgres:18.1")

    protected val mapper = jacksonObjectMapper()
        .registerKotlinModule()

    @BeforeEach
    fun setup() {
        val config = Config.fromClasspath("application-test.yaml")

        postgresContainer.start()

        connectToDatabase(
            DatabaseConnectionParams(
                jdbcUrl = postgresContainer.jdbcUrl,
                driverClassName = postgresContainer.driverClassName,
                username = postgresContainer.username,
                password = postgresContainer.password,
                maximumPoolSize = config.getInt("database.maximumPoolSize")
            )
        )

        server = KtorHttpServer(
            applicationModule = applicationModule,
            port = serverPort,
            wait = false
        )

        server!!.start()
    }

    @AfterEach
    fun tearDown() {
        server?.stop()
        postgresContainer.stop()
    }
}