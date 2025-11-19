package br.com.soat

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.flywaydb.core.Flyway

fun connectToDatabase(params: DatabaseConnectionParams) {
    val datasource = HikariDataSource(HikariConfig().apply {
        jdbcUrl = params.jdbcUrl
        driverClassName = params.driverClassName
        username = params.username
        password = params.password
        maximumPoolSize = params.maximumPoolSize
        isReadOnly = false
        transactionIsolation = "TRANSACTION_SERIALIZABLE"
    })

    Database.connect(datasource)


    Flyway.configure()
        .dataSource(datasource)
        .locations("classpath:db/migration")
        .load()
        .migrate()
}

data class DatabaseConnectionParams(
    val jdbcUrl: String,
    val driverClassName: String,
    val username: String,
    val password: String,
    val maximumPoolSize: Int
)