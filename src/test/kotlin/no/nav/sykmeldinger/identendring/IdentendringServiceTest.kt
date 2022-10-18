package no.nav.sykmeldinger.identendring

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.person.pdl.aktor.v2.Identifikator
import no.nav.person.pdl.aktor.v2.Type
import no.nav.sykmeldinger.TestDB
import no.nav.sykmeldinger.arbeidsforhold.db.ArbeidsforholdDb
import no.nav.sykmeldinger.pdl.error.PersonNotFoundInPdl
import no.nav.sykmeldinger.pdl.model.Navn
import no.nav.sykmeldinger.pdl.service.PdlPersonService
import no.nav.sykmeldinger.sykmelding.db.SykmeldingDb
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBeEqualTo
import java.util.UUID

object IdentendringServiceTest : FunSpec({
    val testDb = TestDB.database
    val arbeidsforholdDb = ArbeidsforholdDb(testDb)
    val sykmeldingDb = SykmeldingDb(testDb)
    val pdlPersonService = mockk<PdlPersonService>()
    val identendringService = IdentendringService(arbeidsforholdDb, sykmeldingDb, pdlPersonService)

    beforeTest {
        TestDB.clearAllData()
        clearMocks(
            pdlPersonService
        )
        coEvery { pdlPersonService.getNavnHvisIdentErAktiv(any()) } returns Navn("Fornavn", null, "Etternavn")
    }

    context("IdentendringService") {
        test("Oppdaterer sykmeldt, sykmelding og arbeidsforhold ved nytt fnr") {
            val fnr = "12345678910"
            val nyttFnr = "10987654321"
            val sykmeldingId = UUID.randomUUID().toString()
            arbeidsforholdDb.insertOrUpdate(getArbeidsforhold(fnr))
            sykmeldingDb.saveOrUpdateSykmeldt(getSykmeldt(fnr))
            sykmeldingDb.saveOrUpdate(sykmeldingId, getSykmelding(), getSykmeldt(fnr))
            val identListe = listOf(
                Identifikator(nyttFnr, Type.FOLKEREGISTERIDENT, true),
                Identifikator(fnr, Type.FOLKEREGISTERIDENT, false),
                Identifikator("2222", Type.AKTORID, false)
            )

            identendringService.oppdaterIdent(identListe)

            sykmeldingDb.getSykmeldt(fnr) shouldBeEqualTo null
            sykmeldingDb.getSykmeldt(nyttFnr)?.fornavn shouldBeEqualTo "Fornavn"
            sykmeldingDb.getSykmeldingIds(fnr).size shouldBeEqualTo 0
            sykmeldingDb.getSykmeldingIds(nyttFnr).size shouldBeEqualTo 1
            arbeidsforholdDb.getArbeidsforhold(fnr).size shouldBeEqualTo 0
            arbeidsforholdDb.getArbeidsforhold(nyttFnr).size shouldBeEqualTo 1
        }
        test("Oppdaterer ingenting hvis vi ikke har data på gamle fnr") {
            val fnr = "12345678910"
            val sykmeldingId = UUID.randomUUID().toString()
            arbeidsforholdDb.insertOrUpdate(getArbeidsforhold(fnr))
            sykmeldingDb.saveOrUpdateSykmeldt(getSykmeldt(fnr))
            sykmeldingDb.saveOrUpdate(sykmeldingId, getSykmelding(), getSykmeldt(fnr))
            val identListeMedAnnetFnr = listOf(
                Identifikator("1234", Type.FOLKEREGISTERIDENT, true),
                Identifikator("1111", Type.FOLKEREGISTERIDENT, false),
                Identifikator("2222", Type.AKTORID, false)
            )

            identendringService.oppdaterIdent(identListeMedAnnetFnr)

            coVerify(exactly = 0) { pdlPersonService.getNavnHvisIdentErAktiv(any()) }
        }
        test("Oppdaterer ingenting hvis endringen ikke gjelder fnr") {
            val identListeUtenEndringIFnr = listOf(
                Identifikator("1234", Type.FOLKEREGISTERIDENT, true),
                Identifikator("1111", Type.AKTORID, true),
                Identifikator("2222", Type.AKTORID, false)
            )

            identendringService.oppdaterIdent(identListeUtenEndringIFnr)

            coVerify(exactly = 0) { pdlPersonService.getNavnHvisIdentErAktiv(any()) }
        }
        test("Kaster feil hvis sjekk mot PDL feiler") {
            coEvery { pdlPersonService.getNavnHvisIdentErAktiv(any()) } throws PersonNotFoundInPdl("Fant ikke person")
            val fnr = "12345678910"
            val nyttFnr = "10987654321"
            val sykmeldingId = UUID.randomUUID().toString()
            arbeidsforholdDb.insertOrUpdate(getArbeidsforhold(fnr))
            sykmeldingDb.saveOrUpdateSykmeldt(getSykmeldt(fnr))
            sykmeldingDb.saveOrUpdate(sykmeldingId, getSykmelding(), getSykmeldt(fnr))
            val identListe = listOf(
                Identifikator(nyttFnr, Type.FOLKEREGISTERIDENT, true),
                Identifikator(fnr, Type.FOLKEREGISTERIDENT, false),
                Identifikator("2222", Type.AKTORID, false)
            )

            assertFailsWith<PersonNotFoundInPdl> {
                identendringService.oppdaterIdent(identListe)
            }
        }
        test("Kaster feil hvis oppdatering ikke inneholder nytt fnr") {
            val identListeUtenNyttFnr = listOf(
                Identifikator("1234", Type.FOLKEREGISTERIDENT, false),
                Identifikator("1111", Type.FOLKEREGISTERIDENT, false),
                Identifikator("2222", Type.AKTORID, false)
            )

            assertFailsWith<IllegalStateException> {
                identendringService.oppdaterIdent(identListeUtenNyttFnr)
            }
        }
    }
})
