package br.com.soat

import br.com.soat.auth.dto.AuthenticateUserRequestDTO
import br.com.soat.config.Config
import br.com.soat.config.fromClasspath
import br.com.soat.consumer.CommandConsumerWorker
import br.com.soat.consumer.EventConsumerWorker
import br.com.soat.mail.EmailService
import br.com.soat.scheduler.ScheduledTaskRunner
import br.com.soat.security.HashService
import br.com.soat.shared.vo.Document
import br.com.soat.shared.vo.Email
import br.com.soat.shared.vo.PhoneNumber
import br.com.soat.user.UserRepository
import br.com.soat.user.model.User
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

    val httpClient: IntegrationTestHttpClient
        get() = http

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
        postgresContainer.start()

        val config = Config.fromClasspath("application-test.yaml").apply {
            put("database.url", postgresContainer.jdbcUrl)
            put("database.username", postgresContainer.username)
            put("database.password", postgresContainer.password)
            put("database.driverClassName", postgresContainer.driverClassName)
            put("server.port", serverPort)
        }

        val dataSource = connectToDatabase(config)

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
        get<ScheduledTaskRunner>().stop()
        stopKoin()
    }

    inline fun <reified T> get(): T = koinApplication.koin.get()

    fun loginAsAdmin(): String {
        val userRepository = get<UserRepository>()
        val hashService = get<HashService>()

        val adminEmail = "admin@test.com"
        val adminPassword = "admin123"

        if (userRepository.findByEmail(Email(adminEmail)) == null) {
            userRepository.create(
                User(
                    name = "Admin Test",
                    email = Email(adminEmail),
                    document = Document("99988877766"),
                    contact = PhoneNumber("11999999999"),
                    hashedPassword = hashService.hash(adminPassword),
                    role = User.Role.ADMIN
                )
            )
        }

        val loginResponse = http.login(
            AuthenticateUserRequestDTO(
                email = adminEmail,
                password = adminPassword
            )
        )

        return loginResponse.body().accessToken
    }

    private val testModule = module {
        single<Config> { Config.fromClasspath("application-test.yaml") }
        single<EmailService> { mockk() }
    }
}