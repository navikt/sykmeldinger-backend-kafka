package no.nav.sykmeldinger.narmesteleder

import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import no.nav.sykmeldinger.TestDB
import no.nav.sykmeldinger.narmesteleder.db.NarmestelederDb
import no.nav.sykmeldinger.narmesteleder.db.NarmestelederDbModel
import no.nav.sykmeldinger.narmesteleder.kafka.NarmestelederLeesahKafkaMessage
import no.nav.sykmeldinger.pdl.model.Navn
import no.nav.sykmeldinger.pdl.model.PdlPerson
import no.nav.sykmeldinger.pdl.service.PdlPersonService
import org.amshove.kluent.shouldBeEqualTo

object NarmesteLederServiceTest :
    FunSpec({
        val testDb = TestDB.database
        val pdlPersonService = mockk<PdlPersonService>()
        val narmesteLederService =
            NarmesteLederService(pdlPersonService, NarmestelederDb(testDb), "prod-gcp")

        val fnr = "12345678910"
        val lederFnr = "10987654321"
        val orgnummer = "888888888"
        val timestamp = OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC)).minusMinutes(5)

        beforeEach {
            coEvery { pdlPersonService.getPerson(any(), any()) } returns
                PdlPerson(Navn("Fornavn", "Mellomnavn", "Etternavn"), lederFnr, emptyList())
            TestDB.clearAllData()
        }

        context("NarmesteLederservice") {
            test("Lagrer nærmeste leder") {
                val nlId = UUID.randomUUID()
                narmesteLederService.updateNarmesteLeder(
                    NarmestelederLeesahKafkaMessage(
                        narmesteLederId = nlId,
                        fnr = fnr,
                        orgnummer = orgnummer,
                        narmesteLederFnr = lederFnr,
                        narmesteLederTelefonnummer = "tlf",
                        narmesteLederEpost = "epost",
                        aktivFom = LocalDate.now().minusYears(2),
                        aktivTom = null,
                        arbeidsgiverForskutterer = true,
                        timestamp = timestamp,
                    ),
                )

                val narmestelederFraDb = TestDB.getNarmesteleder(nlId.toString())
                narmestelederFraDb shouldBeEqualTo
                    NarmestelederDbModel(
                        narmestelederId = nlId.toString(),
                        orgnummer = orgnummer,
                        brukerFnr = fnr,
                        lederFnr = lederFnr,
                        navn = "Fornavn Mellomnavn Etternavn",
                        timestamp = timestamp,
                    )
            }
            test("Oppdaterer nærmeste leder") {
                coEvery { pdlPersonService.getPerson(any(), any()) } returns
                    PdlPerson(
                        Navn("Fornavn", "Mellomnavn", "Etternavn"),
                        lederFnr,
                        emptyList()
                    ) andThen
                    PdlPerson(Navn("Nytt", null, "Navn"), lederFnr, emptyList())

                val nlId = UUID.randomUUID()
                narmesteLederService.updateNarmesteLeder(
                    NarmestelederLeesahKafkaMessage(
                        narmesteLederId = nlId,
                        fnr = fnr,
                        orgnummer = orgnummer,
                        narmesteLederFnr = lederFnr,
                        narmesteLederTelefonnummer = "tlf",
                        narmesteLederEpost = "epost",
                        aktivFom = LocalDate.now().minusYears(2),
                        aktivTom = null,
                        arbeidsgiverForskutterer = true,
                        timestamp = timestamp,
                    ),
                )
                val timestampOppdatering = OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC))

                narmesteLederService.updateNarmesteLeder(
                    NarmestelederLeesahKafkaMessage(
                        narmesteLederId = nlId,
                        fnr = fnr,
                        orgnummer = orgnummer,
                        narmesteLederFnr = lederFnr,
                        narmesteLederTelefonnummer = "tlf2",
                        narmesteLederEpost = "epost2",
                        aktivFom = LocalDate.now().minusYears(2),
                        aktivTom = null,
                        arbeidsgiverForskutterer = true,
                        timestamp = timestampOppdatering,
                    ),
                )

                val narmestelederFraDb = TestDB.getNarmesteleder(nlId.toString())
                narmestelederFraDb shouldBeEqualTo
                    NarmestelederDbModel(
                        narmestelederId = nlId.toString(),
                        orgnummer = orgnummer,
                        brukerFnr = fnr,
                        lederFnr = lederFnr,
                        navn = "Nytt Navn",
                        timestamp = timestampOppdatering,
                    )
            }
            test("Sletter nærmeste leder") {
                val nlId = UUID.randomUUID()
                narmesteLederService.updateNarmesteLeder(
                    NarmestelederLeesahKafkaMessage(
                        narmesteLederId = nlId,
                        fnr = fnr,
                        orgnummer = orgnummer,
                        narmesteLederFnr = lederFnr,
                        narmesteLederTelefonnummer = "tlf",
                        narmesteLederEpost = "epost",
                        aktivFom = LocalDate.now().minusYears(2),
                        aktivTom = null,
                        arbeidsgiverForskutterer = true,
                        timestamp = timestamp,
                    ),
                )

                narmesteLederService.updateNarmesteLeder(
                    NarmestelederLeesahKafkaMessage(
                        narmesteLederId = nlId,
                        fnr = fnr,
                        orgnummer = orgnummer,
                        narmesteLederFnr = lederFnr,
                        narmesteLederTelefonnummer = "tlf",
                        narmesteLederEpost = "epost",
                        aktivFom = LocalDate.now().minusYears(2),
                        aktivTom = LocalDate.now(),
                        arbeidsgiverForskutterer = true,
                        timestamp = timestamp,
                    ),
                )

                val narmestelederFraDb = TestDB.getNarmesteleder(nlId.toString())
                narmestelederFraDb shouldBeEqualTo null
            }
        }
    })
