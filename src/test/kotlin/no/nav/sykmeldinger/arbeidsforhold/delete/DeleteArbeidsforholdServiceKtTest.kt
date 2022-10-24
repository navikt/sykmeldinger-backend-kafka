package no.nav.sykmeldinger.arbeidsforhold.delete

import io.kotest.core.spec.style.FunSpec
import org.amshove.kluent.shouldBeEqualTo
import java.time.LocalTime
import java.time.OffsetTime
import java.time.ZoneOffset

class DeleteArbeidsforholdServiceKtTest : FunSpec({
    context("getDelayTime") {
        test("Får riktig delaytime hvis start er etter nå") {
            val start = OffsetTime.of(LocalTime.of(7, 0), ZoneOffset.UTC)
            val now = OffsetTime.of(LocalTime.of(5, 0), ZoneOffset.UTC)

            getDelayTime(start, now) shouldBeEqualTo 7200000L
        }
        test("Får riktig delaytime hvis start er tidligere enn nå") {
            val start = OffsetTime.of(LocalTime.of(5, 0), ZoneOffset.UTC)
            val now = OffsetTime.of(LocalTime.of(7, 0), ZoneOffset.UTC)

            getDelayTime(start, now) shouldBeEqualTo 79200000L
        }
        test("Får riktig delaytime hvis start er lik nå") {
            val start = OffsetTime.of(LocalTime.of(5, 0), ZoneOffset.UTC)
            val now = OffsetTime.of(LocalTime.of(5, 0), ZoneOffset.UTC)

            getDelayTime(start, now) shouldBeEqualTo 0L
        }
    }
})
