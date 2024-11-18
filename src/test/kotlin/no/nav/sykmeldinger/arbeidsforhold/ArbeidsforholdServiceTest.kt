package no.nav.sykmeldinger.arbeidsforhold

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDate
import no.nav.sykmeldinger.TestDB
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.client.ArbeidsforholdClient
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.AaregArbeidsforhold
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.Ansettelsesperiode
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.ArbeidsforholdType
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.Arbeidssted
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.ArbeidsstedType
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.Ident
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.IdentType
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.Opplysningspliktig
import no.nav.sykmeldinger.arbeidsforhold.client.organisasjon.client.OrganisasjonsinfoClient
import no.nav.sykmeldinger.arbeidsforhold.db.ArbeidsforholdDb
import no.nav.sykmeldinger.arbeidsforhold.model.Arbeidsforhold
import org.amshove.kluent.shouldBeEqualTo

object ArbeidsforholdServiceTest :
    FunSpec({
        val testDb = TestDB.database
        val arbeidsforholdClient = mockk<ArbeidsforholdClient>()
        val organisasjonsinfoClient = mockk<OrganisasjonsinfoClient>()
        val arbeidsforholdDb = ArbeidsforholdDb(testDb)
        val arbeidsforholdService =
            ArbeidsforholdService(arbeidsforholdClient, organisasjonsinfoClient, arbeidsforholdDb)

        beforeTest {
            TestDB.clearAllData()
            clearMocks(
                arbeidsforholdClient,
                organisasjonsinfoClient,
            )
            coEvery { organisasjonsinfoClient.getOrganisasjonsnavn(any()) } returns
                getOrganisasjonsinfo()
        }
        context("ArbeidsforholderService - getArbeidsforhold") {
            test("getArbeidsforhold returnerer liste med arbeidsforhold") {
                coEvery { arbeidsforholdClient.getArbeidsforhold(any()) } returns
                    listOf(
                        AaregArbeidsforhold(
                            1,
                            Arbeidssted(
                                ArbeidsstedType.Underenhet,
                                listOf(Ident(IdentType.ORGANISASJONSNUMMER, "123456789", true))
                            ),
                            Opplysningspliktig(
                                listOf(Ident(IdentType.ORGANISASJONSNUMMER, "987654321", true))
                            ),
                            Ansettelsesperiode(
                                startdato = LocalDate.now().minusYears(3),
                                sluttdato = null
                            ),
                            type =
                                ArbeidsforholdType(
                                    kode = "ordinaertArbeidsforhold",
                                    beskrivelse = ""
                                ),
                        ),
                        AaregArbeidsforhold(
                            2,
                            Arbeidssted(
                                ArbeidsstedType.Underenhet,
                                listOf(Ident(IdentType.ORGANISASJONSNUMMER, "88888888", true))
                            ),
                            Opplysningspliktig(
                                listOf(Ident(IdentType.ORGANISASJONSNUMMER, "999999999", true))
                            ),
                            Ansettelsesperiode(
                                startdato = LocalDate.now().minusMonths(6),
                                sluttdato = LocalDate.now().minusWeeks(3),
                            ),
                            type =
                                ArbeidsforholdType(
                                    kode = "ordinaertArbeidsforhold",
                                    beskrivelse = ""
                                ),
                        ),
                        AaregArbeidsforhold(
                            3,
                            Arbeidssted(
                                ArbeidsstedType.Underenhet,
                                listOf(Ident(IdentType.ORGANISASJONSNUMMER, "88888888", true))
                            ),
                            Opplysningspliktig(
                                listOf(Ident(IdentType.ORGANISASJONSNUMMER, "999999999", true))
                            ),
                            Ansettelsesperiode(
                                startdato = LocalDate.now().minusWeeks(2),
                                sluttdato = LocalDate.now().plusMonths(3),
                            ),
                            type =
                                ArbeidsforholdType(
                                    kode = "ordinaertArbeidsforhold",
                                    beskrivelse = ""
                                ),
                        ),
                    )
                coEvery { organisasjonsinfoClient.getOrganisasjonsnavn(any()) } returns
                    getOrganisasjonsinfo() andThen
                    getOrganisasjonsinfo(
                        navn = "Navn 2",
                    ) andThen
                    getOrganisasjonsinfo(navn = "Navn 2")

                val arbeidsforhold = arbeidsforholdService.getArbeidsforhold("12345678901")

                arbeidsforhold.size shouldBeEqualTo 3
                arbeidsforhold[0].id shouldBeEqualTo 1
                arbeidsforhold[0].fnr shouldBeEqualTo "12345678901"
                arbeidsforhold[0].orgnummer shouldBeEqualTo "123456789"
                arbeidsforhold[0].juridiskOrgnummer shouldBeEqualTo "987654321"
                arbeidsforhold[0].orgNavn shouldBeEqualTo "Navn 1"
                arbeidsforhold[0].fom shouldBeEqualTo LocalDate.now().minusYears(3)
                arbeidsforhold[0].tom shouldBeEqualTo null

                arbeidsforhold[1].id shouldBeEqualTo 3
                arbeidsforhold[1].fnr shouldBeEqualTo "12345678901"
                arbeidsforhold[1].orgnummer shouldBeEqualTo "88888888"
                arbeidsforhold[1].juridiskOrgnummer shouldBeEqualTo "999999999"
                arbeidsforhold[1].orgNavn shouldBeEqualTo "Navn 2"
                arbeidsforhold[1].fom shouldBeEqualTo LocalDate.now().minusWeeks(2)
                arbeidsforhold[1].tom shouldBeEqualTo LocalDate.now().plusMonths(3)

                arbeidsforhold[2].id shouldBeEqualTo 2
                arbeidsforhold[2].fnr shouldBeEqualTo "12345678901"
                arbeidsforhold[2].orgnummer shouldBeEqualTo "88888888"
                arbeidsforhold[2].juridiskOrgnummer shouldBeEqualTo "999999999"
                arbeidsforhold[2].orgNavn shouldBeEqualTo "Navn 2"
                arbeidsforhold[2].fom shouldBeEqualTo LocalDate.now().minusMonths(6)
                arbeidsforhold[2].tom shouldBeEqualTo LocalDate.now().minusWeeks(3)
            }
            test("getArbeidsforhold returnerer tom liste hvis bruker ikke har arbeidsforhold") {
                coEvery { arbeidsforholdClient.getArbeidsforhold(any()) } returns emptyList()

                val arbeidsforhold = arbeidsforholdService.getArbeidsforhold("12345678901")

                arbeidsforhold.size shouldBeEqualTo 0
            }
            test("getArbeidsforhold filtrerer bort arbeidsforhold med annen type enn underenhet") {
                coEvery { arbeidsforholdClient.getArbeidsforhold(any()) } returns
                    listOf(
                        AaregArbeidsforhold(
                            1,
                            Arbeidssted(
                                ArbeidsstedType.Person,
                                listOf(Ident(IdentType.FOLKEREGISTERIDENT, "fnr", true))
                            ),
                            Opplysningspliktig(
                                listOf(Ident(IdentType.ORGANISASJONSNUMMER, "987654321", true))
                            ),
                            Ansettelsesperiode(
                                startdato = LocalDate.now().minusYears(3),
                                sluttdato = null
                            ),
                            type =
                                ArbeidsforholdType(
                                    kode = "ordinaertArbeidsforhold",
                                    beskrivelse = ""
                                ),
                        ),
                        AaregArbeidsforhold(
                            2,
                            Arbeidssted(
                                ArbeidsstedType.Underenhet,
                                listOf(Ident(IdentType.ORGANISASJONSNUMMER, "88888888", true))
                            ),
                            Opplysningspliktig(
                                listOf(Ident(IdentType.ORGANISASJONSNUMMER, "999999999", true))
                            ),
                            Ansettelsesperiode(
                                startdato = LocalDate.now().minusMonths(6),
                                sluttdato = LocalDate.now().minusWeeks(3),
                            ),
                            type =
                                ArbeidsforholdType(
                                    kode = "ordinaertArbeidsforhold",
                                    beskrivelse = ""
                                ),
                        ),
                    )

                val arbeidsforhold = arbeidsforholdService.getArbeidsforhold("12345678901")

                arbeidsforhold.size shouldBeEqualTo 1
                arbeidsforhold[0].id shouldBeEqualTo 2
                arbeidsforhold[0].fnr shouldBeEqualTo "12345678901"
                arbeidsforhold[0].orgnummer shouldBeEqualTo "88888888"
                arbeidsforhold[0].juridiskOrgnummer shouldBeEqualTo "999999999"
                arbeidsforhold[0].orgNavn shouldBeEqualTo "Navn 1"
                arbeidsforhold[0].fom shouldBeEqualTo LocalDate.now().minusMonths(6)
                arbeidsforhold[0].tom shouldBeEqualTo LocalDate.now().minusWeeks(3)
            }
            test(
                "getArbeidsforhold filtrerer bort arbeidsforhold med sluttdato for mer enn 4 måneder siden"
            ) {
                coEvery { arbeidsforholdClient.getArbeidsforhold(any()) } returns
                    listOf(
                        AaregArbeidsforhold(
                            1,
                            Arbeidssted(
                                ArbeidsstedType.Underenhet,
                                listOf(Ident(IdentType.ORGANISASJONSNUMMER, "123456789", true))
                            ),
                            Opplysningspliktig(
                                listOf(Ident(IdentType.ORGANISASJONSNUMMER, "987654321", true))
                            ),
                            Ansettelsesperiode(
                                startdato = LocalDate.now().minusMonths(6),
                                sluttdato = LocalDate.now().minusWeeks(3),
                            ),
                            type =
                                ArbeidsforholdType(
                                    kode = "ordinaertArbeidsforhold",
                                    beskrivelse = ""
                                ),
                        ),
                        AaregArbeidsforhold(
                            2,
                            Arbeidssted(
                                ArbeidsstedType.Underenhet,
                                listOf(Ident(IdentType.ORGANISASJONSNUMMER, "88888888", true))
                            ),
                            Opplysningspliktig(
                                listOf(Ident(IdentType.ORGANISASJONSNUMMER, "999999999", true))
                            ),
                            Ansettelsesperiode(
                                startdato = LocalDate.now().minusYears(6),
                                sluttdato = LocalDate.now().minusMonths(5),
                            ),
                            type =
                                ArbeidsforholdType(
                                    kode = "ordinaertArbeidsforhold",
                                    beskrivelse = ""
                                ),
                        ),
                    )

                val arbeidsforhold = arbeidsforholdService.getArbeidsforhold("12345678901")
                arbeidsforhold.size shouldBeEqualTo 1
                arbeidsforhold[0].id shouldBeEqualTo 1
                arbeidsforhold[0].fnr shouldBeEqualTo "12345678901"
                arbeidsforhold[0].orgnummer shouldBeEqualTo "123456789"
                arbeidsforhold[0].juridiskOrgnummer shouldBeEqualTo "987654321"
                arbeidsforhold[0].orgNavn shouldBeEqualTo "Navn 1"
                arbeidsforhold[0].fom shouldBeEqualTo LocalDate.now().minusMonths(6)
                arbeidsforhold[0].tom shouldBeEqualTo LocalDate.now().minusWeeks(3)
            }
        }
        context("ArbeidsforholderService - insertOrUpdate") {
            test("Lagrer nytt arbeidsforhold") {
                val arbeidsforhold =
                    Arbeidsforhold(
                        id = 1,
                        fnr = "12345678910",
                        orgnummer = "888888888",
                        juridiskOrgnummer = "999999999",
                        orgNavn = "Bedriften AS",
                        fom = LocalDate.of(2020, 5, 1),
                        tom = null,
                    )

                arbeidsforholdService.insertOrUpdate(arbeidsforhold)

                val arbeidsforholdFraDb = arbeidsforholdDb.getArbeidsforhold("12345678910")
                arbeidsforholdFraDb.size shouldBeEqualTo 1
                arbeidsforholdFraDb[0].id shouldBeEqualTo 1
                arbeidsforholdFraDb[0].fnr shouldBeEqualTo "12345678910"
                arbeidsforholdFraDb[0].orgnummer shouldBeEqualTo "888888888"
                arbeidsforholdFraDb[0].juridiskOrgnummer shouldBeEqualTo "999999999"
                arbeidsforholdFraDb[0].orgNavn shouldBeEqualTo "Bedriften AS"
                arbeidsforholdFraDb[0].fom shouldBeEqualTo LocalDate.of(2020, 5, 1)
                arbeidsforholdFraDb[0].tom shouldBeEqualTo null
            }
            test("Oppdaterer arbeidsforhold") {
                val arbeidsforhold =
                    Arbeidsforhold(
                        id = 1,
                        fnr = "12345678910",
                        orgnummer = "888888888",
                        juridiskOrgnummer = "999999999",
                        orgNavn = "Bedriften AS",
                        fom = LocalDate.of(2020, 5, 1),
                        tom = null,
                    )
                arbeidsforholdService.insertOrUpdate(arbeidsforhold)

                arbeidsforholdService.insertOrUpdate(arbeidsforhold.copy(tom = LocalDate.now()))

                val arbeidsforholdFraDb = arbeidsforholdDb.getArbeidsforhold("12345678910")
                arbeidsforholdFraDb.size shouldBeEqualTo 1
                arbeidsforholdFraDb[0].id shouldBeEqualTo 1
                arbeidsforholdFraDb[0].fnr shouldBeEqualTo "12345678910"
                arbeidsforholdFraDb[0].orgnummer shouldBeEqualTo "888888888"
                arbeidsforholdFraDb[0].juridiskOrgnummer shouldBeEqualTo "999999999"
                arbeidsforholdFraDb[0].orgNavn shouldBeEqualTo "Bedriften AS"
                arbeidsforholdFraDb[0].fom shouldBeEqualTo LocalDate.of(2020, 5, 1)
                arbeidsforholdFraDb[0].tom shouldBeEqualTo LocalDate.now()
            }
        }

        context("ArbeidsforholderService - deleteArbeidsforhold") {
            test("Sletter arbeidsforhold") {
                val arbeidsforhold =
                    Arbeidsforhold(
                        id = 5,
                        fnr = "12345678910",
                        orgnummer = "888888888",
                        juridiskOrgnummer = "999999999",
                        orgNavn = "Bedriften AS",
                        fom = LocalDate.of(2020, 5, 1),
                        tom = null,
                    )
                arbeidsforholdService.insertOrUpdate(arbeidsforhold)

                arbeidsforholdService.deleteArbeidsforhold(5)

                val arbeidsforholdFraDb = arbeidsforholdDb.getArbeidsforhold("12345678910")
                arbeidsforholdFraDb.size shouldBeEqualTo 0
            }
        }
    })
