package br.com.soat

import br.com.soat.config.Config
import br.com.soat.config.fromClasspath
import br.com.soat.worker.command.CommandProcessorWorker
import br.com.soat.worker.event.EventProcessorWorker
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.net.ServerSocket
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.testcontainers.containers.PostgreSQLContainer
import org.jetbrains.exposed.sql.transactions.transaction

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class IntegrationTest {

    protected var serverPort = ServerSocket(0).use { it.localPort }
    protected var server: KtorHttpServer? = null
    protected val postgresContainer = PostgreSQLContainer("postgres:18.1").withReuse(true)

    protected val mapper = jacksonObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())

    var koinApplication: KoinApplication? = null

    @BeforeEach
    fun cleanDatabase() {
        transaction {
            exec("""
                DO ${'$'}${'$'} DECLARE
                    r RECORD;
                BEGIN
                    FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
                        EXECUTE 'TRUNCATE TABLE ' || quote_ident(r.tablename) || ' CASCADE';
                    END LOOP;
                END ${'$'}${'$'};
            """.trimIndent())
        }
    }

    @BeforeAll
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

        get<CommandProcessorWorker>().start()
        get<EventProcessorWorker>().start()

        server = KtorHttpServer(
            koin = koinApplication!!.koin,
            port = serverPort,
            wait = false
        )

        server!!.start()
    }

    @AfterAll
    fun tearDown() {
        get<CommandProcessorWorker>().stop()
        get<EventProcessorWorker>().stop()

        server?.stop()
        postgresContainer.stop()

        stopKoin()
    }

    inline fun <reified T> get(): T = koinApplication!!.koin.get()
}