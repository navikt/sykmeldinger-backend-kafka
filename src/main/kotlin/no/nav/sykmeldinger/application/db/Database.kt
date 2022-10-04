package no.nav.sykmeldinger.application.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.sykmeldinger.Environment
import java.sql.Connection
import java.sql.ResultSet
import java.util.Properties

class Database(
    env: Environment
) : DatabaseInterface {
    private val dataSource: HikariDataSource
    override val connection: Connection
        get() = dataSource.connection

    init {
        val properties = Properties()
        properties.setProperty("socketFactory", "com.google.cloud.sql.postgres.SocketFactory")
        properties.setProperty("cloudSqlInstance", env.cloudSqlInstance)
        dataSource = HikariDataSource(
            HikariConfig().apply {
                dataSourceProperties = properties
                jdbcUrl = "jdbc:postgresql://${env.dbHost}:${env.dbPort}/${env.dbName}"
                username = env.databaseUsername
                password = env.databasePassword
                maximumPoolSize = 10
                minimumIdle = 3
                idleTimeout = 10000
                maxLifetime = 300000
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                validate()
            }
        )
    }
}

fun <T> ResultSet.toList(mapper: ResultSet.() -> T) = mutableListOf<T>().apply {
    while (next()) {
        add(mapper())
    }
}
