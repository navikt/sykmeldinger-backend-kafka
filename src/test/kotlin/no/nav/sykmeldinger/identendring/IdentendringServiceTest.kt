package no.nav.sykmeldinger.identendring

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import java.util.UUID
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.sykmeldinger.TestDB
import no.nav.sykmeldinger.arbeidsforhold.db.ArbeidsforholdDb
import no.nav.sykmeldinger.pdl.error.PersonNotFoundInPdl
import no.nav.sykmeldinger.pdl.model.Navn
import no.nav.sykmeldinger.pdl.model.PdlPerson
import no.nav.sykmeldinger.pdl.service.PdlPersonService
import no.nav.sykmeldinger.sykmelding.db.SykmeldingDb
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBeEqualTo

object IdentendringServiceTest :
    FunSpec({
        val testDb = TestDB.database
        val arbeidsforholdDb = ArbeidsforholdDb(testDb)
        val sykmeldingDb = SykmeldingDb(testDb)
        val pdlPersonService = mockk<PdlPersonService>()
        val identendringService = IdentendringService(sykmeldingDb, pdlPersonService)

        beforeTest {
            TestDB.clearAllData()
            clearMocks(
                pdlPersonService,
            )
            coEvery { pdlPersonService.getPerson(any(), any()) } returns
                PdlPerson(
                    navn = Navn("Fornavn", null, "Etternavn"),
                    fnr = "10987654321",
                    oldFnr = emptyList(),
                    foedselsdato = null,
                )
        }

        context("Update ident") {
            test("Update fnr should no throw duplicate key violation") {
                val fnr = "12345678910"
                val nyttFnr = "10987654321"
                coEvery { pdlPersonService.getPerson(any(), any()) } returns
                    PdlPerson(
                        navn = Navn("Fornavn", null, "Etternavn"),
                        fnr = "10987654321",
                        oldFnr = listOf(fnr),
                        foedselsdato = null,
                    )
                val sykmeldingId = UUID.randomUUID().toString()
                arbeidsforholdDb.insertOrUpdate(getArbeidsforhold(fnr))
                sykmeldingDb.saveOrUpdateSykmeldt(getSykmeldt(fnr))
                sykmeldingDb.saveOrUpdateSykmeldt(getSykmeldt(nyttFnr))
                sykmeldingDb.saveOrUpdate(
                    sykmeldingId,
                    getSykmelding(),
                    getSykmeldt(fnr),
                    validationResult = ValidationResult(Status.OK, emptyList())
                )
                val identListe =
                    listOf(
                        nyttFnr,
                        fnr,
                        "2222",
                    )
                identendringService.updateIdent(identListe)

                sykmeldingDb.getSykmeldt(fnr) shouldBeEqualTo null
                sykmeldingDb.getSykmeldt(nyttFnr)?.fornavn shouldBeEqualTo "Annet"
                sykmeldingDb.getSykmeldingIds(fnr).size shouldBeEqualTo 0
                sykmeldingDb.getSykmeldingIds(nyttFnr).size shouldBeEqualTo 1
                arbeidsforholdDb.getArbeidsforhold(fnr).size shouldBeEqualTo 0
                arbeidsforholdDb.getArbeidsforhold(nyttFnr).size shouldBeEqualTo 1
            }
            test("Opdater ident") {
                val fnr = "12345678910"
                val nyttFnr = "10987654321"
                coEvery { pdlPersonService.getPerson(any(), any()) } returns
                    PdlPerson(
                        navn = Navn("Fornavn", null, "Etternavn"),
                        fnr = "10987654321",
                        oldFnr = listOf(fnr),
                        foedselsdato = null,
                    )
                val sykmeldingId = UUID.randomUUID().toString()
                arbeidsforholdDb.insertOrUpdate(getArbeidsforhold(fnr))
                sykmeldingDb.saveOrUpdateSykmeldt(getSykmeldt(fnr))
                sykmeldingDb.saveOrUpdate(
                    sykmeldingId,
                    getSykmelding(),
                    getSykmeldt(fnr),
                    validationResult = ValidationResult(Status.OK, emptyList())
                )
                val identListe =
                    listOf(
                        nyttFnr,
                        fnr,
                        "2222",
                    )
                identendringService.updateIdent(identListe)

                sykmeldingDb.getSykmeldt(fnr) shouldBeEqualTo null
                sykmeldingDb.getSykmeldt(nyttFnr)?.fornavn shouldBeEqualTo "Annet"
                sykmeldingDb.getSykmeldingIds(fnr).size shouldBeEqualTo 0
                sykmeldingDb.getSykmeldingIds(nyttFnr).size shouldBeEqualTo 1
                arbeidsforholdDb.getArbeidsforhold(fnr).size shouldBeEqualTo 0
                arbeidsforholdDb.getArbeidsforhold(nyttFnr).size shouldBeEqualTo 1
            }
        }
        context("IdentendringService oppdater ident ") {
            test("Oppdaterer sykmeldt, sykmelding og arbeidsforhold ved nytt fnr") {
                val fnr = "12345678910"
                val nyttFnr = "10987654321"

                coEvery { pdlPersonService.getPerson(any(), any()) } returns
                    PdlPerson(
                        navn = Navn("Fornavn", null, "Etternavn"),
                        fnr = nyttFnr,
                        oldFnr = listOf(fnr),
                        foedselsdato = null,
                    )

                val sykmeldingId = UUID.randomUUID().toString()
                arbeidsforholdDb.insertOrUpdate(getArbeidsforhold(fnr))
                sykmeldingDb.saveOrUpdateSykmeldt(getSykmeldt(fnr))
                sykmeldingDb.saveOrUpdate(
                    sykmeldingId,
                    getSykmelding(),
                    getSykmeldt(fnr),
                    validationResult = ValidationResult(Status.OK, emptyList())
                )
                val identListe =
                    listOf(
                        nyttFnr,
                        fnr,
                        "2222",
                    )
                identendringService.updateIdent(identListe)

                sykmeldingDb.getSykmeldt(fnr) shouldBeEqualTo null
                sykmeldingDb.getSykmeldt(nyttFnr)?.fornavn shouldBeEqualTo "Annet"
                sykmeldingDb.getSykmeldingIds(fnr).size shouldBeEqualTo 0
                sykmeldingDb.getSykmeldingIds(nyttFnr).size shouldBeEqualTo 1
                arbeidsforholdDb.getArbeidsforhold(fnr).size shouldBeEqualTo 0
                arbeidsforholdDb.getArbeidsforhold(nyttFnr).size shouldBeEqualTo 1
            }

            test("Kaster feil hvis sjekk mot PDL feiler") {
                coEvery { pdlPersonService.getPerson(any(), any()) } throws
                    PersonNotFoundInPdl("Fant ikke person")
                val fnr = "12345678910"
                val nyttFnr = "10987654321"
                val sykmeldingId = UUID.randomUUID().toString()
                arbeidsforholdDb.insertOrUpdate(getArbeidsforhold(fnr))
                sykmeldingDb.saveOrUpdateSykmeldt(getSykmeldt(fnr))
                sykmeldingDb.saveOrUpdate(
                    sykmeldingId,
                    getSykmelding(),
                    getSykmeldt(fnr),
                    validationResult = ValidationResult(Status.OK, emptyList())
                )
                val identListe =
                    listOf(
                        nyttFnr,
                        fnr,
                        "2222",
                    )

                assertFailsWith<PersonNotFoundInPdl> { identendringService.updateIdent(identListe) }
            }
        }
    })
