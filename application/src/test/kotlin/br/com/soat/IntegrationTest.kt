package br.com.soat

import EventDispatcherWorker
import br.com.soat.config.Config
import br.com.soat.worker.command.CommandWorker
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.net.ServerSocket
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.testcontainers.containers.PostgreSQLContainer

abstract class IntegrationTest {

    protected var serverPort = ServerSocket(0).use { it.localPort }
    protected var server: KtorHttpServer? = null
    protected val postgresContainer = PostgreSQLContainer("postgres:18.1")
    protected var koinApplication: KoinApplication? = null

    protected val mapper = jacksonObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())

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

        koinApplication = startKoin {
            modules(applicationModule)
        }

        koinApplication!!.koin.get<CommandWorker>().start()
        koinApplication!!.koin.get<EventDispatcherWorker>().start()

        server = KtorHttpServer(
            koin = koinApplication!!.koin,
            port = serverPort,
            wait = false
        )

        server!!.start()
    }

    @AfterEach
    fun tearDown() {
        koinApplication!!.koin.get<EventDispatcherWorker>().stop()
        koinApplication!!.koin.get<CommandWorker>().stop()

        server?.stop()
        postgresContainer.stop()

        stopKoin()
    }
}