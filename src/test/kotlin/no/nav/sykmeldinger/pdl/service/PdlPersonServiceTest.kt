package no.nav.sykmeldinger.pdl.service

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDate
import java.util.*
import no.nav.sykmeldinger.TestDB
import no.nav.sykmeldinger.azuread.AccessTokenClient
import no.nav.sykmeldinger.identendring.getSykmeldt
import no.nav.sykmeldinger.pdl.client.PdlClient
import no.nav.sykmeldinger.pdl.client.model.*
import no.nav.sykmeldinger.pdl.error.InactiveIdentException
import no.nav.sykmeldinger.pdl.error.PersonNotFoundInPdl
import no.nav.sykmeldinger.pdl.model.Navn
import no.nav.sykmeldinger.sykmelding.db.SykmeldingDb
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBeEqualTo

class PdlPersonServiceTest :
    FunSpec({
        val pdlClient = mockk<PdlClient>()
        val accessTokenClient = mockk<AccessTokenClient>()
        val pdlPersonService = PdlPersonService(pdlClient, accessTokenClient, "scope")
        val testDb = TestDB.database
        val sykmeldingDb = SykmeldingDb(testDb)

        beforeEach {
            clearMocks(pdlClient, accessTokenClient)
            coEvery { accessTokenClient.getAccessToken(any()) } returns "token"
        }

        context("PdlPersonService - getPerson") {
            test("Henter person fra PDL") {
                coEvery { pdlClient.getPerson("fnr", "token") } returns
                    GetPersonResponse(
                        data =
                            ResponseData(
                                PersonResponse(
                                    listOf(
                                        NavnResponse("Fornavn", null, "Etternavn"),
                                    ),
                                    emptyList(),
                                ),
                                Identliste(
                                    listOf(
                                        IdentInformasjon("12345678910", true, "FOLKEREGISTERIDENT"),
                                        IdentInformasjon("xxxxx", false, "AKTOERID"),
                                        IdentInformasjon(
                                            "10987654321",
                                            false,
                                            "FOLKEREGISTERIDENT"
                                        ),
                                    ),
                                ),
                            ),
                        errors = null,
                    )

                val person = pdlPersonService.getPerson("fnr", "callid")

                person.navn shouldBeEqualTo
                    no.nav.sykmeldinger.pdl.model.Navn("Fornavn", null, "Etternavn")
                person.fnr shouldBeEqualTo "10987654321"
            }
            test("Feiler hvis vi ikke finner navn") {
                coEvery { pdlClient.getPerson("fnr", "token") } returns
                    GetPersonResponse(
                        data = ResponseData(null, null),
                        errors = listOf(ResponseError("Fant ikke person", emptyList(), null, null)),
                    )

                assertFailsWith<PersonNotFoundInPdl> { pdlPersonService.getPerson("fnr", "callid") }
            }
            test("Feiler hvis vi ikke finner identer") {
                coEvery { pdlClient.getPerson("fnr", "token") } returns
                    GetPersonResponse(
                        data =
                            ResponseData(
                                PersonResponse(
                                    listOf(
                                        NavnResponse("Fornavn", null, "Etternavn"),
                                    ),
                                    emptyList(),
                                ),
                                Identliste(emptyList()),
                            ),
                        errors = listOf(ResponseError("Fant ikke ident", emptyList(), null, null)),
                    )

                assertFailsWith<PersonNotFoundInPdl> { pdlPersonService.getPerson("fnr", "callid") }
            }
            test("Feiler hvis person mangler gyldig fnr") {
                coEvery { pdlClient.getPerson("fnr", "token") } returns
                    GetPersonResponse(
                        data =
                            ResponseData(
                                PersonResponse(
                                    listOf(
                                        NavnResponse("Fornavn", null, "Etternavn"),
                                    ),
                                    emptyList(),
                                ),
                                Identliste(
                                    listOf(
                                        IdentInformasjon("12345678910", true, "FOLKEREGISTERIDENT"),
                                        IdentInformasjon("xxxxx", false, "AKTOERID"),
                                    ),
                                ),
                            ),
                        errors = null,
                    )

                assertFailsWith<PersonNotFoundInPdl> { pdlPersonService.getPerson("fnr", "callid") }
            }
        }

        context("PdlPersonService - getNavnHvisIdentErAktiv") {
            test("Returnerer navn hvis ident er aktiv") {
                coEvery { pdlClient.getPerson("10987654321", "token") } returns
                    GetPersonResponse(
                        data =
                            ResponseData(
                                PersonResponse(
                                    listOf(
                                        NavnResponse("Fornavn", null, "Etternavn"),
                                    ),
                                    emptyList(),
                                ),
                                Identliste(
                                    listOf(
                                        IdentInformasjon("12345678910", true, "FOLKEREGISTERIDENT"),
                                        IdentInformasjon("xxxxx", false, "AKTOERID"),
                                        IdentInformasjon(
                                            "10987654321",
                                            false,
                                            "FOLKEREGISTERIDENT"
                                        ),
                                    ),
                                ),
                            ),
                        errors = null,
                    )

                pdlPersonService.getNavnHvisIdentErAktiv("10987654321") shouldBeEqualTo
                    no.nav.sykmeldinger.pdl.model.Navn("Fornavn", null, "Etternavn")
            }
            test("Kaster InactiveIdentException hvis ident ikke er aktiv") {
                coEvery { pdlClient.getPerson("12345678910", "token") } returns
                    GetPersonResponse(
                        data =
                            ResponseData(
                                PersonResponse(
                                    listOf(
                                        NavnResponse("Fornavn", null, "Etternavn"),
                                    ),
                                    emptyList()
                                ),
                                Identliste(
                                    listOf(
                                        IdentInformasjon("12345678910", true, "FOLKEREGISTERIDENT"),
                                        IdentInformasjon("xxxxx", false, "AKTOERID"),
                                        IdentInformasjon(
                                            "10987654321",
                                            false,
                                            "FOLKEREGISTERIDENT"
                                        ),
                                    ),
                                ),
                            ),
                        errors = null,
                    )

                assertFailsWith<InactiveIdentException> {
                    pdlPersonService.getNavnHvisIdentErAktiv("12345678910")
                }
            }
            test("Feiler hvis vi ikke finner identer") {
                coEvery { pdlClient.getPerson("fnr", "token") } returns
                    GetPersonResponse(
                        data =
                            ResponseData(
                                PersonResponse(
                                    listOf(
                                        NavnResponse("Fornavn", null, "Etternavn"),
                                    ),
                                    emptyList()
                                ),
                                Identliste(emptyList()),
                            ),
                        errors = listOf(ResponseError("Fant ikke ident", emptyList(), null, null)),
                    )

                assertFailsWith<PersonNotFoundInPdl> {
                    pdlPersonService.getNavnHvisIdentErAktiv("fnr")
                }
            }
        }
        context("PDl get person med foedselsnummer") {
            test("happy case - henter fødselsnummer") {
                coEvery { pdlClient.getPerson("10987654321", "token") } returns
                    GetPersonResponse(
                        data =
                            ResponseData(
                                PersonResponse(
                                    listOf(
                                        NavnResponse("Fornavn", null, "Etternavn"),
                                    ),
                                    listOf(Foedsel("1997-02-25")),
                                ),
                                Identliste(
                                    listOf(
                                        IdentInformasjon("12345678910", true, "FOLKEREGISTERIDENT"),
                                        IdentInformasjon("xxxxx", false, "AKTOERID"),
                                        IdentInformasjon(
                                            "10987654321",
                                            false,
                                            "FOLKEREGISTERIDENT"
                                        ),
                                    ),
                                ),
                            ),
                        errors = null,
                    )
                val pdlPerson = pdlPersonService.getPerson("10987654321", "sykmeldingId")
                pdlPerson.foedselsdato shouldBeEqualTo LocalDate.parse("1997-02-25")
            }
            test("Person med fødselsnummerliste lik null i pdl") {
                coEvery { pdlClient.getPerson("12345678910", "token") } returns
                    GetPersonResponse(
                        data =
                            ResponseData(
                                PersonResponse(
                                    listOf(
                                        NavnResponse("Fornavn", null, "Etternavn"),
                                    ),
                                    null
                                ),
                                Identliste(
                                    listOf(
                                        IdentInformasjon("12345678910", true, "FOLKEREGISTERIDENT"),
                                        IdentInformasjon("xxxxx", false, "AKTOERID"),
                                        IdentInformasjon(
                                            "10987654321",
                                            false,
                                            "FOLKEREGISTERIDENT"
                                        ),
                                    ),
                                ),
                            ),
                        errors = null,
                    )
                val pdlPerson = pdlPersonService.getPerson("12345678910", "sykmeldingId")
                pdlPerson.foedselsdato shouldBeEqualTo null
            }
            test("Person med tom fødselsnummerliste i pdl") {
                coEvery { pdlClient.getPerson("12345678910", "token") } returns
                    GetPersonResponse(
                        data =
                            ResponseData(
                                PersonResponse(
                                    listOf(
                                        NavnResponse("Fornavn", null, "Etternavn"),
                                    ),
                                    emptyList()
                                ),
                                Identliste(
                                    listOf(
                                        IdentInformasjon("12345678910", true, "FOLKEREGISTERIDENT"),
                                        IdentInformasjon("xxxxx", false, "AKTOERID"),
                                        IdentInformasjon(
                                            "10987654321",
                                            false,
                                            "FOLKEREGISTERIDENT"
                                        ),
                                    ),
                                ),
                            ),
                        errors = null,
                    )
                val pdlPerson = pdlPersonService.getPerson("12345678910", "sykmeldingId")
                pdlPerson.foedselsdato shouldBeEqualTo null
            }
            test("Person uten fødselsdato i pdl, lagrer i databasen") {
                sykmeldingDb.saveOrUpdateSykmeldt(getSykmeldt("10987654321"))
                sykmeldingDb.getSykmeldt("10987654321")?.foedselsdato shouldBeEqualTo null
            }
            test("Person med fødselsdato i pdl, lagrer i databasen") {
                sykmeldingDb.saveOrUpdateSykmeldt(
                    getSykmeldt("10987654321", LocalDate.parse("1997-02-25"))
                )
                sykmeldingDb.getSykmeldt("10987654321")?.foedselsdato shouldBeEqualTo
                    LocalDate.parse("1997-02-25")
            }
        }
    })
