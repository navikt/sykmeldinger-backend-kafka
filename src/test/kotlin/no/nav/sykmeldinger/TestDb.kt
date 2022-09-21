package no.nav.sykmeldinger

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.sykmeldinger.application.db.DatabaseInterface
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import java.sql.Connection

class PsqlContainer : PostgreSQLContainer<PsqlContainer>("postgres:14")

class TestDatabase(val connectionName: String, val dbUsername: String, val dbPassword:String) : DatabaseInterface {
    private val dataSource: HikariDataSource
    override val connection: Connection
        get() = dataSource.connection

    init {
        dataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = connectionName
                username = dbUsername
                password = dbPassword
                maximumPoolSize = 1
                minimumIdle = 1
                isAutoCommit = false
                connectionTimeout = 10_000
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                validate()
            }
        )
        runFlywayMigrations()
    }
    private fun runFlywayMigrations() = Flyway.configure().run {
        locations("db")
        dataSource(connectionName, dbUsername, dbPassword)
        load().migrate()
    }
}

class TestDB private constructor() {
    companion object {
        val database: DatabaseInterface

        private val psqlContainer: PsqlContainer

        init {
            try {
                psqlContainer = PsqlContainer()
                    .withExposedPorts(5432)
                    .withUsername("username")
                    .withPassword("password")
                    .withDatabaseName("database")

                psqlContainer.waitingFor(HostPortWaitStrategy())
                psqlContainer.start()
                val username = "username"
                val password = "password"
                val connectionName = psqlContainer.jdbcUrl

                database = TestDatabase(connectionName, username, password)
            } catch (ex: Exception) {
                log.error("Error", ex)
                throw ex
            }
        }
    }
}
