package no.nav.sykmeldinger.status.db

import io.kotest.core.spec.style.FunSpec
import no.nav.sykmeldinger.TestDB
import java.time.OffsetDateTime

internal class SykmeldingStatusDBKtTest : FunSpec({

    context("sykmelding status inserts") {
        test("should handle concurrent updates") {
            val testDb = TestDB.database
            val timestamp = OffsetDateTime.now()
            testDb.insertStatus("1", "OPEN", timestamp)
        }
    }
})