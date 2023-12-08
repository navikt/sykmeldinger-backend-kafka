package no.nav.sykmeldinger.pdl.service

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.OffsetDateTime
import java.util.UUID
import no.nav.sykmeldinger.TestDB
import no.nav.sykmeldinger.identendring.IdentendringService
import no.nav.sykmeldinger.narmesteleder.db.NarmestelederDb
import no.nav.sykmeldinger.narmesteleder.db.NarmestelederDbModel
import no.nav.sykmeldinger.pdl.Endringstype
import no.nav.sykmeldinger.pdl.NavnDataClass
import no.nav.sykmeldinger.pdl.PersonhendelseDataClass
import no.nav.sykmeldinger.pdl.model.Navn
import no.nav.sykmeldinger.pdl.model.PdlPerson
import org.amshove.kluent.shouldBeEqualTo

class PersonhendelseServiceTest :
    FunSpec({
        val identendringService = mockk<IdentendringService>(relaxed = true)
        val testDb = TestDB.database
        val narmesteLederDb = NarmestelederDb(testDb)
        val pdlPersonService = mockk<PdlPersonService>(relaxed = true)
        val personhendelseService =
            PersonhendelseService(
                identendringService = identendringService,
                narmestelederDb = narmesteLederDb,
                pdlPersonService = pdlPersonService
            )
        beforeEach {
            clearMocks(pdlPersonService, identendringService)
            coEvery { pdlPersonService.getPerson(any(), any()) } returns
                PdlPerson(Navn("Fornavn", "Mellomnavn", "Etternavn"), "12345678910", emptyList())
            TestDB.clearAllData()
        }
        context("Identendring") {
            val personhendelse =
                PersonhendelseDataClass(
                    "hendelseId",
                    listOf("12345678910", "annetfnr"),
                    "PDL",
                    OffsetDateTime.now().toInstant(),
                    "FOLKEREGISTERIDENTIFIKATOR_V1",
                    Endringstype.OPPRETTET,
                    "tidligereId",
                    null,
                )

            test("Oppdaterer ident ved riktig opplysningstype og endringstype") {
                personhendelseService.handlePersonhendelse(listOf(personhendelse))

                coVerify(exactly = 1) {
                    identendringService.updateIdent(listOf("12345678910", "annetfnr"))
                }
            }
            test("Oppdater ident ved korrigering") {
                personhendelseService.handlePersonhendelse(
                    listOf(personhendelse.copy(endringstype = Endringstype.KORRIGERT))
                )

                coVerify(exactly = 1) {
                    identendringService.updateIdent(listOf("12345678910", "annetfnr"))
                }
            }
            test("Oppdaterer ikke ident ved annen opplysningstype") {
                personhendelseService.handlePersonhendelse(
                    listOf(personhendelse.copy(opplysningstype = "annen"))
                )

                coVerify(exactly = 0) { identendringService.updateIdent(any()) }
            }

            test("Oppdaterer ikke ident ved endringstype ANNULERT") {
                personhendelseService.handlePersonhendelse(
                    listOf(personhendelse.copy(endringstype = Endringstype.ANNULLERT))
                )

                coVerify(exactly = 0) { identendringService.updateIdent(any()) }
            }

            test("Oppdaterer ikke ident ved endringstype OPPHOERT") {
                personhendelseService.handlePersonhendelse(
                    listOf(personhendelse.copy(endringstype = Endringstype.OPPHOERT))
                )

                coVerify(exactly = 0) { identendringService.updateIdent(any()) }
            }

            test("Oppdaterer ident en gang vved flere eventer") {
                personhendelseService.handlePersonhendelse(
                    listOf(
                        personhendelse,
                        personhendelse.copy(
                            endringstype = Endringstype.KORRIGERT,
                            hendelseId = "hendelseId2"
                        )
                    )
                )

                coVerify(exactly = 1) {
                    identendringService.updateIdent(listOf("12345678910", "annetfnr"))
                }
            }
        }
        context("Navnendring") {
            test(
                "Oppdaterer navn hvis personhendelse er relatert til navn og lederen finnes i db"
            ) {
                val nlId = UUID.randomUUID().toString()
                narmesteLederDb.insertOrUpdate(
                    NarmestelederDbModel(
                        nlId,
                        "8888",
                        "brukerFnr",
                        "12345678910",
                        "Leder Ledersen",
                        OffsetDateTime.now()
                    )
                )
                val personhendelse =
                    getPersonhendelse(
                        "12345678910",
                        NavnDataClass(
                            "Fornavn",
                            null,
                            "Etternavn",
                            "Fornavn Etternavn",
                            null,
                        )
                    )

                personhendelseService.handlePersonhendelse(listOf(personhendelse))

                coVerify(exactly = 1) { pdlPersonService.getPerson("12345678910", any()) }
                TestDB.getNarmesteleder(nlId)?.navn shouldBeEqualTo "Fornavn Mellomnavn Etternavn"
            }
            test(
                "Oppdaterer ikke navn hvis personhendelse er relatert til navn men lederen ikke finnes i db"
            ) {
                val personhendelse =
                    getPersonhendelse(
                        "12345678910",
                        NavnDataClass(
                            "Fornavn",
                            null,
                            "Etternavn",
                            "Fornavn Etternavn",
                            null,
                        )
                    )

                personhendelseService.handlePersonhendelse(listOf(personhendelse))

                coVerify(exactly = 0) { pdlPersonService.getPerson(any(), any()) }
            }
            test("Oppdaterer ikke navn hvis personhendelse ikke er relatert til navn") {
                val personhendelse = getPersonhendelse("12345678910", null)

                personhendelseService.handlePersonhendelse(listOf(personhendelse))

                coVerify(exactly = 0) { pdlPersonService.getPerson(any(), any()) }
            }
        }
    })

fun getPersonhendelse(fnr: String, navn: NavnDataClass?): PersonhendelseDataClass {
    return PersonhendelseDataClass(
        "hendelseId",
        listOf(fnr, "annetfnr"),
        "PDL",
        OffsetDateTime.now().toInstant(),
        "type",
        Endringstype.OPPRETTET,
        "tidligereId",
        navn,
    )
}
