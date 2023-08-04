package no.nav.sykmeldinger

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import no.nav.sykmeldinger.application.db.DatabaseInterface
import no.nav.sykmeldinger.application.db.toList
import no.nav.sykmeldinger.narmesteleder.db.NarmestelederDbModel
import no.nav.sykmeldinger.narmesteleder.db.toNarmestelederDbModel
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy

class PsqlContainer : PostgreSQLContainer<PsqlContainer>("postgres:14.4")

class TestDatabase(val connectionName: String, val dbUsername: String, val dbPassword: String) :
    DatabaseInterface {
    private val dataSource: HikariDataSource
    override val connection: Connection
        get() = dataSource.connection

    init {
        dataSource =
            HikariDataSource(
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
                },
            )
        runFlywayMigrations()
    }

    private fun runFlywayMigrations() =
        Flyway.configure().run {
            locations("db")
            configuration(mapOf("flyway.postgresql.transactional.lock" to "false"))
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
                psqlContainer =
                    PsqlContainer()
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

        fun clearAllData() {
            return database.connection.use {
                it.prepareStatement(
                        """
                    DELETE FROM narmesteleder;
                    DELETE FROM sykmeldingstatus;
                    DELETE FROM arbeidsforhold;
                    DELETE FROM sykmelding;
                    DELETE FROM sykmeldt;
                """,
                    )
                    .use { ps -> ps.executeUpdate() }
                it.commit()
            }
        }

        fun getNarmesteleder(narmestelederId: String): NarmestelederDbModel? {
            return database.connection.use {
                it.prepareStatement(
                        """
                    SELECT * FROM narmesteleder WHERE narmeste_leder_id = ?;
                """,
                    )
                    .use { ps ->
                        ps.setString(1, narmestelederId)
                        ps.executeQuery().toList { toNarmestelederDbModel() }.firstOrNull()
                    }
            }
        }
    }
}
