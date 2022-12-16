package no.nav.sykmeldinger.pdl.service

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.sykmeldinger.azuread.AccessTokenClient
import no.nav.sykmeldinger.pdl.client.PdlClient
import no.nav.sykmeldinger.pdl.client.model.GetPersonResponse
import no.nav.sykmeldinger.pdl.client.model.IdentInformasjon
import no.nav.sykmeldinger.pdl.client.model.Identliste
import no.nav.sykmeldinger.pdl.client.model.Navn
import no.nav.sykmeldinger.pdl.client.model.PersonResponse
import no.nav.sykmeldinger.pdl.client.model.ResponseData
import no.nav.sykmeldinger.pdl.client.model.ResponseError
import no.nav.sykmeldinger.pdl.error.InactiveIdentException
import no.nav.sykmeldinger.pdl.error.PersonNotFoundInPdl
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBeEqualTo

class PdlPersonServiceTest : FunSpec({
    val pdlClient = mockk<PdlClient>()
    val accessTokenClient = mockk<AccessTokenClient>()
    val pdlPersonService = PdlPersonService(pdlClient, accessTokenClient, "scope")

    beforeEach {
        clearMocks(pdlClient, accessTokenClient)
        coEvery { accessTokenClient.getAccessToken(any()) } returns "token"
    }

    context("PdlPersonService - getPerson") {
        test("Henter person fra PDL") {
            coEvery { pdlClient.getPerson("fnr", "token") } returns GetPersonResponse(
                data = ResponseData(
                    PersonResponse(
                        listOf(
                            Navn("Fornavn", null, "Etternavn")
                        )
                    ),
                    Identliste(
                        listOf(
                            IdentInformasjon("12345678910", true, "FOLKEREGISTERIDENT"),
                            IdentInformasjon("xxxxx", false, "AKTOERID"),
                            IdentInformasjon("10987654321", false, "FOLKEREGISTERIDENT")
                        )
                    )
                ),
                errors = null
            )

            val person = pdlPersonService.getPerson("fnr", "callid")

            person.navn shouldBeEqualTo no.nav.sykmeldinger.pdl.model.Navn("Fornavn", null, "Etternavn")
            person.fnr shouldBeEqualTo "10987654321"
        }
        test("Feiler hvis vi ikke finner navn") {
            coEvery { pdlClient.getPerson("fnr", "token") } returns GetPersonResponse(
                data = ResponseData(null, null),
                errors = listOf(ResponseError("Fant ikke person", emptyList(), null, null))
            )

            assertFailsWith<PersonNotFoundInPdl> {
                pdlPersonService.getPerson("fnr", "callid")
            }
        }
        test("Feiler hvis vi ikke finner identer") {
            coEvery { pdlClient.getPerson("fnr", "token") } returns GetPersonResponse(
                data = ResponseData(
                    PersonResponse(
                        listOf(
                            Navn("Fornavn", null, "Etternavn")
                        )
                    ),
                    Identliste(emptyList())
                ),
                errors = listOf(ResponseError("Fant ikke ident", emptyList(), null, null))
            )

            assertFailsWith<PersonNotFoundInPdl> {
                pdlPersonService.getPerson("fnr", "callid")
            }
        }
        test("Feiler hvis person mangler gyldig fnr") {
            coEvery { pdlClient.getPerson("fnr", "token") } returns GetPersonResponse(
                data = ResponseData(
                    PersonResponse(
                        listOf(
                            Navn("Fornavn", null, "Etternavn")
                        )
                    ),
                    Identliste(
                        listOf(
                            IdentInformasjon("12345678910", true, "FOLKEREGISTERIDENT"),
                            IdentInformasjon("xxxxx", false, "AKTOERID")
                        )
                    )
                ),
                errors = null
            )

            assertFailsWith<PersonNotFoundInPdl> {
                pdlPersonService.getPerson("fnr", "callid")
            }
        }
    }

    context("PdlPersonService - getNavnHvisIdentErAktiv") {
        test("Returnerer navn hvis ident er aktiv") {
            coEvery { pdlClient.getPerson("10987654321", "token") } returns GetPersonResponse(
                data = ResponseData(
                    PersonResponse(
                        listOf(
                            Navn("Fornavn", null, "Etternavn")
                        )
                    ),
                    Identliste(
                        listOf(
                            IdentInformasjon("12345678910", true, "FOLKEREGISTERIDENT"),
                            IdentInformasjon("xxxxx", false, "AKTOERID"),
                            IdentInformasjon("10987654321", false, "FOLKEREGISTERIDENT")
                        )
                    )
                ),
                errors = null
            )

            pdlPersonService.getNavnHvisIdentErAktiv("10987654321") shouldBeEqualTo no.nav.sykmeldinger.pdl.model.Navn("Fornavn", null, "Etternavn")
        }
        test("Kaster InactiveIdentException hvis ident ikke er aktiv") {
            coEvery { pdlClient.getPerson("12345678910", "token") } returns GetPersonResponse(
                data = ResponseData(
                    PersonResponse(
                        listOf(
                            Navn("Fornavn", null, "Etternavn")
                        )
                    ),
                    Identliste(
                        listOf(
                            IdentInformasjon("12345678910", true, "FOLKEREGISTERIDENT"),
                            IdentInformasjon("xxxxx", false, "AKTOERID"),
                            IdentInformasjon("10987654321", false, "FOLKEREGISTERIDENT")
                        )
                    )
                ),
                errors = null
            )

            assertFailsWith<InactiveIdentException> {
                pdlPersonService.getNavnHvisIdentErAktiv("12345678910")
            }
        }
        test("Feiler hvis vi ikke finner identer") {
            coEvery { pdlClient.getPerson("fnr", "token") } returns GetPersonResponse(
                data = ResponseData(
                    PersonResponse(
                        listOf(
                            Navn("Fornavn", null, "Etternavn")
                        )
                    ),
                    Identliste(emptyList())
                ),
                errors = listOf(ResponseError("Fant ikke ident", emptyList(), null, null))
            )

            assertFailsWith<PersonNotFoundInPdl> {
                pdlPersonService.getNavnHvisIdentErAktiv("fnr")
            }
        }
    }
})
