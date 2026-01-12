package br.com.soat

import br.com.soat.config.Config
import br.com.soat.config.fromClasspath
import br.com.soat.consumer.CommandConsumerWorker
import br.com.soat.consumer.EventConsumerWorker
import br.com.soat.mail.EmailService
import br.com.soat.scheduler.ScheduledTaskRunner
import io.mockk.mockk
import java.net.ServerSocket
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.testcontainers.containers.PostgreSQLContainer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class IntegrationTest {

    protected var serverPort = ServerSocket(0).use { it.localPort }
    protected val postgresContainer = PostgreSQLContainer("postgres:18.1").withReuse(true)!!

    lateinit var koinApplication: KoinApplication
    lateinit var server: KtorHttpServer
    lateinit var http: IntegrationTestHttpClient

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
    open fun setup() {
        val config = Config.fromClasspath("application-test.yaml")

        postgresContainer.start()

        val dataSource = connectToDatabase(
            DatabaseConnectionParams(
                jdbcUrl = postgresContainer.jdbcUrl,
                driverClassName = postgresContainer.driverClassName,
                username = postgresContainer.username,
                password = postgresContainer.password,
                maximumPoolSize = config.getInt("database.maximumPoolSize")
            )
        )

        koinApplication = startKoin { modules(applicationModule, testModule) }

        http = IntegrationTestHttpClient(serverPort)

        get<EventConsumerWorker>().start()
        get<CommandConsumerWorker>().start()

        get<ScheduledTaskRunner>().start(dataSource)

        server = KtorHttpServer(
            koin = koinApplication.koin,
            port = serverPort,
            wait = false
        )

        server.start()
    }

    @AfterAll
    fun tearDown() {
        server.stop()
        postgresContainer.stop()

        stopKoin()
    }

    inline fun <reified T> get(): T = koinApplication.koin.get()

    private val testModule = module {
        single<Config> { Config.fromClasspath("application-test.yaml") }
        single<EmailService> { mockk() }
    }
}