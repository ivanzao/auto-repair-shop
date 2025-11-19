package br.com.soat

import br.com.soat.br.com.soat.user.UserUseCase
import br.com.soat.br.com.soat.user.port.UserStoragePort
import br.com.soat.config.Config
import br.com.soat.user.UserPostgresStorage
import org.koin.dsl.module
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("br.com.soat.ApplicationKt")

fun main() {
    logger.info("Starting Application")

    val config = Config.fromClasspath("application.yaml")
    logger.info("Loaded ${config.size()} configs from application.yaml")

    connectToDatabase(
        DatabaseConnectionParams(
            jdbcUrl = config.getString("database.url"),
            driverClassName = config.getString("database.driverClassName"),
            username = config.getString("database.username"),
            password = config.getString("database.password"),
            maximumPoolSize = config.getInt("database.maximumPoolSize")
        )
    )

    KtorHttpServer(
        applicationModule = applicationModule,
        port = config.getInt("server.port"),
        wait = true
    ).start()
}

val applicationModule = module {
    // storage
    single<UserStoragePort> { UserPostgresStorage() }

    // domain
    single<UserUseCase> { UserUseCase(get()) }
}