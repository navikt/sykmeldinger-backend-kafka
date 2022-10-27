package no.nav.sykmeldinger.status.kafka

import io.kotest.core.spec.style.FunSpec
import org.amshove.kluent.shouldBeEqualTo
import java.time.OffsetDateTime

internal class SykmeldingStatusFixerKtTest : FunSpec({
    test("Test adjust timestamp") {
        val offsetTimestamp = OffsetDateTime.parse("2019-05-10T04:42:10Z")
        val correctTimestamp = OffsetDateTime.parse("2019-05-10T06:42:10Z")
        val adjustedTimestamp = adjustTimestamp(offsetTimestamp)
        adjustedTimestamp shouldBeEqualTo correctTimestamp
    }

    test("Test adjust timestamp with offset") {
        val offsetTimestamp = OffsetDateTime.parse("2019-12-12T20:23:00Z")
        val correctTimestamp = OffsetDateTime.parse("2019-12-12T21:23:00Z")
        val adjustedTimestamp = adjustTimestamp(offsetTimestamp)
        adjustedTimestamp shouldBeEqualTo correctTimestamp
    }
})
