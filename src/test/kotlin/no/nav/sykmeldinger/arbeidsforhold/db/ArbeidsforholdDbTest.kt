package no.nav.sykmeldinger.arbeidsforhold.db

import io.kotest.core.spec.style.FunSpec
import java.time.LocalDate
import no.nav.sykmeldinger.TestDB
import no.nav.sykmeldinger.arbeidsforhold.model.Arbeidsforhold
import no.nav.sykmeldinger.arbeidsforhold.model.ArbeidsforholdType
import org.amshove.kluent.shouldBeEqualTo

object ArbeidsforholdDbTest :
    FunSpec({
        val testDb = TestDB.database
        val arbeidsforholdDb = ArbeidsforholdDb(testDb)
        val slettingsdato = LocalDate.now().minusMonths(4)

        beforeTest { TestDB.clearAllData() }

        context("ArbeidsforholdDb - deleteOldArbeidsforhold") {
            test("deleteOldArbeidsforhold sletter arbeidsforhold med tom for mer enn 4 mnd siden") {
                val arbeidsforhold =
                    Arbeidsforhold(
                        id = 5,
                        fnr = "12345678910",
                        orgnummer = "888888888",
                        juridiskOrgnummer = "999999999",
                        orgNavn = "Bedriften AS",
                        fom = LocalDate.of(2020, 5, 1),
                        tom = LocalDate.now().minusMonths(5),
                        type = ArbeidsforholdType.ORDINAERT_ARBEIDSFORHOLD,
                    )
                arbeidsforholdDb.insertOrUpdate(arbeidsforhold)

                val antallSlettedeArbeidsforhold =
                    arbeidsforholdDb.deleteOldArbeidsforhold(slettingsdato)

                antallSlettedeArbeidsforhold shouldBeEqualTo 1
            }
            test(
                "deleteOldArbeidsforhold sletter ikke arbeidsforhold med tom for mindre enn 4 mnd siden"
            ) {
                val arbeidsforhold =
                    Arbeidsforhold(
                        id = 5,
                        fnr = "12345678910",
                        orgnummer = "888888888",
                        juridiskOrgnummer = "999999999",
                        orgNavn = "Bedriften AS",
                        fom = LocalDate.of(2020, 5, 1),
                        tom = LocalDate.now().minusMonths(3),
                        type = ArbeidsforholdType.ORDINAERT_ARBEIDSFORHOLD,
                    )
                arbeidsforholdDb.insertOrUpdate(arbeidsforhold)

                val antallSlettedeArbeidsforhold =
                    arbeidsforholdDb.deleteOldArbeidsforhold(slettingsdato)

                antallSlettedeArbeidsforhold shouldBeEqualTo 0
            }
            test("deleteOldArbeidsforhold sletter ikke arbeidsforhold hvis tom er null") {
                val arbeidsforhold =
                    Arbeidsforhold(
                        id = 5,
                        fnr = "12345678910",
                        orgnummer = "888888888",
                        juridiskOrgnummer = "999999999",
                        orgNavn = "Bedriften AS",
                        fom = LocalDate.of(2020, 5, 1),
                        tom = null,
                        type = ArbeidsforholdType.ORDINAERT_ARBEIDSFORHOLD,
                    )
                arbeidsforholdDb.insertOrUpdate(arbeidsforhold)

                val antallSlettedeArbeidsforhold =
                    arbeidsforholdDb.deleteOldArbeidsforhold(slettingsdato)

                antallSlettedeArbeidsforhold shouldBeEqualTo 0
            }
        }
    })
