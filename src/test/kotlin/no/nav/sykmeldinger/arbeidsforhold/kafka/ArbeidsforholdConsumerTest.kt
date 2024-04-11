package no.nav.sykmeldinger.arbeidsforhold.kafka

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.sykmeldinger.TestDB
import no.nav.sykmeldinger.arbeidsforhold.ArbeidsforholdService
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
import no.nav.sykmeldinger.arbeidsforhold.getOrganisasjonsinfo
import no.nav.sykmeldinger.arbeidsforhold.kafka.model.ArbeidsforholdHendelse
import no.nav.sykmeldinger.arbeidsforhold.kafka.model.ArbeidsforholdKafka
import no.nav.sykmeldinger.arbeidsforhold.kafka.model.Arbeidstaker
import no.nav.sykmeldinger.arbeidsforhold.kafka.model.Endringstype
import no.nav.sykmeldinger.arbeidsforhold.kafka.model.Entitetsendring
import no.nav.sykmeldinger.arbeidsforhold.model.Arbeidsforhold
import no.nav.sykmeldinger.sykmelding.db.SykmeldingDb
import no.nav.sykmeldinger.utils.TestHelper.Companion.januar
import no.nav.sykmeldinger.utils.TestHelper.Companion.juni
import no.nav.sykmeldinger.utils.sykmelding
import no.nav.sykmeldinger.utils.sykmeldingFom
import no.nav.sykmeldinger.utils.sykmeldt
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.KafkaConsumer

class ArbeidsforholdConsumerTest :
    FunSpec(
        {
            val testDb = TestDB.database
            val arbeidsforholdDb = ArbeidsforholdDb(testDb)
            val sykmeldingDb = SykmeldingDb(testDb)
            val kafkaConsumer = mockk<KafkaConsumer<String, ArbeidsforholdHendelse>>()
            val organisasjonsinfoClient = mockk<OrganisasjonsinfoClient>()
            val arbeidsforholdClient = mockk<ArbeidsforholdClient>()
            val arbeidsforholdService =
                ArbeidsforholdService(
                    arbeidsforholdClient,
                    organisasjonsinfoClient,
                    arbeidsforholdDb,
                )
            val arbeidsforholdConsumer =
                ArbeidsforholdConsumer(
                    kafkaConsumer,
                    "topic",
                    sykmeldingDb,
                    arbeidsforholdService,
                )

            beforeEach {
                TestDB.clearAllData()
                sykmeldingDb.saveOrUpdate(
                    "1",
                    sykmelding,
                    sykmeldt,
                    ValidationResult(Status.OK, emptyList())
                )
                sykmeldingDb.saveOrUpdateSykmeldt(sykmeldt)
                clearMocks(organisasjonsinfoClient, arbeidsforholdClient)
                coEvery { organisasjonsinfoClient.getOrganisasjonsnavn(any()) } returns
                    getOrganisasjonsinfo()
            }

            context("ArbeidsforholdConsumer - handleArbeidsforholdHendelse") {
                test("Lagrer nytt arbeidsforhold") {
                    coEvery { arbeidsforholdClient.getArbeidsforhold(any()) } returns
                        listOf(
                            AaregArbeidsforhold(
                                1,
                                Arbeidssted(
                                    ArbeidsstedType.Underenhet,
                                    listOf(Ident(IdentType.ORGANISASJONSNUMMER, "123456789", true)),
                                ),
                                Opplysningspliktig(
                                    listOf(Ident(IdentType.ORGANISASJONSNUMMER, "987654321", true)),
                                ),
                                Ansettelsesperiode(
                                    startdato = 1.januar(2020),
                                    sluttdato = null,
                                ),
                            ),
                        )
                    val arbeidsforholdHendelse =
                        ArbeidsforholdHendelse(
                            id = 34L,
                            endringstype = Endringstype.Opprettelse,
                            arbeidsforhold =
                                ArbeidsforholdKafka(
                                    1,
                                    Arbeidstaker(
                                        listOf(
                                            Ident(IdentType.FOLKEREGISTERIDENT, sykmeldt.fnr, true)
                                        ),
                                    ),
                                ),
                            entitetsendringer = listOf(Entitetsendring.Ansettelsesdetaljer),
                        )

                    arbeidsforholdConsumer.handleArbeidsforholdHendelse(arbeidsforholdHendelse)

                    val arbeidsforhold = arbeidsforholdService.getArbeidsforholdFromDb(sykmeldt.fnr)
                    arbeidsforhold.size shouldBeEqualTo 1
                    arbeidsforhold[0].id shouldBeEqualTo 1
                    arbeidsforhold[0].fnr shouldBeEqualTo sykmeldt.fnr
                    arbeidsforhold[0].orgnummer shouldBeEqualTo "123456789"
                    arbeidsforhold[0].juridiskOrgnummer shouldBeEqualTo "987654321"
                    arbeidsforhold[0].orgNavn shouldBeEqualTo "Navn 1"
                    arbeidsforhold[0].fom shouldBeEqualTo 1.januar(2020)
                    arbeidsforhold[0].tom shouldBeEqualTo null

                    coVerify { arbeidsforholdClient.getArbeidsforhold(any()) }
                }
                test("Lagrer ikke utdatert arbeidsforhold") {
                    coEvery { arbeidsforholdClient.getArbeidsforhold(any()) } returns
                        listOf(
                            AaregArbeidsforhold(
                                1,
                                Arbeidssted(
                                    ArbeidsstedType.Underenhet,
                                    listOf(Ident(IdentType.ORGANISASJONSNUMMER, "123456789", true)),
                                ),
                                Opplysningspliktig(
                                    listOf(Ident(IdentType.ORGANISASJONSNUMMER, "987654321", true)),
                                ),
                                Ansettelsesperiode(
                                    startdato = 1.januar(2020),
                                    sluttdato = sykmeldingFom.minusDays(1),
                                ),
                            ),
                        )
                    val arbeidsforholdHendelse =
                        ArbeidsforholdHendelse(
                            id = 34L,
                            endringstype = Endringstype.Opprettelse,
                            arbeidsforhold =
                                ArbeidsforholdKafka(
                                    1,
                                    Arbeidstaker(
                                        listOf(
                                            Ident(IdentType.FOLKEREGISTERIDENT, sykmeldt.fnr, true)
                                        ),
                                    ),
                                ),
                            entitetsendringer = listOf(Entitetsendring.Ansettelsesdetaljer),
                        )

                    arbeidsforholdConsumer.handleArbeidsforholdHendelse(arbeidsforholdHendelse)

                    arbeidsforholdService.getArbeidsforholdFromDb(sykmeldt.fnr).size shouldBeEqualTo
                        0
                }
                test("Oppdaterer eksisterende arbeidsforhold") {
                    coEvery { arbeidsforholdClient.getArbeidsforhold(any()) } returns
                        listOf(
                            AaregArbeidsforhold(
                                1,
                                Arbeidssted(
                                    ArbeidsstedType.Underenhet,
                                    listOf(Ident(IdentType.ORGANISASJONSNUMMER, "123456789", true)),
                                ),
                                Opplysningspliktig(
                                    listOf(Ident(IdentType.ORGANISASJONSNUMMER, "987654321", true)),
                                ),
                                Ansettelsesperiode(
                                    startdato = 1.januar(2020),
                                    sluttdato = null,
                                ),
                            ),
                        )

                    arbeidsforholdService.insertOrUpdate(
                        Arbeidsforhold(
                            id = 1,
                            fnr = sykmeldt.fnr,
                            orgnummer = "123456789",
                            juridiskOrgnummer = "987654321",
                            orgNavn = "Gammel Navn AS",
                            fom = 1.januar(2020),
                            tom = sykmeldingFom.minusDays(1),
                        ),
                    )
                    val arbeidsforholdHendelse =
                        ArbeidsforholdHendelse(
                            id = 34L,
                            endringstype = Endringstype.Endring,
                            arbeidsforhold =
                                ArbeidsforholdKafka(
                                    1,
                                    Arbeidstaker(
                                        listOf(
                                            Ident(IdentType.FOLKEREGISTERIDENT, sykmeldt.fnr, true)
                                        ),
                                    ),
                                ),
                            entitetsendringer = listOf(Entitetsendring.Ansettelsesdetaljer),
                        )

                    arbeidsforholdConsumer.handleArbeidsforholdHendelse(arbeidsforholdHendelse)

                    val arbeidsforhold = arbeidsforholdService.getArbeidsforholdFromDb(sykmeldt.fnr)
                    arbeidsforhold.size shouldBeEqualTo 1
                    arbeidsforhold[0].id shouldBeEqualTo 1
                    arbeidsforhold[0].fnr shouldBeEqualTo sykmeldt.fnr
                    arbeidsforhold[0].orgnummer shouldBeEqualTo "123456789"
                    arbeidsforhold[0].juridiskOrgnummer shouldBeEqualTo "987654321"
                    arbeidsforhold[0].orgNavn shouldBeEqualTo "Navn 1"
                    arbeidsforhold[0].fom shouldBeEqualTo 1.januar(2020)
                    arbeidsforhold[0].tom shouldBeEqualTo null

                    coVerify { arbeidsforholdClient.getArbeidsforhold(any()) }
                }
                test("Sletter arbeidsforhold") {
                    arbeidsforholdService.insertOrUpdate(
                        Arbeidsforhold(
                            id = 1,
                            fnr = sykmeldt.fnr,
                            orgnummer = "123456789",
                            juridiskOrgnummer = "987654321",
                            orgNavn = "Gammel Navn AS",
                            fom = 1.januar(2020),
                            tom = sykmeldingFom.minusDays(1),
                        ),
                    )
                    val arbeidsforholdHendelse =
                        ArbeidsforholdHendelse(
                            id = 34L,
                            endringstype = Endringstype.Sletting,
                            arbeidsforhold =
                                ArbeidsforholdKafka(
                                    1,
                                    Arbeidstaker(
                                        listOf(
                                            Ident(IdentType.FOLKEREGISTERIDENT, sykmeldt.fnr, true)
                                        ),
                                    ),
                                ),
                            entitetsendringer = listOf(Entitetsendring.Ansettelsesdetaljer),
                        )

                    arbeidsforholdConsumer.handleArbeidsforholdHendelse(arbeidsforholdHendelse)

                    arbeidsforholdService.getArbeidsforholdFromDb(sykmeldt.fnr).size shouldBeEqualTo
                        0
                    coVerify(exactly = 0) { arbeidsforholdClient.getArbeidsforhold(any()) }
                }
                test(
                    "Lagrer nytt arbeidsforhold og sletter arbeidsforhold som har blitt utdatert"
                ) {
                    coEvery { arbeidsforholdClient.getArbeidsforhold(any()) } returns
                        listOf(
                            AaregArbeidsforhold(
                                1,
                                Arbeidssted(
                                    ArbeidsstedType.Underenhet,
                                    listOf(Ident(IdentType.ORGANISASJONSNUMMER, "123456789", true)),
                                ),
                                Opplysningspliktig(
                                    listOf(Ident(IdentType.ORGANISASJONSNUMMER, "987654321", true)),
                                ),
                                Ansettelsesperiode(
                                    startdato = 1.januar(2022),
                                    sluttdato = null,
                                ),
                            ),
                            AaregArbeidsforhold(
                                2,
                                Arbeidssted(
                                    ArbeidsstedType.Underenhet,
                                    listOf(Ident(IdentType.ORGANISASJONSNUMMER, "989898988", true)),
                                ),
                                Opplysningspliktig(
                                    listOf(Ident(IdentType.ORGANISASJONSNUMMER, "989898988", true)),
                                ),
                                Ansettelsesperiode(
                                    startdato = 1.januar(2020),
                                    sluttdato = 1.juni(2022),
                                ),
                            ),
                        )
                    arbeidsforholdService.insertOrUpdate(
                        Arbeidsforhold(
                            id = 2,
                            fnr = sykmeldt.fnr,
                            orgnummer = "989898988",
                            juridiskOrgnummer = "989898988",
                            orgNavn = "Gammelt Navn AS",
                            fom = 1.januar(2020),
                            tom = sykmeldingFom.minusDays(1),
                        ),
                    )

                    val arbeidsforholdHendelse =
                        ArbeidsforholdHendelse(
                            id = 34L,
                            endringstype = Endringstype.Opprettelse,
                            arbeidsforhold =
                                ArbeidsforholdKafka(
                                    1,
                                    Arbeidstaker(
                                        listOf(
                                            Ident(IdentType.FOLKEREGISTERIDENT, sykmeldt.fnr, true)
                                        ),
                                    ),
                                ),
                            entitetsendringer = listOf(Entitetsendring.Ansettelsesdetaljer),
                        )

                    arbeidsforholdConsumer.handleArbeidsforholdHendelse(arbeidsforholdHendelse)

                    val arbeidsforhold = arbeidsforholdService.getArbeidsforholdFromDb(sykmeldt.fnr)
                    arbeidsforhold.size shouldBeEqualTo 1
                    arbeidsforhold[0].id shouldBeEqualTo 1

                    coVerify { arbeidsforholdClient.getArbeidsforhold(any()) }
                }
                test("Ignorerer hendelse hvis fnr ikke finnes i databasen fra før") {
                    val arbeidsforholdHendelse =
                        ArbeidsforholdHendelse(
                            id = 34L,
                            endringstype = Endringstype.Opprettelse,
                            arbeidsforhold =
                                ArbeidsforholdKafka(
                                    15,
                                    Arbeidstaker(
                                        listOf(
                                            Ident(IdentType.FOLKEREGISTERIDENT, "12345678901", true)
                                        ),
                                    ),
                                ),
                            entitetsendringer = listOf(Entitetsendring.Ansettelsesdetaljer),
                        )

                    arbeidsforholdConsumer.handleArbeidsforholdHendelse(arbeidsforholdHendelse)

                    arbeidsforholdService
                        .getArbeidsforholdFromDb("12345678901")
                        .size shouldBeEqualTo 0
                    coVerify(exactly = 0) { arbeidsforholdClient.getArbeidsforhold(any()) }
                }

                test(
                    "Skal ikke slette arbeidsforhold der det finnes to arbeidsforhold på samme arbeidsgiver",
                ) {
                    coEvery { arbeidsforholdClient.getArbeidsforhold(any()) } returns
                        listOf(
                            AaregArbeidsforhold(
                                1,
                                Arbeidssted(
                                    ArbeidsstedType.Underenhet,
                                    listOf(Ident(IdentType.ORGANISASJONSNUMMER, "123456789", true)),
                                ),
                                Opplysningspliktig(
                                    listOf(Ident(IdentType.ORGANISASJONSNUMMER, "123456789", true)),
                                ),
                                Ansettelsesperiode(
                                    startdato = 1.januar(2022),
                                    sluttdato = null,
                                ),
                            ),
                            AaregArbeidsforhold(
                                2,
                                Arbeidssted(
                                    ArbeidsstedType.Underenhet,
                                    listOf(Ident(IdentType.ORGANISASJONSNUMMER, "123456789", true)),
                                ),
                                Opplysningspliktig(
                                    listOf(Ident(IdentType.ORGANISASJONSNUMMER, "123456789", true)),
                                ),
                                Ansettelsesperiode(
                                    startdato = 1.januar(2022),
                                    sluttdato = null,
                                ),
                            ),
                        )
                    arbeidsforholdService.insertOrUpdate(
                        Arbeidsforhold(
                            id = 1,
                            fnr = sykmeldt.fnr,
                            orgnummer = "123456789",
                            juridiskOrgnummer = "123456789",
                            orgNavn = "Gammelt Navn AS",
                            fom = 1.januar(2022),
                            tom = null,
                        ),
                    )
                    arbeidsforholdService.insertOrUpdate(
                        Arbeidsforhold(
                            id = 2,
                            fnr = sykmeldt.fnr,
                            orgnummer = "123456789",
                            juridiskOrgnummer = "123456789",
                            orgNavn = "Gammelt Navn AS",
                            fom = 1.januar(2022),
                            tom = null,
                        ),
                    )

                    val arbeidsforholdHendelse =
                        ArbeidsforholdHendelse(
                            id = 34L,
                            endringstype = Endringstype.Endring,
                            arbeidsforhold =
                                ArbeidsforholdKafka(
                                    1,
                                    Arbeidstaker(
                                        listOf(
                                            Ident(IdentType.FOLKEREGISTERIDENT, sykmeldt.fnr, true)
                                        ),
                                    ),
                                ),
                            entitetsendringer = listOf(Entitetsendring.Ansettelsesdetaljer),
                        )

                    arbeidsforholdConsumer.handleArbeidsforholdHendelse(arbeidsforholdHendelse)

                    val arbeidsforhold =
                        arbeidsforholdService.getArbeidsforholdFromDb(sykmeldt.fnr).sortedBy {
                            it.id
                        }
                    arbeidsforhold.size shouldBeEqualTo 2
                    arbeidsforhold[0].id shouldBeEqualTo 1
                    arbeidsforhold[1].id shouldBeEqualTo 2
                    coVerify(exactly = 1) { arbeidsforholdClient.getArbeidsforhold(any()) }
                }
            }

            context("ArbeidsforholdConsumer - getArbeidsforholdSomSkalSlettes") {
                test(
                    "Returnerer tom liste hvis arbeidsforhold-listene inneholder de samme elementene",
                ) {
                    val arbeidsforholdAareg =
                        listOf(
                            Arbeidsforhold(
                                id = 1,
                                fnr = sykmeldt.fnr,
                                orgnummer = "123456789",
                                juridiskOrgnummer = "987654321",
                                orgNavn = "Navn AS",
                                fom = 1.januar(2020),
                                tom = sykmeldingFom.minusDays(1),
                            ),
                            Arbeidsforhold(
                                id = 2,
                                fnr = sykmeldt.fnr,
                                orgnummer = "989898988",
                                juridiskOrgnummer = "989898988",
                                orgNavn = "Bedrift AS",
                                fom = 1.januar(2020),
                                tom = null,
                            ),
                        )
                    val arbeidsforholdFraDb =
                        listOf(
                            Arbeidsforhold(
                                id = 1,
                                fnr = sykmeldt.fnr,
                                orgnummer = "123456789",
                                juridiskOrgnummer = "987654321",
                                orgNavn = "Navn AS",
                                fom = 1.januar(2020),
                                tom = sykmeldingFom.minusDays(1),
                            ),
                            Arbeidsforhold(
                                id = 2,
                                fnr = sykmeldt.fnr,
                                orgnummer = "989898988",
                                juridiskOrgnummer = "989898988",
                                orgNavn = "Gammelt Navn AS",
                                fom = 1.januar(2020),
                                tom = null,
                            ),
                        )

                    val slettesFraDb =
                        arbeidsforholdConsumer.getArbeidsforholdSomSkalSlettes(
                            arbeidsforholdAareg = arbeidsforholdAareg,
                            arbeidsforholdDb = arbeidsforholdFraDb,
                        )

                    slettesFraDb.size shouldBeEqualTo 0
                }
                test(
                    "Returnerer tom liste hvis aareg-arbeidsforhold inneholder flere elementer enn arbeidsforholdDb-listen",
                ) {
                    val arbeidsforholdAareg =
                        listOf(
                            Arbeidsforhold(
                                id = 1,
                                fnr = sykmeldt.fnr,
                                orgnummer = "123456789",
                                juridiskOrgnummer = "987654321",
                                orgNavn = "Navn AS",
                                fom = 1.januar(2020),
                                tom = sykmeldingFom.minusDays(1),
                            ),
                            Arbeidsforhold(
                                id = 2,
                                fnr = sykmeldt.fnr,
                                orgnummer = "989898988",
                                juridiskOrgnummer = "989898988",
                                orgNavn = "Bedrift AS",
                                fom = 1.januar(2020),
                                tom = null,
                            ),
                        )
                    val arbeidsforholdFraDb =
                        listOf(
                            Arbeidsforhold(
                                id = 1,
                                fnr = sykmeldt.fnr,
                                orgnummer = "123456789",
                                juridiskOrgnummer = "987654321",
                                orgNavn = "Navn AS",
                                fom = 1.januar(2020),
                                tom = sykmeldingFom.minusDays(1),
                            ),
                        )

                    val slettesFraDb =
                        arbeidsforholdConsumer.getArbeidsforholdSomSkalSlettes(
                            arbeidsforholdAareg = arbeidsforholdAareg,
                            arbeidsforholdDb = arbeidsforholdFraDb,
                        )

                    slettesFraDb.size shouldBeEqualTo 0
                }
                test(
                    "liste med id hvis agDb har ag som ikke finnes i aareg",
                ) {
                    val arbeidsforholdAareg =
                        listOf(
                            Arbeidsforhold(
                                id = 1,
                                fnr = sykmeldt.fnr,
                                orgnummer = "123456789",
                                juridiskOrgnummer = "987654321",
                                orgNavn = "Navn AS",
                                fom = 1.januar(2020),
                                tom = sykmeldingFom.minusDays(1),
                            ),
                        )
                    val arbeidsforholdFraDb =
                        listOf(
                            Arbeidsforhold(
                                id = 1,
                                fnr = sykmeldt.fnr,
                                orgnummer = "123456789",
                                juridiskOrgnummer = "987654321",
                                orgNavn = "Navn AS",
                                fom = 1.januar(2020),
                                tom = sykmeldingFom.minusDays(1),
                            ),
                            Arbeidsforhold(
                                id = 2,
                                fnr = sykmeldt.fnr,
                                orgnummer = "989898988",
                                juridiskOrgnummer = "989898988",
                                orgNavn = "Gammelt Navn AS",
                                fom = 1.januar(2020),
                                tom = null,
                            ),
                        )

                    val slettesFraDb =
                        arbeidsforholdConsumer.getArbeidsforholdSomSkalSlettes(
                            arbeidsforholdAareg = arbeidsforholdAareg,
                            arbeidsforholdDb = arbeidsforholdFraDb,
                        )

                    slettesFraDb.size shouldBeEqualTo 1
                    slettesFraDb[0] shouldBeEqualTo 2
                }
            }
        },
    )
