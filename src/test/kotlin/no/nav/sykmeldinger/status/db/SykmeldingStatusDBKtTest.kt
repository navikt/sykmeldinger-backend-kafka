package no.nav.sykmeldinger.status.db

import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.sykmeldinger.TestDB
import org.amshove.kluent.shouldBeEqualTo
import java.time.OffsetDateTime
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

internal class SykmeldingStatusDBKtTest : FunSpec({

    context("sykmelding status inserts") {
        test("should handle batch insert") {
            val testDb = TestDB.database
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

            val number = testDb.connection.use { connection ->
                connection.prepareStatement("select count(*) from sykmeldingstatus").use {
                    it.executeQuery().use {
                        it.next()
                        it.getInt(1)
                    }
                }
            }

            number shouldBeEqualTo 10
        }
    }
})
