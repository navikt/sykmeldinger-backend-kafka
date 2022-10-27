package no.nav.sykmeldinger.status.db

import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.sykmeldinger.TestDB
import no.nav.sykmeldinger.status.kafka.adjustTimestamp
import org.amshove.kluent.shouldBeEqualTo
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

object SykmeldingStatusDBKtTest : FunSpec({

    context("sykmelding status inserts") {
        val testDb = SykmeldingStatusDB(TestDB.database)
        test("should handle batch insert") {

            val timestamp = OffsetDateTime.now()
            val batch = (0..9).map {
                SykmeldingStatusKafkaEventDTO(it.toString(), timestamp, "APEN", null, null)
            }
            val sum = batch.chunked(10).map { chunk ->
                async(Dispatchers.IO) {
                    testDb.insertStatus(chunk)
                }
            }.awaitAll().sum()

            sum shouldBeEqualTo 10

            val number = TestDB.database.connection.use { connection ->
                connection.prepareStatement("select count(*) from sykmeldingstatus").use {
                    it.executeQuery().use {
                        it.next()
                        it.getInt(1)
                    }
                }
            }

            number shouldBeEqualTo 10
        }

        test("Should update statustimestamp") {

            val sykmeldingId = UUID.randomUUID().toString()
            val apenStatus = SykmeldingStatusKafkaEventDTO(
                sykmeldingId = sykmeldingId,
                timestamp = OffsetDateTime.parse("2019-05-10T06:23:10Z"),
                statusEvent = "APEN",
                arbeidsgiver = null,
                sporsmals = null
            )
            val status = SykmeldingStatusKafkaEventDTO(
                sykmeldingId = sykmeldingId,
                timestamp = OffsetDateTime.parse("2019-05-10T04:42:10Z"),
                statusEvent = "SENDT",
                arbeidsgiver = null,
                sporsmals = null
            )
            testDb.insertStatus(listOf(apenStatus, status))

            val latestStatus = getLatestStatus(sykmeldingId)
            latestStatus shouldBeEqualTo apenStatus

            testDb.updateStatusTimestamp(
                status.sykmeldingId,
                status.timestamp,
                adjustTimestamp(status.timestamp),
                status.statusEvent
            )

            val newLatestStatus = getLatestStatus(sykmeldingId)
            newLatestStatus shouldBeEqualTo status.copy(timestamp = adjustTimestamp(status.timestamp))
        }
    }
})

private fun getLatestStatus(id: String): SykmeldingStatusKafkaEventDTO {
    return TestDB.database.connection.use { connection ->
        connection.prepareStatement(
            """
                    select * from sykmeldingstatus where sykmelding_id = ? order by timestamp desc limit 1
            """.trimIndent()
        ).use {
            it.setString(
                1,
                id
            )
            it.executeQuery().let {
                it.next()
                SykmeldingStatusKafkaEventDTO(
                    sykmeldingId = it.getString("sykmelding_id"),
                    timestamp = it.getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC),
                    statusEvent = it.getString("event"),
                    arbeidsgiver = null,
                    sporsmals = null
                )
            }
        }
    }
}
