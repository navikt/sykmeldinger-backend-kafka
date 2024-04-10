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
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.Arbeidssted
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.ArbeidsstedType
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.Ident
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.IdentType
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.Opplysningspliktig
import no.nav.sykmeldinger.arbeidsforhold.client.organisasjon.client.OrganisasjonsinfoClient
import no.nav.sykmeldinger.arbeidsforhold.db.ArbeidsforholdDb
import no.nav.sykmeldinger.arbeidsforhold.model.Arbeidsforhold
import no.nav.sykmeldinger.utils.TestHelper.Companion.desember
import no.nav.sykmeldinger.utils.TestHelper.Companion.februar
import no.nav.sykmeldinger.utils.TestHelper.Companion.januar
import no.nav.sykmeldinger.utils.TestHelper.Companion.juli
import no.nav.sykmeldinger.utils.TestHelper.Companion.juni
import no.nav.sykmeldinger.utils.TestHelper.Companion.mars
import org.amshove.kluent.shouldBeEqualTo

class ArbeidsforholdServiceTest :
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
            test(
                "getArbeidsforhold returnerer liste med gyldige arbeidsforhold etter sykmelding startdato"
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
                            Ansettelsesperiode(startdato = 1.januar(2020), sluttdato = null),
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
                                startdato = 1.juni(2022),
                                sluttdato = 7.desember(2022),
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
                                startdato = 14.desember(2022),
                                sluttdato = 1.mars(2023),
                            ),
                        ),
                    )
                coEvery { organisasjonsinfoClient.getOrganisasjonsnavn(any()) } returns
                    getOrganisasjonsinfo() andThen
                    getOrganisasjonsinfo(
                        navn = "Navn 2",
                    ) andThen
                    getOrganisasjonsinfo(navn = "Navn 2")

                val sykmeldingStartDato = 14.desember(2022)
                val sykmeldingSluttDato = 1.mars(2023)
                val arbeidsforhold =
                    arbeidsforholdService.getArbeidsforhold(
                        "12345678901",
                        sykmeldingStartDato,
                        sykmeldingSluttDato
                    )

                arbeidsforhold.size shouldBeEqualTo 2
                arbeidsforhold[0].id shouldBeEqualTo 1
                arbeidsforhold[0].fnr shouldBeEqualTo "12345678901"
                arbeidsforhold[0].orgnummer shouldBeEqualTo "123456789"
                arbeidsforhold[0].juridiskOrgnummer shouldBeEqualTo "987654321"
                arbeidsforhold[0].orgNavn shouldBeEqualTo "Navn 1"
                arbeidsforhold[0].fom shouldBeEqualTo 1.januar(2020)
                arbeidsforhold[0].tom shouldBeEqualTo null

                arbeidsforhold[1].id shouldBeEqualTo 3
                arbeidsforhold[1].fnr shouldBeEqualTo "12345678901"
                arbeidsforhold[1].orgnummer shouldBeEqualTo "88888888"
                arbeidsforhold[1].juridiskOrgnummer shouldBeEqualTo "999999999"
                arbeidsforhold[1].orgNavn shouldBeEqualTo "Navn 2"
                arbeidsforhold[1].fom shouldBeEqualTo 14.desember(2022)
                arbeidsforhold[1].tom shouldBeEqualTo 1.mars(2023)
            }
            test("getArbeidsforhold returnerer tom liste hvis bruker ikke har arbeidsforhold") {
                coEvery { arbeidsforholdClient.getArbeidsforhold(any()) } returns emptyList()

                val arbeidsforhold =
                    arbeidsforholdService.getArbeidsforhold(
                        "12345678901",
                        1.januar(2023),
                        31.januar(2023)
                    )

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
                            Ansettelsesperiode(startdato = 1.januar(2020), sluttdato = null),
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
                                startdato = 1.juni(2022),
                                sluttdato = 7.desember(2022),
                            ),
                        ),
                    )

                val arbeidsforhold =
                    arbeidsforholdService.getArbeidsforhold(
                        "12345678901",
                        1.juni(2022),
                        20.juni(2022)
                    )

                arbeidsforhold.size shouldBeEqualTo 1
                arbeidsforhold[0].id shouldBeEqualTo 2
                arbeidsforhold[0].fnr shouldBeEqualTo "12345678901"
                arbeidsforhold[0].orgnummer shouldBeEqualTo "88888888"
                arbeidsforhold[0].juridiskOrgnummer shouldBeEqualTo "999999999"
                arbeidsforhold[0].orgNavn shouldBeEqualTo "Navn 1"
                arbeidsforhold[0].fom shouldBeEqualTo 1.juni(2022)
                arbeidsforhold[0].tom shouldBeEqualTo 7.desember(2022)
            }
            test("getArbeidsforhold filtrerer bort ugyldig arbeidsforhold") {
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
                                startdato = 1.juni(2022),
                                sluttdato = 7.desember(2022),
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
                                startdato = 1.januar(2018),
                                sluttdato = 1.juni(2023),
                            ),
                        ),
                    )

                val arbeidsforhold =
                    arbeidsforholdService.getArbeidsforhold(
                        "12345678901",
                        1.januar(2023),
                        31.januar(2023)
                    )
                arbeidsforhold.size shouldBeEqualTo 1
                arbeidsforhold[0].id shouldBeEqualTo 2
                arbeidsforhold[0].fnr shouldBeEqualTo "12345678901"
                arbeidsforhold[0].orgnummer shouldBeEqualTo "88888888"
                arbeidsforhold[0].juridiskOrgnummer shouldBeEqualTo "999999999"
                arbeidsforhold[0].orgNavn shouldBeEqualTo "Navn 1"
                arbeidsforhold[0].fom shouldBeEqualTo 1.januar(2018)
                arbeidsforhold[0].tom shouldBeEqualTo 1.juni(2023)
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
        // i denne konteksten er startdato og sluttdato datoer for arbeidsforhold og fom og tom
        // datoer for sykmeldingen
        context("Knytte arbeidsforhold til sykmeldingsperiode") {
            test("sluttdato er null, startdato er før tom") {
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
                                startdato = 1.juni(2022),
                                sluttdato = null,
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
                            Ansettelsesperiode(startdato = 1.januar(2024), sluttdato = null),
                        ),
                    )
                val fom = 1.januar(2023)
                val tom = 31.januar(2023)

                val arbeidsforhold =
                    arbeidsforholdService.getArbeidsforhold("12345678901", fom, tom)
                arbeidsforhold.size shouldBeEqualTo 1
                arbeidsforhold[0].id shouldBeEqualTo 1
                arbeidsforhold[0].fnr shouldBeEqualTo "12345678901"
                arbeidsforhold[0].orgnummer shouldBeEqualTo "123456789"
                arbeidsforhold[0].juridiskOrgnummer shouldBeEqualTo "987654321"
                arbeidsforhold[0].orgNavn shouldBeEqualTo "Navn 1"
                arbeidsforhold[0].fom shouldBeEqualTo 1.juni(2022)
                arbeidsforhold[0].tom shouldBeEqualTo null
            }
            test("sluttdato er null, startdato er lik tom") {
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
                                startdato = 1.januar(2023),
                                sluttdato = null,
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
                            Ansettelsesperiode(startdato = 1.januar(2024), sluttdato = null),
                        ),
                    )
                val fom = 1.januar(2023)
                val tom = 31.januar(2023)

                val arbeidsforhold =
                    arbeidsforholdService.getArbeidsforhold("12345678901", fom, tom)
                arbeidsforhold.size shouldBeEqualTo 1
                arbeidsforhold[0].id shouldBeEqualTo 1
                arbeidsforhold[0].fnr shouldBeEqualTo "12345678901"
                arbeidsforhold[0].orgnummer shouldBeEqualTo "123456789"
                arbeidsforhold[0].juridiskOrgnummer shouldBeEqualTo "987654321"
                arbeidsforhold[0].orgNavn shouldBeEqualTo "Navn 1"
                arbeidsforhold[0].fom shouldBeEqualTo 1.januar(2023)
                arbeidsforhold[0].tom shouldBeEqualTo null
            }
            test(
                "sluttdato er null, startdato er etter tom: ingen aktive arbeidsforhold på sykmeldingstidspunktet"
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
                                startdato = 1.februar(2023),
                                sluttdato = null,
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
                            Ansettelsesperiode(startdato = 1.januar(2024), sluttdato = null),
                        ),
                    )
                val fom = 1.januar(2023)
                val tom = 31.januar(2023)

                val arbeidsforhold =
                    arbeidsforholdService.getArbeidsforhold("12345678901", fom, tom)
                arbeidsforhold.size shouldBeEqualTo 0
            }
            test("sluttdato er etter fom, startdato er før tom") {
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
                                startdato = 1.januar(2022),
                                sluttdato = 1.januar(2024),
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
                                startdato = 1.februar(2023),
                                sluttdato = 1.juni(2023)
                            ),
                        ),
                    )
                val fom = 1.januar(2023)
                val tom = 31.januar(2023)

                val arbeidsforhold =
                    arbeidsforholdService.getArbeidsforhold("12345678901", fom, tom)
                arbeidsforhold.size shouldBeEqualTo 1
                arbeidsforhold[0].id shouldBeEqualTo 1
            }
            test("sluttdato er etter fom, startdato er lik tom") {
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
                                startdato = 1.januar(2023),
                                sluttdato = 1.januar(2024),
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
                                startdato = 1.februar(2023),
                                sluttdato = 1.juni(2023)
                            ),
                        ),
                    )
                val fom = 1.januar(2023)
                val tom = 31.januar(2023)

                val arbeidsforhold =
                    arbeidsforholdService.getArbeidsforhold("12345678901", fom, tom)
                arbeidsforhold.size shouldBeEqualTo 1
                arbeidsforhold[0].id shouldBeEqualTo 1
            }
            test("sluttdato er etter fom, startdato er etter tom") {
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
                                startdato = 1.februar(2023),
                                sluttdato = 1.januar(2024),
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
                                startdato = 1.februar(2023),
                                sluttdato = 1.juni(2023)
                            ),
                        ),
                    )
                val fom = 1.januar(2023)
                val tom = 31.januar(2023)

                val arbeidsforhold =
                    arbeidsforholdService.getArbeidsforhold("12345678901", fom, tom)
                arbeidsforhold.size shouldBeEqualTo 0
            }
            test("sluttdato er lik fom, startdato er før tom") {
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
                                startdato = 1.februar(2023),
                                sluttdato = 1.januar(2024),
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
                                startdato = 1.februar(2023),
                                sluttdato = 1.juni(2023)
                            ),
                        ),
                    )
                val fom = 1.januar(2024)
                val tom = 31.januar(2024)

                val arbeidsforhold =
                    arbeidsforholdService.getArbeidsforhold("12345678901", fom, tom)
                arbeidsforhold.size shouldBeEqualTo 1
                arbeidsforhold[0].id shouldBeEqualTo 1
            }
            test("sluttdato er før fom") {
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
                                startdato = 1.februar(2023),
                                sluttdato = 1.juni(2023),
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
                                startdato = 1.februar(2023),
                                sluttdato = 1.juli(2023)
                            ),
                        ),
                    )
                val fom = 2.juli(2023)
                val tom = 17.juli(2023)

                val arbeidsforhold =
                    arbeidsforholdService.getArbeidsforhold("12345678901", fom, tom)
                arbeidsforhold.size shouldBeEqualTo 0
            }
            test("sluttdato er før fom, startdato er etter tom") {
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
                                startdato = 1.februar(2023),
                                sluttdato = 1.juni(2023),
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
                            Ansettelsesperiode(startdato = 17.juli(2023), sluttdato = null),
                        ),
                    )
                val fom = 2.juli(2023)
                val tom = 16.juli(2023)

                val arbeidsforhold =
                    arbeidsforholdService.getArbeidsforhold("12345678901", fom, tom)
                arbeidsforhold.size shouldBeEqualTo 0
            }
        }
    })
